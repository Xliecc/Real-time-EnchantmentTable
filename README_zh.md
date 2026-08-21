# 附魔台预览（Real-time EnchantmentTable）

> 纯客户端 Fabric 模组，为原版附魔台添加 3D 可视化预览与「附魔台当容器」能力。
> 作者：Xliecc　｜　许可：MIT

📖 **[English README](README.md)**

## 功能
- **3D 可视化预览**：打开附魔台放入物品，待附魔物品从悬浮书中升起、悬浮自转并缓慢起伏；放入青金石后，青金石颗粒（封顶 3 颗）依次从书中冒出、绕物品等角环绕并各自起伏。取回/移除时反向飞回书中。
- **与书本同步**：走近附魔台（中心 3 格内）书打开、物品冒出；走远书合上、物品飞回收起。
- **关闭附魔台后保留物品**（可开关）：关闭界面后物品不退还、按「维度+坐标」归档到 JSON，下次打开同一附魔台自动恢复；关闭后预览持续显示。
- **局域网共享**：多人同开一台附魔台时实时共享内容，按玩家记录各自偏好。
- **Mod Menu 配置页**：全部动画参数图形化调节，保存即时生效，无需重启。
- **附魔特效**：附魔成功瞬间的扩散粒子圈。

不新增任何物品/方块/配方，纯客户端显示效果。

## 依赖
- Minecraft **1.21.11**
- [Fabric Loader](https://fabricmc.net/use/) ≥ **0.19.3**
- [Fabric API](https://modrinth.com/mod/fabric-api)
- 可选：[Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config)（提供配置界面；不装也能正常运行）

## 安装
将 `build/libs/Real-time EnchantmentTable-1.0.0.jar` 放入 `.minecraft/mods/` 即可。

> 提示：Mod Menu + Cloth Config 安装后可在 Mod Menu 里打开本模组的图形化配置页（可选依赖，不装也能正常运行）。

## 配置
- 打开 Mod Menu → 本模组 → 配置页，图形化调节。
- 或直接编辑 `config/enchantment-table.json`。
- 主要可调项：物品/青金石大小、高度、旋转、浮动、进出场飞行、环绕轨道、粒子特效等。
- 全部配置项的文案已中英本地化，随游戏语言切换。

## 构建
需要 JDK 21。

```bash
# Windows
gradlew.bat build
# macOS / Linux
./gradlew build
```

产物位于 `build/libs/Real-time EnchantmentTable-<version>.jar`（另产出 `-sources.jar` 源码包，供自愿分发）。

## 许可
[MIT](LICENSE) © 2026 Xliecc