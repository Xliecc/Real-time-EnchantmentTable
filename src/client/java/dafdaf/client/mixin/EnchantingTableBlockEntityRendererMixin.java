package dafdaf.client.mixin;

import dafdaf.client.enchanting.EnchantingPreviewRenderer;

import net.minecraft.client.render.block.entity.EnchantingTableBlockEntityRenderer;
import net.minecraft.client.render.block.entity.state.EnchantingTableBlockEntityRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把附魔台预览物品渲染注入原版方块实体渲染（BER）阶段。
 *
 * <p>原版 1.21.11 的世界渲染顺序是：地形 → submitBlockEntities（BER）→ 实体 →
 * Fabric 的 AFTER_ENTITIES 世界事件。BER 中提交的几何会进入 Iris/BSL 阴影贴图
 * 捕获范围（与 EasyMagic 同款方案），因此预览物品在光影下可以投射出真实阴影；
 * 而 AFTER_ENTITIES 里的自定义渲染在阴影 pass 中不会被回放、没有阴影。
 *
 * <p>进入 {@code render} 时 {@link MatrixStack} 已被 {@code WorldRenderer#renderBlockEntities}
 * 平移至<b>方块原点</b>（相机相对坐标），因此直接用局部坐标（0.5, y, 0.5）渲染即可。
 */
@Mixin(EnchantingTableBlockEntityRenderer.class)
public abstract class EnchantingTableBlockEntityRendererMixin {


	@Inject(method = "render", at = @At("HEAD"))
	private void dafdaf$renderPreview(EnchantingTableBlockEntityRenderState state, MatrixStack matrices,
			OrderedRenderCommandQueue queue, CameraRenderState cameraState, CallbackInfo ci) {
		EnchantingPreviewRenderer.renderBer(state, matrices, queue);
	}
}