<div align="center">
  <img src="src/main/resources/assets/neodymium2/icon.png?v=rc8" alt="Neodymium 2 Logo" width="160" height="160" />
  <h1>Neodymium 2</h1>
  <p><strong>Selectable server optimization for Minecraft 26.3 Fabric — you choose where to keep 100% vanilla mechanics and where to optimize for TPS.</strong></p>
</div>

> [!NOTE]
> **AI-Generated Project ("AI Slop" Disclosure)**  
> This mod's code, optimization logic, tests, and documentation were written by an AI coding assistant (**Google Antigravity**). Use at your own discretion.

---

## What It Does

Most optimization mods force the same slowdown rules across the entire server, leaving server owners with an all-or-nothing choice between smooth TPS and working redstone/farms.

**Neodymium 2** puts **control in your hands** with **selectable, area-based optimization**:

* **You Choose What Stays 100% Vanilla**: Mark any chunk or region (redstone contraptions, technical farms, trading halls) as protected, and it will always run at full vanilla speed without interference.
* **Optimize Everything Else**: Outside those selected areas, distant mobs, idle villagers, and unused chunks are throttled to keep your server running at a smooth **20 TPS**.
* **Farm-Friendly Wilderness Spawning**: Cuts out laggy deep-cave spawns in the wilderness while keeping standard overworld mob farms working as expected.
* **Flexible Control (Standalone or Plugin-Linked)**: Select protected chunks directly with `/neodymium2 chunk` commands, or let players/admins toggle protection through an external region mod like `YAWPManager`.

---

## Requirements

* **Minecraft**: `26.3`
* **Fabric Loader**: `>=0.19.3` (with `fabric-api`)
* **Java**: `>=25`
* **Required Mod**: **[ServerCore](https://github.com/Wesley1808/ServerCore)** (`1.5.20+26.3`)

---

## Commands

| Command | Description |
| :--- | :--- |
| `/neodymium2 status` | View server TPS/MSPT, protected area count, and performance stats |
| `/neodymium2 modules` | View active optimization modules |
| `/neodymium2 regions [page]` | Browse protected areas with clickable **`[TP]`**, **`[Manage]`**, and **`[Remove]`** buttons |
| `/neodymium2 nametags [page]` | Check status of name-tagged mobs in loaded chunks |
| `/neodymium2 chunk toggle` | Toggle vanilla protection on/off for the chunk you are standing in |
| `/neodymium2 chunk add [radius]` | Protect the current chunk or a square radius of chunks around you |
| `/neodymium2 chunk remove` | Remove protection from the current chunk |
| `/neodymium2 lang [auto\|en_us\|ko_kr]` | Switch display language (`auto` detects client language) |
| `/neodymium2 reload` | Reload `config/neodymium2/config.json` and `regions.json` |

---

## Developer API (Optional)

External region mods can hook into Neodymium 2 via `kr.harusarri.neodymium2.api.NeodymiumApi`:

```java
NeodymiumApi.registerRegionProvider(provider);
NeodymiumApi.setVanillaSensitive("minecraft:overworld", "my_region", true);
boolean isProtected = NeodymiumApi.isProtectedBlock(level, blockPos);
```

---

## Build from Source

```bash
./gradlew build
```

The compiled JAR is generated in `build/libs/`.

---

## Credits & License

* Inspired by optimization ideas from **[Paper](https://github.com/PaperMC/Paper)**, **[Pufferfish](https://github.com/pufferfish-gg/Pufferfish)**, **[Purpur](https://github.com/PurpurMC/Purpur)**, **[Gale](https://github.com/GaleMC/Gale)**, **[ServerCore](https://github.com/Wesley1808/ServerCore)**, **[Lithium](https://github.com/CaffeineMC/lithium)**, and **[Fabric Carpet](https://github.com/gnembon/fabric-carpet)**.
* Developed with the assistance of **Google Antigravity**.
* Licensed under the **[MIT License](./LICENSE)**.
