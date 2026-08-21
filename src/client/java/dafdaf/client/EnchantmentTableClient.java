package dafdaf.client;

import dafdaf.client.enchanting.EnchantingPreviewRenderer;
import dafdaf.client.config.PreviewConfig;
import dafdaf.client.enchanting.EnchantingTableStorage;
import dafdaf.client.enchanting.EnchantingTableOpenS2CPacket;
import dafdaf.client.enchanting.EnchantingTableStoredS2CPacket;
import dafdaf.client.enchanting.KeepPreferenceC2SPacket;
import dafdaf.client.enchanting.OpenEnchantingTableTracker;
import dafdaf.client.enchanting.PlayerKeepPrefs;

import com.google.gson.JsonElement;

import com.mojang.serialization.JsonOps;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class EnchantmentTableClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// 注册附魔台 3D 预览渲染回调（纯客户端，仅在世界渲染阶段生效）。
		WorldRenderEvents.AFTER_ENTITIES.register(EnchantingPreviewRenderer::render);

		// 注册自定义 payload 类型（CLIENTBOUND）：必须在任何 registerGlobalReceiver/发送前完成。
		// 局域网主机=集成服务器=本地客户端 JVM，服务端与客户端两侧都经此注册，payload 类型一致。
		PayloadTypeRegistry.playS2C().register(EnchantingTableOpenS2CPacket.ID, EnchantingTableOpenS2CPacket.CODEC);
		PayloadTypeRegistry.playS2C().register(EnchantingTableStoredS2CPacket.ID, EnchantingTableStoredS2CPacket.CODEC);
		PayloadTypeRegistry.playC2S().register(KeepPreferenceC2SPacket.ID, KeepPreferenceC2SPacket.CODEC);

		// S2C 接收器（联机/单机同一通道）：① 开桌坐标 → tracker（客户端渲染定位实时预览）；
		// ② 保留记录 → storage 内存缓存（渲染线程即刻可见）。单机下服务端已直写缓存，幂等。
		ClientPlayNetworking.registerGlobalReceiver(EnchantingTableOpenS2CPacket.ID,
				(payload, context) -> context.client().execute(() -> {
					OpenEnchantingTableTracker.set(payload.pos(), payload.dimensionKey());
					// 打开附魔台时重上报本机 keep 偏好（保持最新，覆盖运行中改配置）。
					ClientPlayNetworking.send(new KeepPreferenceC2SPacket(
							PreviewConfig.get().keepItemsWhenClosed));
				}));
		ClientPlayNetworking.registerGlobalReceiver(EnchantingTableStoredS2CPacket.ID,
				(payload, context) -> context.client().execute(() -> {
					MinecraftClient client = context.client();
					if (client.world == null) {
						return;
					}
					RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE,
							client.world.getRegistryManager());
					ItemStack item = EnchantingTableStoredS2CPacket.fromJson(ops, payload.itemJson());
					ItemStack lapis = EnchantingTableStoredS2CPacket.fromJson(ops, payload.lapisJson());
					EnchantingTableStorage.storeCached(payload.dimensionKey(), payload.pos(), item, lapis);
				}));

		// C2S：客户端上报自己的 keep 偏好 → 服务端按玩家记录（修复「房主关 keep 影响其他玩家」）。
		ServerPlayNetworking.registerGlobalReceiver(KeepPreferenceC2SPacket.ID,
				(payload, context) -> context.server().execute(() ->
						PlayerKeepPrefs.set(context.player().getUuid(), payload.keepItemsWhenClosed())));

		// 客户端加入服务器时：上报本机 keep 偏好（服务端按玩家记录，修复房主 keep 污染）。
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
				ClientPlayNetworking.send(new KeepPreferenceC2SPacket(PreviewConfig.get().keepItemsWhenClosed)));

		// 服务端：玩家加入时把存储里的全部非空记录逐条补发给该玩家（新进客户端初始同步）。
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity serverPlayer = handler.getPlayer();
			RegistryOps<JsonElement> ops = RegistryOps.of(JsonOps.INSTANCE, server.getRegistryManager());
			for (EnchantingTableStorage.DimensionEntry sp : EnchantingTableStorage.listAllEntries()) {
				ServerPlayNetworking.send(serverPlayer, EnchantingTableStoredS2CPacket.fromData(
						ops, sp.pos(), sp.dimensionKey(), sp.data().item(), sp.data().lapis()));
			}
		});

		// 玩家离开时清除其 keep 偏好，防 UUID → 记录长期堆积。
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				PlayerKeepPrefs.remove(handler.getPlayer().getUuid()));

		// 玩家亲手挖掉附魔台时，像原版箱子一样把存储保留的物品（待附魔物品 + 青金石）
		// 掉落出来，并清除该位置的存储记录（PlayerBlockBreakEvents.AFTER 只在玩家破坏
		// 那一格时触发，比挂 AbstractBlock.onStateReplaced 更省开销；代价是爆炸/活塞
		// 等非玩家破坏不掉物，附魔台的记录会残留）。
		PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
			if (state.getBlock() != Blocks.ENCHANTING_TABLE) {
				return;
			}
			if (!(world instanceof ServerWorld serverWorld)) {
				return;
			}
			EnchantingTableStorage.TableData data = EnchantingTableStorage.take(serverWorld, pos.toImmutable());
			if (data != null) {
				ItemStack item = data.item();
				if (item != null && !item.isEmpty()) {
					Block.dropStack(serverWorld, pos.toImmutable(), item);
				}
				ItemStack lapis = data.lapis();
				if (lapis != null && !lapis.isEmpty()) {
					Block.dropStack(serverWorld, pos.toImmutable(), lapis);
				}
			}
		});
	}
}