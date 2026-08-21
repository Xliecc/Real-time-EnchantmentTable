# Real-time EnchantmentTable

> Client-side Fabric mod that adds a 3D visual preview to the vanilla Enchanting Table.
> Author: Xliecc　｜　License: MIT

📖 **[中文说明](README_zh.md)**

## Features
- **Real-time 3D preview**: Open an Enchanting Table and place an item — the item rises from the floating book, hovers, spins, and gently bobs. Place lapis lazuli and up to 3 shards emerge one by one, orbit the item evenly, and bob independently. Retrieving/removing them plays the reverse animation back into the book.
- **Synced with the book**: Walk within 3 blocks of the table and the book opens and the item emerges; walk away and it flies back and closes.
- **Keep items after closing (optional)**: Keeps the items in the table when the GUI closes. Items are persisted per dimension + position and restored when reopening the same table. The preview keeps displaying.
- **LAN sharing**: When multiple players use the same table, contents stay in sync in real time, with per-player preferences.
- **Mod Menu config screen**: All animation parameters can be adjusted graphically, applied immediately without restart.
- **Enchanting FX**: A spreading particle ring on successful enchanting.

Adds no new items, blocks, or recipes — purely a client-side visual enhancement.

## Dependencies
- Minecraft **1.21.11**
- [Fabric Loader](https://fabricmc.net/use/) ≥ **0.19.3**
- [Fabric API](https://modrinth.com/mod/fabric-api)
- Optional: [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config) (provide the config screen; the mod runs fine without them)

## Installation
Put `build/libs/Real-time EnchantmentTable-1.0.0.jar` into `.minecraft/mods/`.

> Tip: With Mod Menu + Cloth Config installed, the graphical config screen opens from Mod Menu (optional; the mod runs fine without them).

## Configuration
- Open Mod Menu → this mod → config screen for graphical editing.
- Or edit `config/enchantment-table.json` directly.
- Key adjustable values: item/lapis size, heights, rotation, floating, enter/exit flight, orbit, particle FX, etc.
- All config texts are localized (中文/EN), following the game language.

## Building
Requires JDK 21.

```bash
# Windows
gradlew.bat build
# macOS / Linux
./gradlew build
```

Artifacts are produced at `build/libs/Real-time EnchantmentTable-<version>.jar` (a `-sources.jar` source bundle is also produced for optional redistribution).

## License
[MIT](LICENSE) © 2026 Xliecc