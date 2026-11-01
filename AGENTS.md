# Villaging: handoff notes for the next coding agent

Fabric mod for Minecraft 26.2 (Java 25, Fabric Loader 0.20.1, Fabric API 0.161.0+26.2, Loom, Mojang names).
Villagers, iron golems and copper golems become smart AI helpers the player commands in plain words. The player is the king.
Repository: https://github.com/MichaelCodeToLimit/Villaging (public, MIT, branch `main`). Owner's Modrinth user: md430.

## Build and test
- `./gradlew build --offline -q` builds the jar (`build/libs/villaging-<version>.jar`) and runs the unit tests. No output means success.
- `./gradlew runClientGameTest --offline` launches the real game and runs about 68 in-game checks (5 to 10 minutes). Look for
  `[villaging-gametest] PASS` / `FAIL` lines in the output.
- `VILLAGING_ONLY="<text>" ./gradlew runClientGameTest --offline` runs only the checks whose name contains `<text>`.
- Do not run anything else heavy at the same time (another Gradle build, a large Ollama model, a real Minecraft). Running out of memory
  killed the Gradle daemon once, and an overloaded machine made two checks time out (`Can't keep up` warnings in the log).
- Screenshots from the game tests land in `build/run/clientGameTest/screenshots/` (`gradlew clean` removes them).
- Current version: see `gradle.properties` (`version=`). 1.0.0 to 1.0.3 are released builds; 1.0.4 (blueprints) and 1.0.5 (teams) are merged to main and numbered to follow the Modrinth count (1.0.6 to 1.0.9 next, then 1.2.0, 1.2.1 and so on).
  Build details: blueprint code is in `com.villaging.blueprint` (pure) and `com.villaging.build` (engine, job, build action); `.gitignore` ignores only the
  top-level `/build/` folder, so those packages are tracked.

## Code layout (`src/main/java/com/villaging/`)
- `ai/`: pure Java, no Minecraft imports. `AgentLoop` (model call + tool loop, retries, "thinking" message after 3 s, quota/key error messages),
  providers (`GeminiProvider`, `CloudProvider` = Claude, `OllamaProvider`), `SystemPrompt`, tool definitions in `ai/tools/`.
- `command/`: `CommandService` (pure rules), `HelperRoster` (pure name matching), `VillagingRuntime` (game glue, one per server),
  `HelperCommands` (`/announcements`, `/villaging summon`), `KeySettings`.
- `villager/`: the Minecraft layer: actions, brains (`VillagerBrain` persisted per mob), `VillagerSpeech`, `TalkHold`, scripts.
- `king/`: who is king, the crown item, `ClickRule`. `guide/GuideContent` is the in-game guide (every page at most 650 characters).
- `mixin/`: pauses vanilla AI while a helper is controlled.
- Client code: `src/client/java/com/villaging/client/` (chat screen with select/copy, key box, guide). In-game tests: `src/gametest/`.
- Docs: `README.md`, `docs/modrinth/` (listing text, gallery images), `docs/wiki/` (wiki pages), `docs/community/DISCORD.md`.

## Rules the owner set (keep following them)
- Every player uses their own AI key. Never store, print or echo a real API key (not in code, logs, tests, commits or chat). When showing
  `config/villaging.json`, hide the `apiKey` value.
- Helpers must never answer "I can't do that right now". Give a real reason only when the AI service itself fails (quota, bad key).
- Modrinth publishing, Discord and GitHub account actions need the owner's own accounts. Do not enter their credentials. Ask before
  publishing anything new.
- Commits so far end with a `Co-Authored-By` trailer for the assistant that wrote them.

## Gotchas learned the hard way
- Windows + Git Bash: `sed -i` with `a\` and `\t` mangles lines, and very long heredocs with quotes can fail. Use the editor tool for
  multi-line edits. The files use TAB indentation (and LF, with Git warning about CRLF).
- Minecraft 26.2 APIs differ from older versions: mouse handlers are `mouseClicked(MouseButtonEvent, boolean)` etc.; `KeyEvent.isCopy()`;
  `Font.getSplitter()`. Use `javap` on `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-{common,clientonly}-deobf/26.2/*.jar`
  (classpath separator `;` on Windows) to check signatures.
- The game-test input does not deliver the Ctrl modifier, so copy/paste checks send `KeyEvent`s straight to the screen.
- Helpers' spoken lines are saved to memory when spoken (`VillagerSpeech.say`), not when the AI run ends.
- Minecraft has its own `/summon`, so the helper command is `/villaging summon <name>`.
- `/announcements` asks helpers one every 1.5 s (max 12) because each needs a model call; Gemini's free tier ran out of quota otherwise.

## State of external things (as of 2026-10-29)
- Modrinth project `villaging` exists (private/unreviewed). Done: description, license MIT, tags, disclosures (AI-generated code/assets/text and generative
  AI functionality both on), version 1.0.0 uploaded. To do by the owner: upload 1.0.2 (changelog in `docs/modrinth/LISTING.md`), gallery images
  from `docs/modrinth/gallery/`, Links page (GitHub source/issues/wiki, Discord `https://discord.gg/V8zF3tuWW`), then Submit for review.
  Play-test once with a working AI first.
- GitHub wiki: pages are written in `docs/wiki/` but not pushed. The owner must first create any page on the repo's Wiki tab, then push
  `docs/wiki/*.md` to `https://github.com/MichaelCodeToLimit/Villaging.wiki.git`.
- The owner's Gemini free quota was used up (HTTP 429). Their settings file now uses Ollama with `llama3.1:8b` (3 to 5 s per answer when warm,
  about 26 s cold). `gemma4:12b` crashed Ollama; do not use it. `kimi-k2.6:cloud` was not tested.

## Planned next work
- A Paper server plugin (Fabric client mod still required by players). Design was agreed in chat but no spec or code exists yet:
  Gradle multi-project `core` (pure Java: `ai`, `config`, `guide`, with their unit tests) + `fabric` (today's mod) + `paper` (new plugin using NMS,
  Paper 26.2). Same plugin channels and packets so the existing client works unchanged. Keys: per-player, plus an optional admin-set server key
  (`keyMode: player | server | both`, default both). Order: refactor with all checks green, then Paper skeleton (commands, keys, networking), then
  actions, kings, golems, crown. Write the spec to `docs/superpowers/specs/` and get the owner's approval before coding.
- Blueprints: release 1.0.4 (engine, five templates, nine tools, build settings) is merged to main from plan
  `docs/superpowers/plans/2026-10-31-villaging-blueprints-engine.md` and spec `docs/superpowers/specs/2026-10-31-villaging-blueprints-design.md`.
  Still to do from the spec, each as its own plan: release 1.2.0 (`capture_area`, `.nbt` import/export, `/villaging blueprint` commands, the
  "clear the site first" answer) and 1.3.0 (preview outline, `stairs_run`, `fill_palette_pattern`, `gatehouse`, polish); with teams taking 1.2.0 these move one release later. Teams (1.0.5): plan `docs/superpowers/plans/2026-10-31-villaging-teams.md`, spec `docs/superpowers/specs/2026-10-31-villaging-teams-design.md`.
- Known smaller items (1.0.3 added Ollama `keep_alive: 30m` and the optional `fallbackProvider` backup AI):
  weapons never wear out; Infinity bow false failure; bed use only lies the fake player down.
