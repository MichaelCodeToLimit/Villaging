# Villaging

A Fabric mod for Minecraft 26.2 that makes villagers, iron golems and copper golems smart. You are their king:
right-click a smart helper, chat in plain words, and an AI decides what it does. Helpers act through a fixed
set of safe tools (the things a player's hands can do), never by running code.

**Version 1.0.0.** A smart villager can talk, follow, walk, mine, place and use blocks, break
areas, carry and trade items, work chests, craft, smelt, brew, chop trees, farm, fish, shear, milk, breed and tame
animals, fight, guard, flee, sit and sleep. It can run whole plans for long jobs and ask you questions in a pop-up.
Building from blueprints, ranks and chains of command beyond relaying orders are the next sub-projects (see
`docs/superpowers/specs/`).

## Setup

1. Put the built jar (`build/libs/villaging-1.0.0.jar`) and Fabric API in your `mods` folder (Fabric Loader 0.20.1+).
2. Start the game and open a world. A box asks for your AI key (it shows dots, not the key). Pick Claude, Gemini or
   Ollama, paste the key, press Enter. Reopen it any time with `/villaging key`. On a multiplayer server only the
   owner or an admin can use it, and the key is stored on that server.
3. Or set the AI by hand in `config/villaging.json` (the box writes the same file):

   **Cloud (Claude).** Set `"provider": "cloud"` and put your key in `"apiKey"`. Never share this file.

   **Google Gemini.** Set `"provider": "gemini"` and put your Google AI key in `"apiKey"`. The default model is
   `gemini-3.5-flash` (change it with `"geminiModel"`). Free keys have low request limits, so a busy villager may
   answer "I can't think right now" until the limit resets.

   **Local (Ollama).** Install Ollama, `ollama pull` a model that supports tools, then set
   `"provider": "ollama"` and `"ollamaModel": "<name>"` (default URL `http://localhost:11434`). Villaging keeps
   the model warm for 30 minutes after a request, so follow-up orders answer without a cold start.

| Option | Default | Meaning |
|---|---|---|
| `provider` | `cloud` | `cloud`, `gemini` or `ollama` |
| `apiKey` | empty | key for `cloud` or `gemini` (never share this file) |
| `geminiModel` / `geminiUrl` | `gemini-3.5-flash` / `https://generativelanguage.googleapis.com` | Gemini model and base URL |
| `model` | `claude-sonnet-5-5` | cloud model |
| `cloudUrl` | `https://api.anthropic.com` | cloud base URL |
| `ollamaUrl` / `ollamaModel` | `http://localhost:11434` / `llama3.1:8b` | local model |
| `fallbackProvider` | empty (off) | backup AI used when the main one runs out of quota, is overloaded or unreachable: `ollama`, `gemini` or `cloud` (must differ from `provider`; a keyed backup uses the same `apiKey`). A failing main AI rests for a minute so messages are not delayed. Example: `"provider": "gemini"` with `"fallbackProvider": "ollama"` keeps your villagers working on a local model when Gemini's free allowance is used up. |
| `build` | see Building | `buildTicksPerBlock`, `buildAskThreshold`, `maxActiveBuilds`, `maxBlueprintBlocks`, `maxSavedBlueprints` |
| `maxSteps` | 20 | model calls per command |
| `callsPerMinute` | 10 | per-villager rate limit (protects your bill) |
| `actionRange` | 64 | how far an order may send a villager |
| `respectProtection` | `true` | villagers obey spawn protection, the world border and protection mods like you do; `false` skips those checks |
| `allowBreakBedrock` | `false` | `true` lets villagers break bedrock and other unbreakable blocks the way a creative-mode player can |
| `maxScriptSteps` | 500 | most steps one task script may take |
| `maxScriptSeconds` | 300 | longest one task script may run |
| `maxScriptDepth` | 3 | deepest nesting inside a task script |

If the config is missing a key, the mod still loads and tells you what to fix when you give an order.

## Playing

0. The first time you join a world, a **guide** explains everything. Reopen it with `/villaging guide`.
1. **Sneak + right-click an ordinary villager** and type. Press Enter to send, Escape to cancel. The first thing you
   say makes it smart (it gets a name), and if the world has no king yet you become king. After that a plain
   **right-click** on a smart villager or golem opens the chat; sneak + right-click gives vanilla trading (or lets you
   heal a golem with an iron ingot).
2. Or craft the **Crown of the King** (`GGG / GDG`, G = gold ingot, D = diamond): use it in the air to claim the
   throne (one king per world), and on a villager to make it smart.
3. **Golems:** tell a smart villager "recruit the iron golem" (or copper golem). The golem gets a name, obeys orders
   with the same abilities, and while it works its own behaviour (patrolling, sorting items) pauses.
4. **Rename:** "your name is Garrick", or "rename Bob to Odo". **Recruit:** "recruit two more villagers"; you are
   asked to approve, and up to 5 ordinary villagers within 32 blocks become smart (they are chosen instantly, not
   walked to). **Your title:** "call me Boss" (everyone) or "you call me Sire" (only that villager). Default: King.

Talk normally: slang, typos, other languages, "mine that block" (the block you are looking at), questions.
A villager stands still while you talk, and the chat window stays open so you can keep a full conversation. If the AI
service is slow or busy, the villager keeps trying and tells you it is working on it; it never just gives up.
The Crown is also in the creative inventory under Tools & Utilities.

### Commands

| Command | What it does |
|---|---|
| `/announcements <message>` | Speaks to every smart villager and golem within 64 blocks at once (up to 12, one every 1.5 seconds so the AI service is not flooded). Each answers in its own name. King only. |
| `/villaging summon <name>` | Brings one helper to your side and stops what it was doing. A start of its name is enough (`al` finds Alma); if two names match it lists them. Needs no AI. King only. |
| `/villaging key` | Opens the AI key box. |
| `/villaging guide` | Opens the first-time guide. |

(Minecraft has its own `/summon` for spawning mobs, so the helper command lives under `/villaging`.)

### What villagers can do

- **Look:** `look_around`, `look_at`, `check_inventory`.
- **World:** `mine_block`, `place_block`, `use_block` (doors, levers, beds...), `break_area` (up to 16 blocks a side).
- **Items:** `pick_up`, `drop`, `give_to_king`, `take_from_king`, `equip`, `use_item`, `eat`.
- **Containers:** `open_container`, `put_in`, `take_out`.
- **Making things:** `craft` (a crafting table within 16 blocks for big recipes), `smelt`, `brew`.
- **Gathering:** `chop_tree`, `till`, `plant`, `harvest`, `fish`, `shear`, `milk`, `breed`, `tame`.
- **Fighting:** `attack`, `shoot`, `guard`, `flee`. Villagers never hurt players or pets.
- **Moving:** `go_to`, `follow_me`, `stop`, `sit`, `sleep`.
- **Talking:** `say`, and `ask`, which pops up a dialog with options and a box for your own answer.
- **Long jobs:** `run_script` runs a whole plan of the same tools (loops, conditions) without asking the AI again at
  each step, and `stop_script` ends it. Telling the villager to stop also ends it.
- **Chain of command:** a villager marked as a commander can `relay_order` to another smart villager, but only after
  you approve it in the dialog.

Villagers have their own 36-slot inventory, saved with them. Nothing is conjured: they gather, craft and carry.

### Building from blueprints

Say what you want ("build a stone cottage by the river", "make a watchtower here, 10 high") and one helper designs it, checks
the materials, asks you, then builds it block by block from the bottom up.

| Tool | What it does |
|---|---|
| `use_template` | Saves a ready-made design: `cottage`, `tower`, `wall`, `bridge` or `castle`, with size, style (`stone`, `wood`, `brick`, `sandstone`, `dark`) and roof options. |
| `design_blueprint` | The AI draws its own plan from simple shapes (box, hollow box, floor, walls, gable/flat/pyramid roof, cylinder, line, door, window). Up to 48 wide and deep, 64 high, 60 shapes. |
| `list_blueprints`, `show_blueprint` | See the saved designs, or one layer of a design as text. |
| `plan_build` | Chooses where (and turned 0, 90, 180 or 270 degrees), checks the site, and counts the materials against what the helper carries and a chest. Starts nothing. |
| `start_build`, `stop_build`, `cancel_build`, `build_status` | Build, pause, end, or check progress. |

- **Materials come first from the helper and a chest** (name one, or the nearest chest or barrel within 8 blocks is used). If something
  runs out the helper stops, says exactly what it needs ("I need 12 more stone"), tells its AI so it can gather or craft it, and
  carries on when you say `start_build`.
- **Nothing is forced.** Occupied or protected spots are kept and reported; blocks are never broken to make room. Builds bigger than 64
  blocks, or on a site with occupied spots, ask you first. A build survives a restart (it waits for `start_build`).
- Blueprints are saved in the world, in `<world>/villaging/blueprints/`. Saving your own builds and importing structure files comes next.
- Settings (in `config/villaging.json`, under `"build"`): `buildTicksPerBlock` (6; higher is slower), `buildAskThreshold` (64),
  `maxActiveBuilds` (3 at once in the world), `maxBlueprintBlocks` (20000), `maxSavedBlueprints` (50).

### Questions and risky acts

- Lava, fire, TNT and other explosives, and breaking large areas always ask you first in a pop-up dialog. You can say
  "you don't need to ask about fire" to switch that off for one villager (you confirm it once), and "ask me again" to
  switch it back.
- A villager only takes items from your inventory when you tell it to, shows you exactly what it wants, and waits for
  your yes.

### Known limits
- **Not yet:** enchanting and anvils (villagers have no experience levels yet), saving your own builds as blueprints and
  importing structure files, and ranks (teams of helpers building together). These are separate releases.
- **Building:** one helper builds one structure at a time; the AI's own designs are simple shapes (the ready-made designs and
  your saved ones look best), and a small local AI model designs worse than Gemini or Claude.
- **Protection mods:** villagers act through a hidden fake player. Protection and claims mods can see it as a
  stranger and may refuse its actions even in your own claim. `respectProtection` only controls the checks this mod
  makes itself.
- **Trees:** a villager cannot climb, so it fells a tall tree only as high as it can reach.
- **Fishing** is simulated: about one catch every 20 seconds near water with a rod.

## Developing

```bash
./gradlew test             # unit tests (pure Java, no game)
./gradlew runClientGameTest   # launches the real game and runs the in-game checks
./gradlew build
```

Optional real-model checks before `runClientGameTest`: `VILLAGING_REAL_MODEL=<ollama model>` or
`VILLAGING_GEMINI_KEY=<key>` (the key is only read from the environment, never stored in the repo).
