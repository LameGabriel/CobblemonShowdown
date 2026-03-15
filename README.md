# CobblemonShowdown

A **Fabric 1.21.1** Minecraft mod that adds a **matchmaking queue** for Pokémon PvP battles
powered by the [Cobblemon](https://cobblemon.com) mod.

---

## Features

| Feature | Details |
|---------|---------|
| **Queue system** | Players join a global FIFO queue and are automatically paired for a 1v1 singles battle |
| **Instant matching** | The first two queued players are matched immediately; subsequent arrivals are matched within one second |
| **Offline cleanup** | Disconnected players are removed from the queue automatically |
| **Battle validation** | Cobblemon's own `BattleBuilder` handles party validation (no Pokémon → friendly error, not re-queued) |
| **Commands** | Simple `/showdown queue` command tree (see below) |

---

## Commands

| Command | Description |
|---------|-------------|
| `/showdown queue join` | Join the matchmaking queue |
| `/showdown queue leave` | Leave the queue |
| `/showdown queue status` | Show current queue size and your position |
| `/showdown queue` | Alias for `status` |

---

## Requirements

- Minecraft **1.21.1**
- [Fabric Loader](https://fabricmc.net) ≥ 0.16.5
- [Fabric API](https://modrinth.com/mod/fabric-api) (0.105.x for 1.21.1)
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) ≥ 1.12.3+kotlin.2.0.0
- [Cobblemon](https://modrinth.com/mod/cobblemon) (1.6.x for 1.21.1)

---

## Building from source

```bash
# Clone
git clone https://github.com/LameGabriel/CobblemonShowdown.git
cd CobblemonShowdown

# Download the Gradle wrapper jar (only needed once)
gradle wrapper      # or use your system Gradle ≥ 8.8

# Build
./gradlew build
# Output: build/libs/cobblemon-showdown-<version>.jar
```

> **Note:** Cobblemon is available on their own Maven repository
> (`https://maven.cobblemon.com/releases`).  Fabric Loom will resolve it
> automatically during the build.

---

## How the queue works

```
Player A: /showdown queue join  → queued (size=1, pos=1)
Player B: /showdown queue join  → queued (size=2, pos=2) → match found!
  └─ BattleBuilder.pvp(A, B, GEN_9_SINGLES) is called
     ├─ Success  → both players receive "battle found" message and enter Cobblemon battle UI
     └─ Error    → both players are notified of the reason (e.g. no Pokémon in party)
```

The server also runs a maintenance pass every second to:
1. Remove any players who disconnected without leaving the queue
2. Match any two remaining players (handles edge cases like simultaneous joins)

---

## License

MIT

