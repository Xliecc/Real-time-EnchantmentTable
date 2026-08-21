package dafdaf.client.enchanting;

import net.minecraft.util.math.BlockPos;

/**
 * 记录「当前正在使用的附魔台」的方块位置，供渲染线程在 GUI 打开时定位预览。
 *
 * <p><b>为什么不能直接读客户端 handler 的 {@code ScreenHandlerContext}：</b>客户端打开附魔台
 * 界面时，{@code EnchantmentScreen} 里的 handler 是网络镜像——由
 * {@code ScreenHandlerType.create(syncId, inventory)} 用 {@code ScreenHandlerContext.EMPTY}
 * 构造（OpenScreen 数据包只带 syncId/标题，不带方块坐标），因此客户端的 {@code context}
 * 恒为空，读不到坐标。
 *
 * <p><b>正确来源 = 服务端的真实 context：</b>玩家右击附魔台时，服务端
 * {@code EnchantingTableBlock.createScreenHandlerFactory} 用真实坐标构造 handler
 * （{@code ScreenHandlerContext.create(world, pos)}）。本 mod 单机运行（集成服务器与客户端
 * 同 JVM），因此由 {@link dafdaf.client.mixin.EnchantmentScreenHandlerMixin} 在服务端线程
 * 构造 handler 时把坐标写入本类（服务端线程写、渲染线程读，用 {@code volatile} 保证可见性），
 * 渲染线程只需读 {@link #get()}——与玩家视线、站位无关。
 *
 * <p>打开时设置、关闭时清空（见 mixin）；渲染器仅在 {@code EnchantmentScreen} 打开时才读取，
 * 并在读取后校验该位置仍是附魔台。
 */
public final class OpenEnchantingTableTracker {

	/** 当前打开的附魔台位置；无打开界面时（关闭后已清空）为 {@code null}。 */
	private static volatile BlockPos current;

	/** 当前打开附魔台所在维度的 id 字符串（如 {@code minecraft:overworld}），与 {@link #current} 一并写入。 */
	private static volatile String currentDimensionKey;

	private OpenEnchantingTableTracker() {
	}

	/** 服务端构造真实 handler 时写入（mixin 在 {@code ScreenHandlerContext.run} 回调内调用）。 */
	public static void set(BlockPos pos, String dimensionKey) {
		current = pos;
		currentDimensionKey = dimensionKey;
	}

	/** 关闭附魔台界面时清空（mixin 在 {@code onClosed} 无条件调用，客户端/服务端任一侧先执行）。 */
	public static void clear() {
		current = null;
		currentDimensionKey = null;
	}

	/** 渲染线程读取：当前打开的 handler 对应的附魔台位置；无则为 {@code null}。 */
	public static BlockPos get() {
		return current;
	}

	/**
	 * 当前打开附魔台所在维度的 id 字符串；供关闭时把物品写入存储缓存
	 * （{@link EnchantingTableStorage#storeCached}）按维度建键用——客户端镜像 handler 的
	 * {@code context} 为 EMPTY 拿不到 world，坐标与维度都来自服务端写入的 tracker。
	 */
	public static String getDimensionKey() {
		return currentDimensionKey;
	}
}
