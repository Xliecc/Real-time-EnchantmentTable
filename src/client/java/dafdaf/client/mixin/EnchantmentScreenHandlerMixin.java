package dafdaf.client.mixin;

import dafdaf.client.config.PreviewConfig;
import dafdaf.client.enchanting.EnchantingTableNetworking;
import dafdaf.client.enchanting.EnchantingTableOpeners;
import dafdaf.client.enchanting.EnchantingTableStorage;
import dafdaf.client.enchanting.EnchantingPreviewRenderer;
import dafdaf.client.enchanting.PlayerKeepPrefs;
import dafdaf.client.enchanting.OpenEnchantingTableTracker;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.screen.EnchantmentScreenHandler;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 为 {@link EnchantmentScreenHandler} 注入「附魔台作为容器 + 共享」逻辑。
 *
 * <p><b>共享附魔台语义</b>（参照 template-mod 的共享合成网格）：同一位置的附魔台，
 * 所有打开者共享同一份<b>权威内容</b>（{@link EnchantingTableStorage}）：
 * <ul>
 *   <li>打开（构造）时：从权威内容 <b>peek（不取走）</b> 恢复进自己的槽位并登记打开者——任何
 *       玩家打开都能看到其他人留在台上的东西（共享显示）；</li>
 *   <li>编辑（{@code onContentChanged}）时：以当前槽位为权威 <b>store</b> 并<b>写回所有其他
 *       打开者</b>的槽位——B 拿走时 A 的槽位同步变空（不复制）、A 放入时 B 实时看到（共享）；</li>
 *   <li>关闭（{@code onClosed}）时：还有其他打开者 → 只清自己的镜像槽、不动权威（内容继续留在
 *       台上）；最后一名打开者 + keep 开启 → 权威存存储供下次打开；最后一名 + keep 关闭 →
 *       不拦截原版归还并清空权威记录。</li>
 * </ul>
 *
 * <p>仅当 {@code keepItemsWhenClosed} 开启时保留材料；开关关闭时打开/关闭都走原版
 * （不恢复、不拦截还原，仅登记/注销打开者以维持共享语义正确）。
 *
 * <p>此 mixin 属于 client 源集，依赖单机（集成服务器与客户端同 JVM）运行：PCL2 单机下
 * 服务端逻辑同样经过此变换。客户端/服务端分离的联机环境不生效。
 */
@Mixin(EnchantmentScreenHandler.class)
public abstract class EnchantmentScreenHandlerMixin {

	/** 调试日志（观察共享/恢复链路）。 */
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("enchantment-table");

	@Shadow
	@Final
	private ScreenHandlerContext context;

	@Shadow
	@Final
	private Inventory inventory;

	@Shadow
	@Final
	private int[] enchantmentPower;

	@Shadow
	public abstract void onContentChanged(Inventory inventory);


	/** 共享写回重入保护（服务端单线程）：写回他人槽位会触发对方 onContentChanged →
	 * 本注入再进来时跳过（避免把写回误判为对方实际操作）。 */
	private static boolean sharingSync = false;

	/**
	 * 打开附魔台（创建菜单）时：写入当前附魔台坐标到 {@link OpenEnchantingTableTracker}
	 * （供渲染器定位 GUI 预览），登记打开者，并恢复该位置保留的物品（peek 不取走 →
	 * 任何打开者都能看到共享内容）。
	 */
	@Inject(method = "<init>(ILnet/minecraft/entity/player/PlayerInventory;Lnet/minecraft/screen/ScreenHandlerContext;)V",
			at = @At("RETURN"))
	private void enchantmentTable$restoreOnOpen(int syncId, PlayerInventory playerInventory,
			ScreenHandlerContext context, CallbackInfo ci) {
		this.context.run((world, pos) -> {
			if (world instanceof ServerWorld serverWorld) {
				BlockPos ip = pos.toImmutable();
				// 记录当前打开的附魔台坐标（服务端真实 context；客户端镜像为 EMPTY，run 是空操作）。
				OpenEnchantingTableTracker.set(ip, world.getRegistryKey().getValue().toString());
				// 登记打开者（共享语义：编辑时写回所有打开者）。
				if (playerInventory.player instanceof ServerPlayerEntity serverPlayer) {
					EnchantingTableOpeners.add(ip, serverPlayer.getUuid());
					// 联机：把坐标发给打开的玩家本人（客户端 tracker 定位实时预览）。
					EnchantingTableNetworking.sendOpen(serverPlayer, ip,
							world.getRegistryKey().getValue().toString());
				}
				// 无论 keep 当前值都恢复已存物品到槽位（显示在台上）：切换 keep 不退回、不删除
				// 已放物品；是否保留/退还由「关闭时」（onClosed）按当时的 keep 判定——keep 开则
				// 保留继续存在台上，keep 关则借原版归还机制把物品正常带出（退回背包）。
				// 从权威内容恢复进槽位（peek 不取走）。恢复写槽会触发 onContentChanged →
				// 共享写回注入：用 sharingSync 抑制，避免恢复被误判为操作。
Registry<net.minecraft.enchantment.Enchantment> reg = serverWorld.getServer()
				.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
				sharingSync = true;
				try {
					EnchantingTableStorage.TableData stored = EnchantingTableStorage.peek(serverWorld, ip);
					if (stored != null && !stored.isEmpty()) {
						this.inventory.setStack(0, EnchantingTableStorage.canonicalizeEnchantments(reg, stored.item().copy()));
						this.inventory.setStack(1, EnchantingTableStorage.canonicalizeEnchantments(reg, stored.lapis().copy()));
						this.onContentChanged(this.inventory);
						// 恢复后立即同步客户端渲染缓存：即使 keep 已关也会在下一步用缓存兜底显示
						// （消除「重开瞬间槽位未同步、渲染误判为空而播放退场动画」的竞态）。
						EnchantingTableStorage.storeCached(serverWorld.getRegistryKey().getValue().toString(),
								ip, stored.item().copy(), stored.lapis().copy());
					}
				} finally {
					sharingSync = false;
				}
			}
		});
	}

	/**
	 * 编辑共享：槽位内容变化（放入/拿走/换物品）时，把当前槽位作为<b>权威内容</b>更新存储，
	 * 并<b>写回所有其他打开者</b>的槽位——两人 GUI 实时同步，拿走即从所有视图消失（不复制）。
	 */
	@Inject(method = "onContentChanged", at = @At("TAIL"))
	private void enchantmentTable$syncLiveTable(Inventory inventory, CallbackInfo ci) {
		if (sharingSync) {
			return;
		}
		this.context.run((world, pos) -> {
			if (!(world instanceof ServerWorld serverWorld)) {
				return;
			}
			BlockPos ip = pos.toImmutable();
			ItemStack item = ((EnchantmentScreenHandler)(Object)this).getSlot(0).getStack();
			ItemStack lapis = ((EnchantmentScreenHandler)(Object)this).getSlot(1).getStack();
			LOGGER.debug("onContentChanged item={} lapis={}", item, lapis);
			// ① 权威内容持久化：编辑时始终 store（共享/权威语义，与 keep 开关无关；keep 只决定
			// 最后一人关闭时保留或归还）。实时显示走缓存与广播。
			EnchantingTableStorage.store(serverWorld, ip, item, lapis);
			// ①b 同步客户端即时缓存（同 JVM 渲染立即变空；LAN 下对方客户端无本地记录则无副作用）。
			EnchantingTableStorage.storeCached(serverWorld.getRegistryKey().getValue().toString(), ip, item, lapis);
			// ①c 广播给追踪该区块的所有客户端（联机下 B 的 storage 也同步显示/清空）。
			EnchantingTableNetworking.broadcastStored(serverWorld, ip,
					serverWorld.getRegistryKey().getValue().toString(), item, lapis);
			// ② 共享写回：登记的同位置打开者（玩家一次只能开一个 GUI，其 currentScreenHandler
			// 必是该位置的 handler），除自己外槽位同步为权威内容。
Registry<net.minecraft.enchantment.Enchantment> reg = serverWorld.getServer()
				.getRegistryManager().getOrThrow(net.minecraft.registry.RegistryKeys.ENCHANTMENT);
			// ② 共享写回 + 自身规范化：全部在 sharingSync 保护内——回写/规范化 setStack 触发
			// 的 onContentChanged 会被共享守卫拦截，避免无限递归（StackOverflowError）。
			// ③ 自身槽位规范化：把所有带附魔的槽位物品按服务端注册表重解引用，避免原版
			// container_set_content 编码附魔时抛 Can't find id 断线（含背包内附魔物品的整容器同步）。
			sharingSync = true;
			try {
				ItemStack itemC = ((EnchantmentScreenHandler)(Object)this).getSlot(0).getStack();
				ItemStack lapisC = ((EnchantmentScreenHandler)(Object)this).getSlot(1).getStack();
				if (itemC.hasEnchantments() || lapisC.hasEnchantments()) {
					((EnchantmentScreenHandler)(Object)this).getSlot(0).setStack(
							itemC.isEmpty() ? ItemStack.EMPTY : EnchantingTableStorage.canonicalizeEnchantments(reg, itemC));
					((EnchantmentScreenHandler)(Object)this).getSlot(1).setStack(
							lapisC.isEmpty() ? ItemStack.EMPTY : EnchantingTableStorage.canonicalizeEnchantments(reg, lapisC));
				}
				for (java.util.UUID openerUuid : EnchantingTableOpeners.getOpeners(ip)) {
					ServerPlayerEntity opener = serverWorld.getServer().getPlayerManager().getPlayer(openerUuid);
					if (opener == null
							|| !(opener.currentScreenHandler instanceof EnchantmentScreenHandler other)
							|| other == (EnchantmentScreenHandler) (Object) this) {
						continue;
					}
					other.getSlot(0).setStack(item.isEmpty() ? ItemStack.EMPTY
							: EnchantingTableStorage.canonicalizeEnchantments(reg, item.copy()));
					other.getSlot(1).setStack(lapis.isEmpty() ? ItemStack.EMPTY
							: EnchantingTableStorage.canonicalizeEnchantments(reg, lapis.copy()));
				}
			} finally {
				sharingSync = false;
			}
		});
	}

	/**
	 * 关闭附魔台：共享语义（见类注释）。在 vanilla 的 {@code context.run(... dropInventory ...)}
	 * 之前拦截；有其他人打开时不走归还（防止把共享内容复制给最后一人）。
	 */
	@Inject(method = "onClosed",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/screen/ScreenHandlerContext;run(Ljava/util/function/BiConsumer;)V"),
			cancellable = true)
	private void enchantmentTable$keepOnClose(PlayerEntity player, CallbackInfo ci) {
		// 关闭界面即清空 tracker（与 keep 开关无关）。
		BlockPos trackerPos = OpenEnchantingTableTracker.get();
		String trackerDimKey = OpenEnchantingTableTracker.getDimensionKey();
		OpenEnchantingTableTracker.clear();
		if (sharingSync) {
			return;
		}
		this.context.run((world, pos) -> {
			if (!(world instanceof ServerWorld serverWorld)) {
				return;
			}
			BlockPos ip = pos.toImmutable();
			String dimensionKey = world.getRegistryKey().getValue().toString();
			boolean hasOthers = EnchantingTableOpeners.hasOtherOpeners(ip, player.getUuid());
			EnchantingTableOpeners.remove(ip, player.getUuid());
			LOGGER.info("onClosed hasOthers={}", hasOthers);
			ItemStack item = ((EnchantmentScreenHandler)(Object)this).getSlot(0).getStack();
			ItemStack lapis = ((EnchantmentScreenHandler)(Object)this).getSlot(1).getStack();
			// 清槽/归还会触发自身 onContentChanged → 共享写回注入：用 sharingSync 抑制，
			// 避免「关闭清槽」被误判为编辑操作（错误覆盖权威/写回他人）。
			sharingSync = true;
			try {
				if (hasOthers) {
					// 共享中：取消原版归还（防复制），只清自己的镜像槽；不动权威。
					ci.cancel();
					((EnchantmentScreenHandler)(Object)this).getSlot(0).setStack(ItemStack.EMPTY);
					((EnchantmentScreenHandler)(Object)this).getSlot(1).setStack(ItemStack.EMPTY);
					return;
				}
				// 最后一名打开者：按「关闭者本人的 keep 偏好」判定保留/退还（客户端经 C2S 上报，
				// 而非服务端全局配置——修复「房主关掉 keep 后其他成员（keep 开启）关桌也不保留」）。
				// keep 开则保留进存储（下次打开恢复）；keep 关则借原版归还机制把物品正常带出（退回背包）。
				boolean keep = PlayerKeepPrefs.getOrDefault(player.getUuid());
				if (keep) {
					ci.cancel();
					EnchantingTableStorage.store(serverWorld, ip, item, lapis);
					EnchantingTableNetworking.broadcastStored(serverWorld, ip, dimensionKey, item, lapis);
					((EnchantmentScreenHandler)(Object)this).getSlot(0).setStack(ItemStack.EMPTY);
					((EnchantmentScreenHandler)(Object)this).getSlot(1).setStack(ItemStack.EMPTY);
				} else {
					// keep 关闭：不拦截 vanilla 归还（物品退回玩家背包，位置由原版处理）；
					// 同时移除权威存储记录并广播空，防止下次打开旧内容复活。
					EnchantingTableStorage.take(serverWorld, ip);
					EnchantingTableNetworking.broadcastStored(serverWorld, ip, dimensionKey,
							ItemStack.EMPTY, ItemStack.EMPTY);
				}
			} finally {
				sharingSync = false;
			}
		});
	}

	/**
	 * 点击附魔选项生效瞬间（附魔成功时）在附魔台稍高处沿圆周向四周发射一圈彩色粒子。
	 */
	@Inject(method = "onButtonClick", at = @At("RETURN"))
	private void enchantmentTable$burstParticlesOnEnchant(PlayerEntity player, int id,
			CallbackInfoReturnable<Boolean> cir) {
		try {
			PreviewConfig.EnchantFxStyle style = PreviewConfig.get().enchantFxStyle;
			if (style == PreviewConfig.EnchantFxStyle.OFF || !cir.getReturnValue()) {
				return;
			}
			this.context.run((world, pos) -> {
				EnchantingPreviewRenderer.triggerEnchantBurst(pos);
			});
		} catch (Exception ignored) {
			// 触发失败不影响附魔本身
		}
	}
}