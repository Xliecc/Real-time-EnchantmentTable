# Modrinth 上传资料包（v1.0.0）

> 供用户登录 Modrinth 后手动上传，或提供 API token 后代为上传（本会话不做自动上传）。
> 上传 jar = `build/libs/Real-time EnchantmentTable-1.0.0.jar`（563530 字节）。

---

## 1. 基本信息

| 字段 | 值 |
|---|---|
| 项目标题（Name） | 实时附魔台 / Real-time EnchantmentTable |
| 项目 ID / Slug（上传后系统生成/建议） | `real-time-enchantmenttable`（全小写连字符，与 mod id 一致，合规） |
| 作者 | Xliecc |
| 版本号 | 1.0.0 |
| 协议（License） | MIT |
| 加载器（Loaders） | Fabric |
| 支持游戏版本（Game versions） | 1.21.11 |
| 环境（Side） | **Client + Server 均可**（`environment: "*"`；视觉为客户端，服务端无感） |

---

## 2. 简介 / 描述（Description）

### 2.1 短简介（Short summary，项目页一行）
> 在附魔台上方以 3D 形式实时预览其中物品，拥有丝滑的动画与大量自定义配置。
> A real-time 3D preview of the enchanting table contents, with smooth animations and lots of customization.

### 2.2 项目描述（Markdown，放完整 description 区）

````markdown
# 实时附魔台 / Real-time EnchantmentTable

在**附魔台上方**以 3D 形式**实时预览**其中的物品：待附魔物品从悬浮书中升起、悬浮自转并缓慢起伏，青金石颗粒绕物品环绕飞行；附魔成功瞬间还有扩散粒子特效。

A real-time **3D preview** floating **above the enchanting table**: the item rises from the floating book, hovers, spins and gently bobs, while lapis shards orbit around it; a spreading particle ring fires on enchant success.

## 特性 / Features

- ✨ **实时 3D 预览**：物品以 3D 形式悬浮在附魔台上方，放入/取回/移除均有丝滑进出场动画。
- 🔮 **青金石环绕**：放入青金石后，颗粒依次冒出并绕物品等角环绕、各自起伏。
- 🎞️ **丝滑动画**：旋转、浮动、进出场飞行、快旋等动画参数全部可调。
- 🎛️ **大量自定义配置**：物品/青金石大小、高度、轨道、速度、幅度、粒子特效等（经 Mod Menu + Cloth Config 配置）。
- 📦 **附魔台当容器**（可选）：关闭界面后物品按「维度+坐标」归档，重开自动恢复。
- 🌐 **局域网共享**：多人同开一台附魔台时实时共享内容，按玩家记录各自偏好。
- 🌏 **中英双语**界面与简介。

## 安装 / Installation

1. 安装 [Fabric Loader](https://fabricmc.net/use/)（>= 0.19.3）。
2. 安装 [Fabric API](https://modrinth.com/mod/fabric-api)。
3. 安装本模组（依赖见下）。
4. 强烈建议安装 [Mod Menu](https://modrinth.com/mod/modmenu) 与 [Cloth Config](https://modrinth.com/mod/cloth-config) 以获得配置界面。

## 依赖 / Dependencies

| 依赖 | 类型 | 说明 |
|---|---|---|
| [Fabric API](https://modrinth.com/mod/fabric-api) | 必需（`depends`） | 运行必需 |
| [Mod Menu](https://modrinth.com/mod/modmenu) | 可选（强烈建议） | 配置入口 |
| [Cloth Config](https://modrinth.com/mod/cloth-config) | 可选（强烈建议） | 配置界面；缺失时模组仍可运行，但无配置界面 |

> Java 21+，Minecraft 1.21.11，Fabric Loader >= 0.19.3。

## 源码 / Source

[GitHub: Xliecc/Real-time-EnchantmentTable](https://github.com/Xliecc/Real-time-EnchantmentTable)

## 许可 / License

[MIT](LICENSE)。
````

---

## 3. 版本信息（Version）

| 字段 | 值 |
|---|---|
| 版本号 | 1.0.0 |
| 加载器 | Fabric |
| 游戏版本 | 1.21.11 |
| 上传文件 | `Real-time EnchantmentTable-1.0.0.jar`（563530 字节） |
| 版本类型（Release type） | Release（正式版） |
| 主要版本（featured） | 是 |

---

## 4. 其他平台备注

- **GitHub**：仓库已发布（公开、MIT、CI 通过），Release v1.0.0 已建好并附带 jar 下载。
- **CurseForge**（可选）：1.21 模组上传有运营门槛（见其平台政策），如需上传参照本资料包的简介/依赖说明。