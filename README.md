# Villaging

A Fabric mod for Minecraft 26.2 that makes villagers smart. You are their king: sneak and right-click a smart
villager, type anything into the box, and an AI decides what the villager does. Villagers act through a fixed
set of safe actions (the things a player's hands can do), never by running code.

**This is the Foundation release.** A villager can talk, follow you, walk to a spot and mine a block. Building,
crafting, combat, ranks and chains of command are the next sub-projects (see `docs/superpowers/specs/`).

## Setup

1. Put the built jar (`build/libs/villaging-1.0.0.jar`) and Fabric API in your `mods` folder (Fabric Loader 0.20.1+).
2. Start the game once. It creates `config/villaging.json`.
3. Choose the AI in that file:

   **Cloud (Claude).** Set `"provider": "cloud"` and put your key in `"apiKey"`. Never share this file.

   **Local (Ollama).** Install Ollama, `ollama pull` a model that supports tools, then set
   `"provider": "ollama"` and `"ollamaModel": "<name>"` (default URL `http://localhost:11434`).

| Option | Default | Meaning |
|---|---|---|
| `provider` | `cloud` | `cloud` or `ollama` |
| `apiKey` | empty | cloud key |
| `model` | `claude-sonnet-5-5` | cloud model |
| `cloudUrl` | `https://api.anthropic.com` | cloud base URL |
| `ollamaUrl` / `ollamaModel` | `http://localhost:11434` / `llama3.1:8b` | local model |
| `maxSteps` | 20 | model calls per command |
| `callsPerMinute` | 10 | per-villager rate limit (protects your bill) |
| `actionRange` | 64 | how far an order may send a villager |

If the config is missing a key, the mod still loads and tells you what to fix when you give an order.

## Playing

1. Craft the **Crown of the King**: `GGG / GDG` (G = gold ingot, D = diamond).
2. Use the crown in the air to claim the throne (one king per world).
3. Use the crown on a villager to make it smart; it gets a name.
4. **Sneak + right-click** the villager and type. Press Enter to send, Escape to cancel.

Talk normally: slang, typos, other languages, "mine that block" (the block you are looking at), questions.
If it cannot do something yet it says so and offers the closest thing.

## Developing

```bash
./gradlew test             # unit tests (pure Java, no game)
./gradlew runClientGameTest   # launches the real game and runs the in-game checks
./gradlew build
```

Optional real-model check: set `VILLAGING_REAL_MODEL=<ollama model>` before `runClientGameTest`.

Not yet supported: protected-area mods (the mod only refuses to break bedrock and chests that hold items).
