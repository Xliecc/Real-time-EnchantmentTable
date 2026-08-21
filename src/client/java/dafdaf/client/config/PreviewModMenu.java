package dafdaf.client.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Mod Menu 配置页入口（fabric.mod.json 的 {@code modmenu} entrypoint）。
 *
 * <p>依赖情况：
 * <ul>
 *   <li>Mod Menu 未安装：本 entrypoint 根本不会被加载，无影响。</li>
 *   <li>Mod Menu 已装但 Cloth Config 缺失：显示一个提示页（不会崩溃）。</li>
 *   <li>两者都装：弹出 Cloth Config 构建的配置页，编辑 {@link PreviewConfig} 并即时生效。</li>
 * </ul>
 *
 * <p>配置项按「通用 / 物品 / 青金石 / 附魔特效」四个分类组织：与待附魔物品相关的放「物品」，
 * 与青金石环绕相关的放「青金石」，粒子特效放「附魔特效」，两者共用的（开关类）放「通用」。
 *
 * <p>所有界面文案一律走 {@link Text#translatable} 本地化：中文在
 * {@code assets/enchantment-table/lang/zh_cn.json}，英文在 {@code en_us.json}，跟随游戏语言切换。
 *
 * <p>滑条采用<b>固定范围</b>：手柄位置 = 当前值在固定 {@code [min, max]} 中的相对位置，
 * 拖动保存后下次打开仍停在同一位置（真实反映数值），不做任何居中处理。
 */
public final class PreviewModMenu implements ModMenuApi {

	/** 配置界面文案的翻译键前缀（与 assets/enchantment-table/lang/*.json 保持一致）。 */
	private static final String K = "config.enchantment-table.";

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return parent -> {
			if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
				return missingClothConfigScreen(parent);
			}
			return buildScreen(parent);
		};
	}

	private static Screen buildScreen(Screen parent) {
		PreviewConfig cfg = PreviewConfig.get();

		ConfigBuilder builder = ConfigBuilder.create()
				.setParentScreen(parent)
				.setTitle(Text.translatable(K + "title"))
				.setSavingRunnable(cfg::save);
		ConfigEntryBuilder entry = builder.entryBuilder();

		// —— 分类：通用 ——
		ConfigCategory commonCat = builder.getOrCreateCategory(Text.translatable(K + "category.common"));

		commonCat.addEntry(entry.startEnumSelector(
						Text.translatable(K + "common.modToggle"), PreviewConfig.ModToggle.class, cfg.modToggle)
				.setDefaultValue(PreviewConfig.ModToggle.YES)
				.setEnumNameProvider(v -> Text.translatable(K + "modtoggle."
						+ (((PreviewConfig.ModToggle) v) == PreviewConfig.ModToggle.YES ? "yes" : "no")))
				.setTooltip(Text.translatable(K + "common.modToggle.tooltip"))
				.setSaveConsumer(v -> cfg.modToggle = v)
				.build());

		commonCat.addEntry(entry.startBooleanToggle(
						Text.translatable(K + "common.keepItems"), cfg.keepItemsWhenClosed)
				.setDefaultValue(false)
				.setTooltip(Text.translatable(K + "common.keepItems.tooltip"))
				.setSaveConsumer(value -> cfg.keepItemsWhenClosed = value)
				.build());

		commonCat.addEntry(entry.startBooleanToggle(
						Text.translatable(K + "common.animEnabled"), cfg.animEnabled)
				.setDefaultValue(true)
				.setTooltip(Text.translatable(K + "common.animEnabled.tooltip"))
				.setSaveConsumer(value -> cfg.animEnabled = value)
				.build());

		// —— 分类：物品（待附魔物品） ——
		ConfigCategory itemCat = builder.getOrCreateCategory(Text.translatable(K + "category.item"));

		itemCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "item.scale"),
						(int) Math.round(cfg.itemScale * 100.0), 5, 95)
				.setDefaultValue(65)
				.setTextGetter(h -> Text.translatable(K + "unit.blocks2", String.format("%.2f", h / 100.0)))
				.setSaveConsumer(h -> cfg.itemScale = h / 100.0)
				.build());

		itemCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "item.height"),
						(int) Math.round(cfg.itemHeight * 20.0), 1, 39)
				.setDefaultValue(15)
				.setTextGetter(t -> Text.translatable(K + "unit.blocks2", String.format("%.2f", t / 20.0)))
				.setSaveConsumer(t -> cfg.itemHeight = t / 20.0)
				.build());

		itemCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "item.rotation"), cfg.rotationSeconds, 1, 30)
				.setDefaultValue(8)
				.setTextGetter(sec -> Text.translatable(K + "unit.seconds0", Integer.toString(sec)))
				.setSaveConsumer(sec -> cfg.rotationSeconds = sec)
				.build());

		itemCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "item.emergeSpin"),
						(int) Math.round(cfg.emergeSpinTurns * 10.0), 0, 40)
				.setDefaultValue(10)
				.setTextGetter(t -> Text.translatable(K + "unit.turns", String.format("%.1f", t / 10.0)))
				.setTooltip(Text.translatable(K + "item.emergeSpin.tooltip"))
				.setSaveConsumer(t -> cfg.emergeSpinTurns = t / 10.0)
				.build());

		itemCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "item.retireSpin"),
						(int) Math.round(cfg.itemEmergeOutSpinTurns * 10.0), 0, 50)
				.setDefaultValue(5)
				.setTextGetter(t -> Text.translatable(K + "unit.turns", String.format("%.1f", t / 10.0)))
				.setTooltip(Text.translatable(K + "item.retireSpin.tooltip"))
				.setSaveConsumer(t -> cfg.itemEmergeOutSpinTurns = t / 10.0)
				.build());

		itemCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "item.floatSpeed"),
						(int) Math.round(cfg.floatSeconds), 1, 10)
				.setDefaultValue(3)
				.setTextGetter(sec -> Text.translatable(K + "unit.seconds0", Integer.toString(sec)))
				.setSaveConsumer(sec -> cfg.floatSeconds = sec)
				.build());

		itemCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "item.floatAmp"),
						(int) Math.round(cfg.floatAmplitude * 1000.0), 0, 200)
				.setDefaultValue(80)
				.setTextGetter(t -> Text.translatable(K + "unit.blocks3", String.format("%.3f", t / 1000.0)))
				.setSaveConsumer(t -> cfg.floatAmplitude = t / 1000.0)
				.build());

		itemCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "item.emergeTime"),
						(int) Math.round(cfg.itemEmergeSeconds * 10.0), 4, 30)
				.setDefaultValue(14)
				.setTextGetter(t -> Text.translatable(K + "unit.seconds1", String.format("%.1f", t / 10.0)))
				.setTooltip(Text.translatable(K + "item.emergeTime.tooltip"))
				.setSaveConsumer(t -> cfg.itemEmergeSeconds = t / 10.0)
				.build());

		itemCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "item.retireTime"),
						(int) Math.round(cfg.itemEmergeOutSeconds * 10.0), 2, 20)
				.setDefaultValue(8)
				.setTextGetter(t -> Text.translatable(K + "unit.seconds1", String.format("%.1f", t / 10.0)))
				.setTooltip(Text.translatable(K + "item.retireTime.tooltip"))
				.setSaveConsumer(t -> cfg.itemEmergeOutSeconds = t / 10.0)
				.build());

		itemCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "item.stagger"),
						(int) Math.round(cfg.emergeStaggerSeconds * 10.0), 1, 8)
				.setDefaultValue(2)
				.setTextGetter(t -> Text.translatable(K + "unit.seconds1", String.format("%.1f", t / 10.0)))
				.setTooltip(Text.translatable(K + "item.stagger.tooltip"))
				.setSaveConsumer(t -> cfg.emergeStaggerSeconds = t / 10.0)
				.build());

		// —— 分类：青金石（环绕材料） ——
		ConfigCategory lapisCat = builder.getOrCreateCategory(Text.translatable(K + "category.lapis"));

		lapisCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "lapis.scale"),
						(int) Math.round(cfg.lapisScale * 100.0), 5, 100)
				.setDefaultValue(40)
				.setTextGetter(h -> Text.translatable(K + "unit.blocks2", String.format("%.2f", h / 100.0)))
				.setSaveConsumer(h -> cfg.lapisScale = h / 100.0)
				.build());

		lapisCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "lapis.orbitRadius"),
						(int) Math.round(cfg.orbitRadius * 100.0), 20, 120)
				.setDefaultValue(65)
				.setTextGetter(h -> Text.translatable(K + "unit.blocks2", String.format("%.2f", h / 100.0)))
				.setSaveConsumer(h -> cfg.orbitRadius = h / 100.0)
				.build());

		// 青金石高度：显示离台面的绝对高度；渲染锚点 0.75+0.75=1.5 对应物品默认悬浮高度。
		// tick = (lapisHeight + 1.5) * 100；固定范围 [0, 250] 对应 lapisHeight[-1.5, +1.0]，
		// 落在 PreviewConfig clamp[-1.5,1.5] 内，拉满不会被 clamp 回退。
		lapisCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "lapis.height"),
						(int) Math.round((cfg.lapisHeight + 1.5) * 100.0), 0, 250)
				.setDefaultValue(125)
				.setTextGetter(h -> Text.translatable(K + "unit.blocks2", String.format("%.2f", h / 100.0)))
				.setTooltip(Text.translatable(K + "lapis.height.tooltip"))
				.setSaveConsumer(h -> cfg.lapisHeight = h / 100.0 - 1.5)
				.build());

		lapisCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "lapis.orbitSpeed"), cfg.orbitSeconds, 1, 60)
				.setDefaultValue(18)
				.setTextGetter(sec -> Text.translatable(K + "unit.seconds0", Integer.toString(sec)))
				.setSaveConsumer(sec -> cfg.orbitSeconds = sec)
				.build());

		lapisCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "lapis.selfSpin"),
						(int) Math.round(cfg.lapisRotationSeconds * 10.0), 10, 100)
				.setDefaultValue(60)
				.setTextGetter(t -> Text.translatable(K + "unit.seconds1", String.format("%.1f", t / 10.0)))
				.setSaveConsumer(t -> cfg.lapisRotationSeconds = t / 10.0)
				.build());

		lapisCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "lapis.emergeSpin"),
						(int) Math.round(cfg.lapisEmergeSpinTurns * 10.0), 0, 40)
				.setDefaultValue(10)
				.setTextGetter(t -> Text.translatable(K + "unit.turns", String.format("%.1f", t / 10.0)))
				.setTooltip(Text.translatable(K + "lapis.emergeSpin.tooltip"))
				.setSaveConsumer(t -> cfg.lapisEmergeSpinTurns = t / 10.0)
				.build());

		lapisCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "lapis.retireSpin"),
						(int) Math.round(cfg.lapisEmergeOutSpinTurns * 10.0), 0, 30)
				.setDefaultValue(3)
				.setTextGetter(t -> Text.translatable(K + "unit.turns", String.format("%.1f", t / 10.0)))
				.setTooltip(Text.translatable(K + "lapis.retireSpin.tooltip"))
				.setSaveConsumer(t -> cfg.lapisEmergeOutSpinTurns = t / 10.0)
				.build());

		lapisCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "lapis.floatAmp"),
						(int) Math.round(cfg.lapisFloatAmplitude * 1000.0), 0, 200)
				.setDefaultValue(75)
				.setTextGetter(t -> Text.translatable(K + "unit.blocks3", String.format("%.3f", t / 1000.0)))
				.setSaveConsumer(t -> cfg.lapisFloatAmplitude = t / 1000.0)
				.build());

		lapisCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "lapis.floatSpeed"),
						(int) Math.round(cfg.lapisFloatSeconds * 10.0), 5, 60)
				.setDefaultValue(25)
				.setTextGetter(t -> Text.translatable(K + "unit.seconds1", String.format("%.1f", t / 10.0)))
				.setSaveConsumer(t -> cfg.lapisFloatSeconds = t / 10.0)
				.build());

		lapisCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "lapis.emergeTime"),
						(int) Math.round(cfg.emergeSeconds * 10.0), 2, 30)
				.setDefaultValue(10)
				.setTextGetter(t -> Text.translatable(K + "unit.seconds1", String.format("%.1f", t / 10.0)))
				.setSaveConsumer(t -> cfg.emergeSeconds = t / 10.0)
				.build());

		lapisCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "lapis.retireTime"),
						(int) Math.round(cfg.emergeOutSeconds * 10.0), 2, 20)
				.setDefaultValue(8)
				.setTextGetter(t -> Text.translatable(K + "unit.seconds1", String.format("%.1f", t / 10.0)))
				.setSaveConsumer(t -> cfg.emergeOutSeconds = t / 10.0)
				.build());

		// —— 分类：特效（附魔生效瞬间的粒子爆发） ——
		ConfigCategory fxCat = builder.getOrCreateCategory(Text.translatable(K + "category.effect"));

		fxCat.addEntry(entry.startEnumSelector(
						Text.translatable(K + "fx.style"), PreviewConfig.EnchantFxStyle.class, cfg.enchantFxStyle)
				.setDefaultValue(PreviewConfig.EnchantFxStyle.ON)
				.setEnumNameProvider(style -> Text.translatable(K + "fxstyle."
						+ (((PreviewConfig.EnchantFxStyle) style) == PreviewConfig.EnchantFxStyle.ON ? "on" : "off")))
				.setTooltip(Text.translatable(K + "fx.style.tooltip"))
				.setSaveConsumer(style -> cfg.enchantFxStyle = style)
				.build());

		fxCat.addEntry(entry.startColorField(
						Text.translatable(K + "fx.color"), cfg.enchantFxColor)
				.setDefaultValue(0xFFFF50FF)
				.setAlphaMode(true)
				.setTooltip(Text.translatable(K + "fx.color.tooltip").styled(s -> s.withColor(Formatting.RED)))
				.setSaveConsumer(color -> cfg.enchantFxColor = color)
				.build());

		fxCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "fx.radius"),
						(int) Math.round(cfg.enchantFxRadius * 20.0), 6, 60)
				.setDefaultValue(24)
				.setTextGetter(t -> Text.translatable(K + "unit.blocks2", String.format("%.2f", t / 20.0)))
				.setSaveConsumer(t -> cfg.enchantFxRadius = t / 20.0)
				.build());

		fxCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "fx.density"), cfg.enchantFxDensity, 0, 100)
				.setDefaultValue(55)
				.setTextGetter(v -> Text.translatable(K + "unit.count", Integer.toString(v)))
				.setTooltip(Text.translatable(K + "fx.density.tooltip"))
				.setSaveConsumer(v -> cfg.enchantFxDensity = v)
				.build());

		fxCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "fx.height"),
						(int) Math.round((cfg.enchantFxHeight + 1.5) * 20.0), 20, 60)
				.setDefaultValue(35)
				.setTextGetter(t -> Text.translatable(K + "unit.blocks2", String.format("%.2f", t / 20.0 - 1.5)))
				.setTooltip(Text.translatable(K + "fx.height.tooltip"))
				.setSaveConsumer(t -> cfg.enchantFxHeight = t / 20.0 - 1.5)
				.build());

		fxCat.addEntry(entry.startIntSlider(
						Text.translatable(K + "fx.duration"),
						(int) Math.round(cfg.enchantFxDuration * 10.0), 1, 10)
				.setDefaultValue(4)
				.setTextGetter(t -> Text.translatable(K + "unit.seconds1", String.format("%.1f", t / 10.0)))
				.setSaveConsumer(t -> cfg.enchantFxDuration = t / 10.0)
				.build());

		return builder.build();
	}

	private static Screen missingClothConfigScreen(Screen parent) {
		return new Screen(Text.translatable(K + "error.noCloth")) {
			@Override
			protected void init() {
				super.init();
				this.addDrawableChild(ButtonWidget.builder(Text.translatable(K + "error.back"), button -> this.close())
						.dimensions(this.width / 2 - 50, this.height / 2, 100, 20)
						.build());
			}
		};
	}
}
