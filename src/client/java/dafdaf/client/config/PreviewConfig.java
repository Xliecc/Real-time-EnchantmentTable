package dafdaf.client.config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
/**
 * 附魔台预览的运行时配置（无 Cloth Config 依赖，纯 Gson 持久化）。
 *
 * <p>由 {@code com.terraformersmc.modmenu.api.ModMenuApi} 的配置页编辑，{@code save()} 后
 * {@link dafdaf.client.enchanting.EnchantingPreviewRenderer} 每帧读取最新值，无需重启即可生效。
 * 配置文件位于 {@code config/enchantment-table.json}。
 *
 * <p>动画默认值参考 EasyMagic（简易附魔台）的观感：物品弹出 + 约 6 秒/圈自转 +
 * 大幅度慢起伏；青金石等角环绕（18 秒/圈）+ 每颗错相快速起伏（波纹感）。
 */
public final class PreviewConfig {
	/** 预览光照模式：跟随世界光照（昼夜/室内外自然明暗）/ 固定全亮。 */
	public enum LightingMode {
		WORLD, FULL
	}
	/** 模组启用开关（「是/否」显示用枚举选择器，避免 Cloth 布尔项显示英文 True/False）。 */
	public enum ModToggle {
		YES,  // 是
		NO    // 否
	}
	/** 附魔生效瞬间的粒子效果开关。 */
	public enum EnchantFxStyle {
		OFF,   // 关闭
		ON     // 启用（横向扩散圈）
	}
	/** 预览光照模式。跟随世界光照时在附魔台上方采样方块光+天空光，夜晚/室内会变暗、
	 * 光影下不显得自发光；固定全亮则忽略环境光，始终最亮。 */
	public LightingMode lightingMode = LightingMode.WORLD;
	/** 模组总开关（旧布尔字段，向前兼容保留；界面改用 {@link #modToggle}）。 */
	public Boolean modEnabled = true;
	/** 模组启用开关（是/否）。关闭后不再渲染任何预览与特效（仍保留存储逻辑以免丢物品）。 */
	public ModToggle modToggle = ModToggle.YES;
	/** 启用进出场动画（物品/青金石从书中冒出与收回应有的飞行动画）；关闭时物品/青金石
	 * 直接出现在/消失于悬浮位（节约资源、贴近原版静态显示）。 */
	public Boolean animEnabled = true;
	/** 附魔生效粒子效果开关：OFF 关闭 / ON 启用（横向扩散圈）。 */
	public EnchantFxStyle enchantFxStyle = EnchantFxStyle.ON;
	/** 附魔生效粒子效果主色（ARGB，默认附魔紫色 0xFFFF50FF）。 */
	public int enchantFxColor = 0xFFFF50FF;
	/** 附魔生效粒子效果扩散半径（格）。 */
	public double enchantFxRadius = 1.2;
	/** 附魔生效粒子效果持续时长（秒）。 */
	public double enchantFxDuration = 0.4;
	/** 粒子浓淡（0~100）：越高粒子越多越大（更浓），越低越稀疏越小（更淡）。 */
	public int enchantFxDensity = 55;
	/** 粒子圈平面相对台面（y+0.75）的高度偏移（格）。默认 0.25（物品中偏上）；
	 * 可选预设 -1.20 让扩散圈压在工作台底部（近台面下沿）。 */
	public double enchantFxHeight = 0.25;
	/**
	 * 「附魔台作为容器」：开启后附魔台像容器一样把两个槽位的物品（待附魔物品 + 青金石）按
	 * 维度+坐标归档到本 mod 的持久化存储，关闭界面不退还玩家、下次打开同一位置的附魔台
	 * 仍可查看/操作；关闭则保持原版逻辑（物品退还玩家/掉落）。用装箱类型以便区分
	 * 「配置缺失」与「显式 false」。
	 */
	public Boolean keepItemsWhenClosed = false;
	/** 待附魔物品预览的缩放（物品多为 2D 精灵，1.0 约为满一格的视觉大小）。
	 *  默认值已同步为用户当前调定值 0.65。 */
	public double itemScale = 0.65;
	/** 物品基座高于附魔台顶面（y+0.75）的距离（格）。默认高过原版悬浮的书。 */
	public double itemHeight = 0.75;
	/** 物品自转一圈所需秒数（越大越慢）。EasyMagic 观感约 6.3 秒/圈。 */
	public int rotationSeconds = 8;
	/** 物品入场附加快旋的圈数（从书中冒出时附加的整圈数）。 */
	public double emergeSpinTurns = 1.0;
	/** 物品出场（收回进书）时附加的快旋圈数：0 = 收回时不自转加速，仅正常自转。 */
	public double itemEmergeOutSpinTurns = 0.5;
	/** 物品浮动速度：一整个来回（上→下→上）所需秒数（越小越快）。 */
	public double floatSeconds = 3;
	/** 物品浮动幅度（格，峰值偏移量）。EasyMagic 观感约 0.1。 */
	public double floatAmplitude = 0.08;
	/** 青金石预览的缩放。 */
	public double lapisScale = 0.4;
	/** 青金石环绕轨道的半径（格，绕物品中心）。 */
	public double orbitRadius = 0.65;
	/** 青金石轨道平面相对台面（y+0.75）的高度偏移（格）。0 表示跟随物品悬浮高度；负值更低。 */
	public double lapisHeight = -0.25;
	/** 青金石环绕一圈所需秒数（越大越慢）。 */
	public int orbitSeconds = 18;
	/** 青金石自身旋转一圈所需秒数。 */
	public double lapisRotationSeconds = 6.0;
	/** 青金石环绕过程中的垂直起伏幅度（格）。EasyMagic 观感约 0.075。 */
	public double lapisFloatAmplitude = 0.075;
	/** 青金石垂直起伏速度（秒/来回）。EasyMagic 观感约 1.57 秒（错相波纹感）。 */
	public double lapisFloatSeconds = 2.5;
	/** 物品「入场」（从书中冒出到位置）飞行用时（秒）。 */
	public double itemEmergeSeconds = 1.4;
	/** 物品「出场」（从位置收回进书）飞行用时（秒）。 */
	public double itemEmergeOutSeconds = 0.8;
	/** 青金石「入场」（从书中冒出到轨道）飞行用时（秒）。 */
	public double emergeSeconds = 1.0;
	/** 青金石「出场」（从轨道收回进书）飞行用时（秒）。 */
	public double emergeOutSeconds = 0.8;
	/** 多颗青金石依次出场的错峰间隔（秒）；回收同样按此间隔依次飞回。 */
	public double emergeStaggerSeconds = 0.2;
	/** 青金石「入场」快旋圈数（从书中冒出时附加旋转的整圈数）。 */
	public double lapisEmergeSpinTurns = 1.0;
	/** 青金石「出场」（回收进书）时的快旋圈数。 */
	public double lapisEmergeOutSpinTurns = 0.3;
	private static final double MIN_ITEM_SCALE = 0.10;
	private static final double MAX_ITEM_SCALE = 3.00;
	private static final double MIN_ITEM_HEIGHT = 0.05;
	private static final double MAX_ITEM_HEIGHT = 2.2;
	private static final int MIN_ROTATION_SECONDS = 1;
	private static final int MAX_ROTATION_SECONDS = 60;
	private static final double MIN_EMERGE_SPIN_TURNS = 0.0;
	private static final double MAX_EMERGE_SPIN_TURNS = 4.5;
	private static final double MIN_FLOAT_SECONDS = 1;
	private static final double MAX_FLOAT_SECONDS = 30;
	private static final double MIN_FLOAT_AMPLITUDE = 0.0;
	private static final double MAX_FLOAT_AMPLITUDE = 0.25;
	private static final double MIN_LAPIS_SCALE = 0.05;
	private static final double MAX_LAPIS_SCALE = 1.20;
	private static final double MIN_ORBIT_RADIUS = 0.15;
	private static final double MAX_ORBIT_RADIUS = 1.4;
	private static final double MIN_LAPIS_HEIGHT = -1.5;
	private static final double MAX_LAPIS_HEIGHT = 1.5;
	private static final int MIN_ORBIT_SECONDS = 1;
	private static final int MAX_ORBIT_SECONDS = 60;
	private static final double MIN_LAPIS_ROTATION_SECONDS = 0.5;
	private static final double MAX_LAPIS_ROTATION_SECONDS = 60.0;
	private static final double MIN_LAPIS_FLOAT_AMPLITUDE = 0.0;
	private static final double MAX_LAPIS_FLOAT_AMPLITUDE = 0.25;
	private static final double MIN_LAPIS_FLOAT_SECONDS = 0.5;
	private static final double MAX_LAPIS_FLOAT_SECONDS = 30;
	private static final double MIN_EMERGE_SECONDS = 0.2;
	private static final double MAX_EMERGE_SECONDS = 3.5;
	private static final double MIN_EMERGE_STAGGER_SECONDS = 0.1;
	private static final double MIN_FX_RADIUS = 0.3;
	private static final double MIN_FX_HEIGHT = -1.5;
	private static final double MAX_FX_HEIGHT = 1.8;
	private static final double MAX_FX_RADIUS = 3.5;
	private static final double MIN_FX_DURATION = 0.2;
	private static final double MAX_FX_DURATION = 3.0;
	private static final int MIN_FX_COLOR = 0;
	private static final int MAX_FX_COLOR = 0xFFFFFFFF;
	private static final double MAX_EMERGE_STAGGER_SECONDS = 1.0;
	private static final double MIN_LAPIS_EMERGE_SPIN = 0.0;
	private static final double MAX_LAPIS_EMERGE_SPIN = 4.5;
	private static final double MIN_ITEM_EMERGE_OUT_SPIN = 0.0;
	private static final double MAX_ITEM_EMERGE_OUT_SPIN = 6.0;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** 单例。<b>必须声明在 {@link #GSON} 之后</b>：静态初始化按声明顺序执行，
	 * {@code INSTANCE} 在构造里会调 {@link #load()}，而 load 用 GSON——若 GSON 尚未
	 * 初始化（null）会 NPE，导致每次启动配置被静默重置为默认值。 */
	private static final PreviewConfig INSTANCE = new PreviewConfig();
	private PreviewConfig() {
		load();
	}
	public static PreviewConfig get() {
		return INSTANCE;
	}
	private Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("enchantment-table.json");
	}
	/** 从磁盘加载配置；文件缺失或损坏时保留默认值。 */
	public synchronized void load() {
		Path path = file();
		if (!Files.exists(path)) {
			return;
		}
		try {
			PreviewConfig loaded = GSON.fromJson(Files.readString(path), PreviewConfig.class);
			if (loaded == null) {
				return;
			}
			if (loaded.lightingMode != null) {
				this.lightingMode = loaded.lightingMode;
			}
			if (loaded.modEnabled != null) {
				this.modEnabled = loaded.modEnabled;
			}
			if (loaded.modToggle != null) {
				this.modToggle = loaded.modToggle;
			}
			if (loaded.animEnabled != null) {
				this.animEnabled = loaded.animEnabled;
			}
			if (loaded.keepItemsWhenClosed != null) {
				this.keepItemsWhenClosed = loaded.keepItemsWhenClosed;
			}
			if (loaded.enchantFxStyle != null) {
				this.enchantFxStyle = loaded.enchantFxStyle;
			}
			// 0 表示旧配置没这个字段（Gson 默认 int=0），保留当前默认色；
			// 用户真的选了纯黑(0)颜色时也保留黑（没人会选纯黑还抱怨看不见）。
			if (loaded.enchantFxColor != 0) {
				this.enchantFxColor = loaded.enchantFxColor;
			}
			this.enchantFxRadius = clampDouble(loaded.enchantFxRadius, MIN_FX_RADIUS, MAX_FX_RADIUS);
			this.enchantFxDuration = clampDouble(loaded.enchantFxDuration, MIN_FX_DURATION, MAX_FX_DURATION);
			this.enchantFxDensity = clampInt(loaded.enchantFxDensity, 0, 100);
			this.enchantFxHeight = clampDouble(loaded.enchantFxHeight, MIN_FX_HEIGHT, MAX_FX_HEIGHT);
			this.itemScale = clampDouble(loaded.itemScale, MIN_ITEM_SCALE, MAX_ITEM_SCALE);
			this.itemHeight = clampDouble(loaded.itemHeight, MIN_ITEM_HEIGHT, MAX_ITEM_HEIGHT);
			this.rotationSeconds = clampInt(loaded.rotationSeconds, MIN_ROTATION_SECONDS, MAX_ROTATION_SECONDS);
			this.emergeSpinTurns = clampDouble(loaded.emergeSpinTurns, MIN_EMERGE_SPIN_TURNS, MAX_EMERGE_SPIN_TURNS);
			this.itemEmergeOutSpinTurns = clampDouble(loaded.itemEmergeOutSpinTurns, MIN_ITEM_EMERGE_OUT_SPIN, MAX_ITEM_EMERGE_OUT_SPIN);
			this.lapisEmergeSpinTurns = clampDouble(loaded.lapisEmergeSpinTurns, MIN_LAPIS_EMERGE_SPIN, MAX_LAPIS_EMERGE_SPIN);
			this.lapisEmergeOutSpinTurns = clampDouble(loaded.lapisEmergeOutSpinTurns, MIN_LAPIS_EMERGE_SPIN, MAX_LAPIS_EMERGE_SPIN);
			this.floatSeconds = clampDouble(loaded.floatSeconds, MIN_FLOAT_SECONDS, MAX_FLOAT_SECONDS);
			this.floatAmplitude = clampDouble(loaded.floatAmplitude, MIN_FLOAT_AMPLITUDE, MAX_FLOAT_AMPLITUDE);
			this.lapisScale = clampDouble(loaded.lapisScale, MIN_LAPIS_SCALE, MAX_LAPIS_SCALE);
			this.orbitRadius = clampDouble(loaded.orbitRadius, MIN_ORBIT_RADIUS, MAX_ORBIT_RADIUS);
			this.lapisHeight = clampDouble(loaded.lapisHeight, MIN_LAPIS_HEIGHT, MAX_LAPIS_HEIGHT);
			this.orbitSeconds = clampInt(loaded.orbitSeconds, MIN_ORBIT_SECONDS, MAX_ORBIT_SECONDS);
			this.lapisRotationSeconds = clampDouble(loaded.lapisRotationSeconds, MIN_LAPIS_ROTATION_SECONDS, MAX_LAPIS_ROTATION_SECONDS);
			this.lapisFloatAmplitude = clampDouble(loaded.lapisFloatAmplitude,
					MIN_LAPIS_FLOAT_AMPLITUDE, MAX_LAPIS_FLOAT_AMPLITUDE);
			this.lapisFloatSeconds = clampDouble(loaded.lapisFloatSeconds,
					MIN_LAPIS_FLOAT_SECONDS, MAX_LAPIS_FLOAT_SECONDS);
			this.itemEmergeSeconds = clampDouble(loaded.itemEmergeSeconds, MIN_EMERGE_SECONDS, MAX_EMERGE_SECONDS);
			this.itemEmergeOutSeconds = clampDouble(loaded.itemEmergeOutSeconds, MIN_EMERGE_SECONDS, MAX_EMERGE_SECONDS);
			this.emergeSeconds = clampDouble(loaded.emergeSeconds, MIN_EMERGE_SECONDS, MAX_EMERGE_SECONDS);
			this.emergeStaggerSeconds = clampDouble(loaded.emergeStaggerSeconds,
					MIN_EMERGE_STAGGER_SECONDS, MAX_EMERGE_STAGGER_SECONDS);
			this.emergeOutSeconds = clampDouble(loaded.emergeOutSeconds, MIN_EMERGE_SECONDS, MAX_EMERGE_SECONDS);
		} catch (Exception e) {
			// 配置损坏：回退默认值
	}
	}
	/** 写入磁盘。 */
	public synchronized void save() {
		try {
			Path path = file();
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this));
		} catch (IOException e) {
			// 忽略写入失败（不影响游戏运行）
		}
	}
	private static int clampInt(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
	private static double clampDouble(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}