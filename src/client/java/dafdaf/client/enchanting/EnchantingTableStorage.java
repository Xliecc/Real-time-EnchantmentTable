package dafdaf.client.enchanting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.Map;

/**
 * 「附魔台作为容器」的持久化存储。
 *
 * <p>1.21.11 原版附魔台的方块实体只有书的翻页动画与自定义名称、<b>不存储物品</b>（菜单里的
 * {@code SimpleInventory(2)} 在关闭界面时会把物品退还/掉落），无法把物品存进方块实体；因此按
 * 「维度 + 坐标」把附魔台的两个槽位物品（槽 0 = 待附魔物品、槽 1 = 青金石）归档到一个 JSON 文件
 * （{@code config/enchantment-table-storage.json}），下次打开同一位置的附魔台时恢复。
 *
 * <p>物品用 {@link ItemStack#CODEC} + {@link RegistryOps} 序列化，可完整保留 1.21
 * 数据组件（附魔、改名、damage 等）。
 *
 * <p>线程约定：{@link #store}/{@link #peek} 由集成服务器的 server 线程调用（mixin
 * 拦截 {@code onClosed}/{@code <init>}），{@link #peekAll} 由渲染线程每帧读取。
 * 三者在同一把 {@link #LOCK} 上同步，保证缓存可见性。
 */
public final class EnchantingTableStorage {

	/**
	 * 规范化栈上的附魔组件：把 storage JSON 反序列化产生的「游离」RegistryEntry 引用
	 * 重解为给定注册表（服务端）里的规范条目，避免原版 container_set_content 二进制编码时
	 * 抛 {@code Can't find id for Reference{...}} 断线。无法重解引用的条目（无 key/key
	 * 缺失）直接丢弃——此类条目本来也无法过网络编码。不修改原对象，返回副本或原对象。
	 */
	public static ItemStack canonicalizeEnchantments(
			net.minecraft.registry.Registry<net.minecraft.enchantment.Enchantment> reg, ItemStack stack) {
		if (stack == null || stack.isEmpty() || !stack.hasEnchantments()) {
			return stack;
		}
		net.minecraft.component.type.ItemEnchantmentsComponent enc = stack.getEnchantments();
		java.util.Set<it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<
				net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment>>> entries =
				enc.getEnchantmentEntries();
		if (entries.isEmpty()) {
			return stack;
		}
		net.minecraft.component.type.ItemEnchantmentsComponent.Builder b =
				new net.minecraft.component.type.ItemEnchantmentsComponent.Builder(
						net.minecraft.component.type.ItemEnchantmentsComponent.DEFAULT);
		for (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<
				net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment>> e : entries) {
			net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment> entry = e.getKey();
			net.minecraft.registry.RegistryKey<net.minecraft.enchantment.Enchantment> key =
					entry.getKey().orElse(null);
			if (key == null) {
				continue; // 无 key：无法重解引用，丢弃该附魔
			}
			net.minecraft.registry.entry.RegistryEntry<net.minecraft.enchantment.Enchantment> canonical =
					reg.getEntry(key.getValue()).orElse(null);
			if (canonical == null) {
				continue; // key 不在服务端注册表：无法过网络编码，丢弃
			}
			b.add(canonical, e.getIntValue());
		}
		// 总是用服务端注册表的规范条目重建组件并返回副本（哪怕一个都没丢弃）。
		// 不设 !changed 早退：storage/客户端反序列化产生的「游离」RegistryEntry 与注册表值对象
		// 不同实例时，原版 PACKET_CODEC 按 raw id 查找会失败（Can't find id 断线），必须统一换回
		// canonical 实例。若条目丢弃导致组件为空，也返回去附魔副本（宁丢附魔不炸）。
		ItemStack copy = stack.copy();
		copy.set(net.minecraft.component.DataComponentTypes.ENCHANTMENTS, b.build());
		return copy;
	}

	/** 一个位置归档的完整数据：待附魔物品 + 青金石（均可为空）。 */
	public record TableData(ItemStack item, ItemStack lapis) {

		/** 是否有任何可见内容（两个槽位都空）。 */
		public boolean isEmpty() {
			return (item == null || item.isEmpty()) && (lapis == null || lapis.isEmpty());
		}
	}

	/**
	 * 存储中一个位置的非空保留预览：位置 + 数据。供渲染线程整表扫描当前维度，
	 * 实现「关闭界面后保留预览始终显示」（不依赖玩家视线）。
	 */
	public record StoredPreview(BlockPos pos, TableData data) {
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** store（server 线程）与 peekAll（渲染线程）共用，保证 HashMap 可见性。 */
	private static final Object LOCK = new Object();

	private static Map<String, TableData> cache;
	private static boolean loaded;

	private EnchantingTableStorage() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("enchantment-table-storage.json");
	}

	private static String key(String dimensionId, BlockPos pos) {
		return dimensionId + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	private static String key(ServerWorld world, BlockPos pos) {
		return key(world.getRegistryKey().getValue().toString(), pos);
	}

	private static RegistryOps<JsonElement> ops(ServerWorld world) {
		RegistryWrapper.WrapperLookup registryManager = world.getServer().getRegistryManager();
		return RegistryOps.of(JsonOps.INSTANCE, registryManager);
	}

	/** 客户端读取前置：若缓存尚未加载，直接用客户端自身的 registry manager 从存储文件
	 * 加载（不依赖服务端）。使「重启后不打开 GUI 也立即恢复预览」。 */
	private static void ensureClientLoaded(ClientWorld world) {
		if (loaded) {
			return;
		}
		try {
			loaded = true;
			cache = new HashMap<>();
			Path path = file();
			if (!Files.exists(path)) {
				return;
			}
			RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, world.getRegistryManager());
			JsonObject root = GSON.fromJson(Files.readString(path), JsonObject.class);
			if (root != null && root.has("entries")) {
				for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("entries").entrySet()) {
					cache.put(e.getKey(), decodeTableData(ops, e.getValue()));
				}
			}
		} catch (Exception ignored) {
			// 文件损坏/无法解析：视为空存储
		}
	}

	/** 把两个槽位的物品防御性拷贝后归档。 */
	private static TableData normalize(ItemStack item, ItemStack lapis) {
		return new TableData(
				item == null ? ItemStack.EMPTY : item.copy(),
				lapis == null ? ItemStack.EMPTY : lapis.copy());
	}

	/**
	 * 把附魔台两槽物品存入该位置；随后调用方负责清空槽位。
	 */
	public static void store(ServerWorld world, BlockPos pos, ItemStack item, ItemStack lapis) {
		synchronized (LOCK) {
			ensureLoaded(world);
			String k = key(world, pos);
			cache.put(key(world, pos), normalize(item, lapis));
			save(world);
		}
	}

	/**
	 * 客户端安全读取指定位置的归档数据（<b>不移除</b>），供 BER 渲染阶段按位置取内容；
	 * 无记录返回 {@code null}。仅扫内存缓存（不加载存储文件），键按当前维度前缀过滤。
	 */
	public static TableData peekClient(ClientWorld world, BlockPos pos) {
		synchronized (LOCK) {
			ensureClientLoaded(world);
			if (cache == null) {
				return null;
			}
			return cache.get(key(world.getRegistryKey().getValue().toString(), pos));
		}
	}

	/**
	 * 读取该位置归档的数据（<b>不移除</b>），供打开界面时恢复进附魔台槽位；无记录返回
	 * {@code null}。记录在 GUI 打开期间仍留在存储缓存中，渲染线程的 {@link #peekAll}
	 * 持续可见——避免「打开界面时记录被瞬时移除、实时内容尚未接管」的渲染空档
	 * （预览闪没一瞬）。关闭时 {@link #store} 会覆盖该记录。
	 */
	public static TableData peek(ServerWorld world, BlockPos pos) {
		synchronized (LOCK) {
			ensureLoaded(world);
			return cache.get(key(world, pos));
		}
	}

	/**
	 * 客户端安全地写入内存缓存（<b>不落盘</b>、不需要服务端 registry ops）：供关闭界面时
	 * （客户端镜像 handler 的 {@code onClosed}，其 {@code context} 为 EMPTY、跑不了
	 * {@code context.run}）立即把物品写进缓存，渲染线程 {@link #peekAll} 即刻可见——
	 * 填补「实时源停止、服务端 store 还要等关闭数据包往返」之间的渲染空档（预览闪没一瞬）。
	 * 服务端随后会经 {@link #store} 完整覆盖并落盘。
	 * 若缓存尚未加载（{@code cache == null}）则忽略：此时渲染线程本就无记录可读，服务端的
	 * {@code store} 会负责加载与落盘。
	 *
	 * @param dimensionKey 维度 id 字符串（如 {@code minecraft:overworld}），来自
	 *                     {@link OpenEnchantingTableTracker#getDimensionKey()}（客户端拿不到 world 对象）
	 */
	
	/**
	 * 全维度非空记录（玩家加入时补发用，不依赖具体 ServerWorld，仅需解析键）。
	 * 返回 (dimensionKey, pos, data)。
	 */
	public static List<DimensionEntry> listAllEntries() {
		synchronized (LOCK) {
			List<DimensionEntry> out = new ArrayList<>();
			if (cache == null) {
				return out.isEmpty() ? List.of() : out;
			}
			for (Map.Entry<String, TableData> e : cache.entrySet()) {
				if (e.getValue() != null && !e.getValue().isEmpty()) {
					int i = e.getKey().lastIndexOf(':');
					if (i < 0) {
						continue;
					}
					String dim = e.getKey().substring(0, i);
					BlockPos pos = posFromKey(e.getKey());
					if (pos != null) {
						out.add(new DimensionEntry(dim, pos, e.getValue()));
					}
				}
			}
			return out.isEmpty() ? List.of() : out;
		}
	}

	/** 全维度记录：维度键 + 位置 + 数据。 */
	public record DimensionEntry(String dimensionKey, BlockPos pos, TableData data) {
	}

	/**
	 * 服务端遍历当前世界全部非空记录（玩家加入时全量补发用）。
	 */
	public static List<StoredPreview> listAll(ServerWorld world) {
		synchronized (LOCK) {
			ensureLoaded(world);
			if (cache == null) {
				return List.of();
			}
			String dimPrefix = world.getRegistryKey().getValue().toString() + ":";
			List<StoredPreview> out = new ArrayList<>();
			for (Map.Entry<String, TableData> e : cache.entrySet()) {
				if (e.getKey().startsWith(dimPrefix) && e.getValue() != null && !e.getValue().isEmpty()) {
					BlockPos pos = posFromKey(e.getKey());
					if (pos != null) {
						out.add(new StoredPreview(pos, e.getValue()));
					}
				}
			}
			return out.isEmpty() ? List.of() : out;
		}
	}

public static void storeCached(String dimensionKey, BlockPos pos, ItemStack item, ItemStack lapis) {
		synchronized (LOCK) {
			if (cache == null) {
				return;
			}
			cache.put(key(dimensionKey, pos), normalize(item, lapis));
		}
	}


	/**
	 * 读取并移除该位置归档的数据（附魔台被破坏时用于把储存物品掉落出来、像原版箱子一样），
	 * 并立即落盘。无记录返回 {@code null}。
	 */
	public static TableData take(ServerWorld world, BlockPos pos) {
		synchronized (LOCK) {
			ensureLoaded(world);
			String k = key(world, pos);
			TableData data = cache.get(k);
			if (data != null) {
				cache.remove(k);
				save(world);
			}
			return data;
		}
	}

	/**
	 * 客户端安全读取当前维度所有非空的保留记录（供渲染线程遍历，实现保留预览不依赖视线）。
	 * 仅扫内存缓存（不加载存储文件），键按「维度:坐标」前缀过滤当前维度；损坏键直接跳过。
	 */
	public static List<StoredPreview> peekAll(ClientWorld world) {
		synchronized (LOCK) {
			ensureClientLoaded(world);
			if (cache == null) {
				return List.of();
			}
			String dimPrefix = world.getRegistryKey().getValue().toString() + ":";
			List<StoredPreview> out = new ArrayList<>();
			for (Map.Entry<String, TableData> e : cache.entrySet()) {
				if (e.getKey().startsWith(dimPrefix) && e.getValue() != null && !e.getValue().isEmpty()) {
					BlockPos pos = posFromKey(e.getKey());
					if (pos != null) {
						out.add(new StoredPreview(pos, e.getValue()));
					}
				}
			}
			return out.isEmpty() ? List.of() : out;
		}
	}

	/** 从存储键（{@code dimension:x,y,z}）反解出坐标；格式不符返回 {@code null}。 */
	private static BlockPos posFromKey(String key) {
		int i = key.lastIndexOf(':');
		if (i < 0) {
			return null;
		}
		String[] parts = key.substring(i + 1).split(",");
		if (parts.length != 3) {
			return null;
		}
		try {
			return new BlockPos(
					Integer.parseInt(parts[0]),
					Integer.parseInt(parts[1]),
					Integer.parseInt(parts[2]));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static void ensureLoaded(ServerWorld world) {
		if (loaded) {
			return;
		}
		cache = new HashMap<>();
		Path path = file();
		if (Files.exists(path)) {
			try {
				RegistryOps<JsonElement> ops = ops(world);
				JsonObject root = GSON.fromJson(Files.readString(path), JsonObject.class);
				if (root != null && root.has("entries")) {
					for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("entries").entrySet()) {
						cache.put(e.getKey(), decodeTableData(ops, e.getValue()));
					}
				}
			} catch (Exception ignored) {
				// 文件损坏/无法解析：视为空存储
			}
		}
		loaded = true;
	}

	private static final Object FLUSH_LOCK = new Object();
	private static boolean flushPending;

	/**
	 * 落盘改为<b>合并式异步写</b>：store/take 在 LOCK 内已同步更新内存缓存，这里只把
	 * 序列化 + 文件 IO 移出主线程（整合服 server 线程 = 渲染线程所在进程），并合并连续
	 * 多次改动为一次写入，避免每帧/每次编辑都全量写 JSON 造成卡顿。
	 */
	private static void save(ServerWorld world) {
		synchronized (FLUSH_LOCK) {
			if (flushPending) {
				return; // 已有一次待写，本次改动并入下一次
			}
			flushPending = true;
		}
		final ServerWorld w = world;
		final String serialized;
		synchronized (LOCK) {
			serialized = serialize(w);
		}
		// 单线程异步写（IO 不阻塞游戏线程；合并：写期间的新改动会再触发一轮）
		CompletableFuture.runAsync(() -> {
			try {
				Path path = file();
				Files.createDirectories(path.getParent());
				Files.writeString(path, serialized);
			} catch (IOException ignored) {
				// 写入失败仅影响该特性，不影响游戏运行
			} finally {
				synchronized (FLUSH_LOCK) {
					flushPending = false;
				}
			}
		});
	}

	/** 在 LOCK 内对当前缓存全量序列化（JSON 字符串）。 */
	private static String serialize(ServerWorld w) {
		try {
			RegistryOps<JsonElement> ops = ops(w);
			JsonObject root = new JsonObject();
			JsonObject entries = new JsonObject();
			for (Map.Entry<String, TableData> e : cache.entrySet()) {
				JsonObject obj = new JsonObject();
				obj.add("item", encodeStack(ops, e.getValue().item()));
				obj.add("lapis", encodeStack(ops, e.getValue().lapis()));
				entries.add(e.getKey(), obj);
			}
			root.add("entries", entries);
			return GSON.toJson(root);
		} catch (Exception ex) {
			return null; // 序列化失败：放弃本轮写入
		}
	}


	private static JsonElement encodeStack(RegistryOps<JsonElement> ops, ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return JsonNull.INSTANCE;
		}
		DataResult<JsonElement> result = ItemStack.CODEC.encodeStart(ops, stack);
		return result.result().orElse(JsonNull.INSTANCE);
	}

	private static ItemStack decodeStack(RegistryOps<JsonElement> ops, JsonElement element) {
		if (element == null || element.isJsonNull()) {
			return ItemStack.EMPTY;
		}
		DataResult<ItemStack> result = ItemStack.CODEC.parse(ops, element);
		return result.result().orElse(ItemStack.EMPTY);
	}

	private static TableData decodeTableData(RegistryOps<JsonElement> ops, JsonElement element) {
		if (element != null && element.isJsonObject()) {
			JsonObject obj = element.getAsJsonObject();
			return new TableData(
					decodeStack(ops, obj.has("item") ? obj.get("item") : null),
					decodeStack(ops, obj.has("lapis") ? obj.get("lapis") : null));
		}
		// 格式不符：视为空记录
		return new TableData(ItemStack.EMPTY, ItemStack.EMPTY);
	}
}