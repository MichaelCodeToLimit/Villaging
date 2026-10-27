# Villaging Abilities (sub-project 2) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A smart villager can do with its hands what a player can, via a fixed set of safe AI tools, a hidden fake player, its own saved inventory, a pop-up question dialog, and safe task scripts.

**Architecture:** Pure-Java layer (`com.villaging.ai`, `com.villaging.ai.script`, risk policy, pending questions) holds tool schemas, validators, scripts and risk rules and is unit-tested with fakes. The Minecraft layer (`com.villaging.villager`, `.net`, client) adds `VillagerHands` (a hidden `ServerPlayer`), `ToolHandler`s that run on the server thread and `VillagerAction`s for multi-tick work. Every new tool is a registry entry (`Tools.register`) plus a handler (`ToolHandlers.register`); the AI never runs code.

**Tech Stack:** Fabric Loader 0.20.1, Fabric API 0.161.0+26.2, Loom 1.19-SNAPSHOT, Java 25, Minecraft 26.2 (Mojang names), JUnit 5.11.4, Fabric client game tests.

**Spec:** `docs/superpowers/specs/2026-10-27-villaging-abilities-design.md` (builds on `2026-10-26-villaging-foundation-design.md`).

## Global Constraints

- Fabric on Minecraft 26.2, Mojang names; `Identifier` replaces `ResourceLocation`; Java 25.
- The AI only calls registered tools; nothing it sends is ever executed as code. Every call is validated (`Tools.validate`) before it runs and again on the server thread.
- Tool results are `ok...` or `failed: <reason>`; failures never throw into the model.
- Villagers never hurt players or pets (`Player`, any tamed `TamableAnimal`, any entity with an owner), regardless of settings or orders.
- `respectProtection` (default `true`) governs spawn protection, protection mods, world border and `actionRange` for villager actions. `allowBreakBedrock` (default `false`) lets villagers break bedrock and other unbreakable blocks as a creative player can; independent of `respectProtection`.
- Risky acts (lava, fire, TNT, mass destruction) always ask the king first unless that villager's saved preference turns the category off. Questions use the pop-up dialog: a few options plus a box for the king's own answer.
- `take_from_king` takes only items the king names and always confirms with the king through the dialog; chests follow the protection setting.
- A commander's relayed order runs only after the king approves it through the dialog.
- The fake player is never visible to clients, never saved, never in the player list, and is discarded when the villager unloads or dies.
- Foundation tests (113 unit, 17 in-game) stay green after every task.
- Commit trailer: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus

- A forged or repeated `AnswerPayload` (wrong player, wrong question id, answered twice, answered after cancel) must be rejected and must never run the risky act.
- A villager that dies, unloads or is removed while a question or script is pending must leave no leaked fake player, pending question or running script.
- A model that calls `set_ask_preference` or `take_from_king` on its own must still be forced through the dialog.
- A task script with a failing step, over-long loop, excessive nesting or a risky step must stop with a clear reason and never run unvalidated steps.
- Pets and tamed animals must be refused as `attack`, `shoot` and `guard`-engage targets even when named by the king; players likewise.
- `respectProtection=false` and `allowBreakBedrock=true` must change only what the spec says (checks skipped; bedrock/barrier breakable); `true`/`false` defaults keep Foundation behaviour.

---

## File Structure

Pure Java (unit-tested):
- `config/VillagingConfig.java` (modify): new settings.
- `ai/ToolSchema.java` (create): shared schema/validator helpers (string, int, number, enum, array props, `requiredInt`, `requiredString`).
- `ai/tools/*Tools.java` (create): `LookTools`, `WorldTools`, `ItemTools`, `ContainerTools`, `CraftTools`, `GatherTools`, `CombatTools`, `MoveTools`, `ScriptTools`, `AskTools`, `CommandTools`; each has `static void register()`; `AbilityTools.registerAll()`.
- `ai/RiskPolicy.java` (create): classifies a tool call into a risk category.
- `ai/PendingQuestions.java` (create): one-shot question registry.
- `ai/script/` (create): `Script`, `ScriptParser`, `ScriptRunner`, `Condition`.

Minecraft layer:
- `villager/ToolContext.java`, `ToolHandler.java`, `ToolHandlers.java` (create); `MinecraftActionExecutor.java` (modify to dispatch through them).
- `villager/VillagerInventory.java`, `VillagerInventories.java` (create): 36-slot saved inventory attachment.
- `villager/VillagerBrain.java` (modify): `askOff` set and `commander` flag.
- `villager/VillagerHands.java`, `HandsManager.java` (create): hidden fake player.
- `villager/Protection.java` (create): single place for protection/bedrock/pet/player rules; `MineAction` uses it.
- `villager/actions/*Action.java` (create): multi-tick actions.
- `net/AskPayload.java`, `net/AnswerPayload.java` (create); `client/AskScreen.java` (create); `Villaging.java`, `VillagingClient.java` (modify).
- `ai/SystemPrompt.java` (modify); `README.md` (modify).

Tests: `src/test/java/com/villaging/...` mirrors packages; in-game checks extend `src/gametest/java/com/villaging/gametest/VillagingClientGameTest.java` following its existing fake-model pattern (`FakeOllama` scripted replies).

Verification commands: `./gradlew test --tests <Class>` (unit), `./gradlew test` (suite), `./gradlew runClientGameTest` (in game).

---

### Task 1: Settings and brain fields

**Files:**
- Modify: `src/main/java/com/villaging/config/VillagingConfig.java`, `src/main/java/com/villaging/villager/VillagerBrain.java`
- Test: `src/test/java/com/villaging/config/VillagingConfigTest.java`, `src/test/java/com/villaging/villager/VillagerBrainTest.java`

**Interfaces:**
- Produces: `VillagingConfig` gains `boolean respectProtection` (default true), `boolean allowBreakBedrock` (false), `int maxScriptSteps` (500), `int maxScriptSeconds` (300), `int maxScriptDepth` (3) appended to the record in that order after `actionRange`. `VillagerBrain` gains `Set<String> askOff` (default empty) and `boolean commander` (default false), with `withAskOff(String category, boolean off)` and `withCommander(boolean)`; codec fields `askOff`, `commander` optional.

- [ ] **Step 1: Write failing tests.** Config: defaults are `true/false/500/300/3`; a file with `"respectProtection": false, "allowBreakBedrock": true` loads those; non-positive script limits fall back to defaults; missing keys keep defaults. Brain: `withAskOff("fire", true).askOff().contains("fire")`; `withAskOff("fire", false)` removes it; codec round-trips `askOff` and `commander`; old data without the fields decodes to empty/false.
- [ ] **Step 2: Run** `./gradlew test --tests VillagingConfigTest --tests VillagerBrainTest`. Expected: FAIL (compile errors / missing members).
- [ ] **Step 3: Implement** the record fields, `defaults()`, `load()` (use `Boolean` in `Raw` so absence keeps defaults) and the brain fields; update every constructor call (`Villaging`, tests, `VillagingRuntime`).
- [ ] **Step 4: Run** `./gradlew test`. Expected: all pass.
- [ ] **Step 5: Commit** `feat: abilities settings and brain fields`.

### Task 2: Tool plumbing (ToolSchema, ToolHandler dispatch)

**Files:**
- Create: `ai/ToolSchema.java`, `villager/ToolContext.java`, `villager/ToolHandler.java`, `villager/ToolHandlers.java`
- Modify: `ai/Tools.java` (use `ToolSchema`; behavior unchanged), `villager/MinecraftActionExecutor.java`
- Test: `src/test/java/com/villaging/ai/ToolSchemaTest.java`, existing `ToolsTest`

**Interfaces:**
- Produces:
  - `ToolSchema.object(Prop... props)` builder with `.required(String...)`, `.build() -> JsonObject`; `ToolSchema.prop(name, type, description)`, `enumProp(name, description, String... values)`, `arrayOfStrings(name, description)`.
  - Argument checkers returning `Optional<String>`: `ToolSchema.intIn(args, key, min, max)`, `stringNonBlank(args, key, maxLen)`, `oneOf(args, key, Set<String>)`, `coordinates(args, whole, range, ox, oy, oz)` (the existing logic).
  - `ToolContext(MinecraftServer server, Villager villager, UUID kingId, VillagingConfig config)`.
  - `interface ToolHandler { CompletableFuture<String> run(ToolContext ctx, JsonObject args); }`
  - `ToolHandlers.register(String name, ToolHandler)`, `ToolHandlers.get(String) -> Optional<ToolHandler>`.

- [ ] **Step 1: Write failing tests** `ToolSchemaTest`: `intIn` rejects missing, non-number, fractional and out-of-range values with messages naming the key; `oneOf` rejects unknown values listing allowed ones; `stringNonBlank` rejects blank and over-length; `coordinates` rejects NaN/too-far (moved cases). Keep existing `ToolsTest` unchanged and green.
- [ ] **Step 2: Run** `./gradlew test --tests ToolSchemaTest`. Expected: FAIL (class missing).
- [ ] **Step 3: Implement** `ToolSchema`; refactor `Tools` to call it (no behavior change). Move the five existing tools' execution from the `switch` in `MinecraftActionExecutor` into handlers registered in a new `ToolHandlers.registerBase()`; `execute` looks the handler up (`failed: unknown tool` otherwise). `say`/`stop`/`follow_me`/`go_to`/`mine_block` behave identically.
- [ ] **Step 4: Run** `./gradlew test` then `./gradlew runClientGameTest`. Expected: all unit tests pass and the 17 in-game checks still pass (proves the refactor).
- [ ] **Step 5: Commit** `refactor: tool schema helpers and handler dispatch`.

### Task 3: Villager inventory (36 slots, saved)

**Files:**
- Create: `villager/VillagerInventory.java`, `villager/VillagerInventories.java`
- Modify: `villager/VillagerState.java` (describe the new inventory), `Villaging.java` (init)
- Test: `src/test/java/com/villaging/villager/VillagerInventoryTest.java`; in-game check

**Interfaces:**
- Produces: `VillagerInventory extends SimpleContainer` (size 36) with `static final Codec<VillagerInventory> CODEC` (list of `ItemStack` with slot) and `int count(Item)`, `ItemStack addItem` (inherited), `boolean remove(Item, int)`; `VillagerInventories.get(Villager) -> VillagerInventory` and `VillagerInventories.set(Villager, VillagerInventory)`, a persistent data attachment like `VillagerBrains`. Armor and hand slots stay the villager's vanilla equipment slots.

- [ ] **Step 1: Write failing tests** `count`/`remove` arithmetic (remove more than held returns false and changes nothing; splits across slots); codec round-trip keeps stacks and counts (only if codec is testable without a registry; otherwise cover in game).
- [ ] **Step 2: Run** `./gradlew test --tests VillagerInventoryTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the container and attachment (`AttachmentRegistry.createPersistent`, as `VillagerBrains` does). `VillagerState.describe` lists this inventory.
- [ ] **Step 4: In-game check** (extend the client game test): give a villager items, save and reload the world chunk (or kill and respawn from NBT using the test's existing helper), assert the stacks persist.
- [ ] **Step 5: Run** `./gradlew test` and `./gradlew runClientGameTest`. Expected: pass.
- [ ] **Step 6: Commit** `feat: saved 36-slot villager inventory`.

### Task 4: VillagerHands (hidden fake player)

**Files:**
- Create: `villager/VillagerHands.java`, `villager/HandsManager.java`
- Modify: `Villaging.java` (clear on server stop, discard on villager unload/death via `ServerEntityEvents.ENTITY_UNLOAD` and a death hook), `villager/ToolContext.java` (add `VillagerHands hands()` lazily created)
- Test: in-game checks

**Interfaces:**
- Produces: `VillagerHands extends ServerPlayer` (fake: no connection, never added to the player list or tracked by clients, game profile derived from the villager UUID), with `void syncFrom(Villager)` (copy position, rotation, hand items, and use the villager's `VillagerInventory`) and `void syncTo(Villager)` (copy back changed inventory). `HandsManager.get(Villager) -> VillagerHands`, `release(Villager)`, `clear()`.

- [ ] **Step 1: Write the in-game checks first** (they are the tests for this task): (a) after the villager acts, `server.getPlayerList().getPlayers()` does not contain the fake player and no client has an entity with its id (assert via the client world); (b) unloading or killing the villager releases the hands (`HandsManager` size back to 0); (c) hands' main-hand item equals the villager's main-hand item after `syncFrom`.
- [ ] **Step 2: Run** `./gradlew runClientGameTest`. Expected: FAIL (class missing).
- [ ] **Step 3: Implement** the fake player using the same technique as Fabric's existing fake-player patterns for the 26.x API (a `ServerPlayer` subclass with a stub packet listener); override anything that would send packets to do nothing. Keep creation lazy and cheap.
- [ ] **Step 4: Run** `./gradlew runClientGameTest`. Expected: PASS.
- [ ] **Step 5: Commit** `feat: hidden villager fake player`.

### Task 5: Protection service (settings applied everywhere)

**Files:**
- Create: `villager/Protection.java`
- Modify: `villager/MineAction.java` (use `Protection`; hardness<0 check respects `allowBreakBedrock`)
- Test: `src/test/java/com/villaging/villager/ProtectionRulesTest.java` (pure decision part), in-game checks

**Interfaces:**
- Produces:
  - Pure: `ProtectionRules.canBreakUnbreakable(boolean allowBreakBedrock)`, `ProtectionRules.isForbiddenTarget(boolean isPlayer, boolean isTamedOrOwned) -> boolean` (true means never attack).
  - MC: `Protection.blockProblem(ServerLevel, Villager, BlockPos, BlockState, boolean respectProtection) -> String|null` (null = allowed; same messages `MineAction` used: `that area is protected`, `something is protecting that block`), `Protection.entityProblem(Entity target) -> String|null` ("I won't hurt a player/pet"), both using the king as the acting player when `respectProtection` is true.

- [ ] **Step 1: Write failing tests**: `canBreakUnbreakable(false)` is false, `(true)` true; `isForbiddenTarget` true for player, true for owned/tamed, false for a plain zombie.
- [ ] **Step 2: Run** `./gradlew test --tests ProtectionRulesTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the rules and `Protection`; with `respectProtection=false` skip the mayBuild/mayInteract/border/event checks; with `allowBreakBedrock=true` allow `hardness < 0` (barrier, bedrock) in `MineAction`, which must also skip the `MAX_HARDNESS` refusal for those. Pass `VillagingConfig` into `MineAction` through the handler context.
- [ ] **Step 4: In-game checks**: bedrock refused by default and mined (as an instant/creative-style break) when `allowBreakBedrock=true`; protected area refused when `respectProtection=true` and allowed when `false` (use the existing veto event helper); existing protection checks from the Foundation still pass.
- [ ] **Step 5: Run** `./gradlew test` and `./gradlew runClientGameTest`. Expected: pass.
- [ ] **Step 6: Commit** `feat: protection service with bedrock and protection settings`.

### Task 6: Risk policy and per-villager ask preferences

**Files:**
- Create: `ai/RiskPolicy.java`, `ai/tools/AskTools.java` (tool `set_ask_preference`)
- Test: `src/test/java/com/villaging/ai/RiskPolicyTest.java`

**Interfaces:**
- Produces: `enum RiskCategory { LAVA, FIRE, EXPLOSIVE, MASS_DESTRUCTION }`; `RiskPolicy.classify(String toolName, JsonObject args, String heldItemId) -> Optional<RiskCategory>`: `place_block`/`use_item` with `minecraft:lava_bucket` -> LAVA; `flint_and_steel`, `fire_charge` -> FIRE; `tnt`, `tnt_minecart`, `end_crystal`, `respawn_anchor` -> EXPLOSIVE; `break_area` whose box volume exceeds `RiskPolicy.MASS_VOLUME = 64` -> MASS_DESTRUCTION; everything else empty. `RiskPolicy.mustAsk(Optional<RiskCategory>, Set<String> askOff)` -> true unless the category's lowercase name is in `askOff`. Tool `set_ask_preference(category, ask: boolean)`.

- [ ] **Step 1: Write failing tests**: each category triggers on its item/volume (volume 64 does not, 65 does); unrelated items give empty; `mustAsk` is true by default, false when the category name is in `askOff`, and true for an unrelated category.
- [ ] **Step 2: Run** `./gradlew test --tests RiskPolicyTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the policy and register `set_ask_preference` (validator: `category` must be one of the four lowercase names, `ask` boolean). Its handler (Task 7) always confirms through the dialog when turning asking off.
- [ ] **Step 4: Run** `./gradlew test`. Expected: pass.
- [ ] **Step 5: Commit** `feat: risk policy`.

### Task 7: Questions: ask tool, dialog and one-shot answers

**Files:**
- Create: `ai/PendingQuestions.java`, `ai/tools/AskTools.java` (add `ask`), `net/AskPayload.java`, `net/AnswerPayload.java`, `client/AskScreen.java`, `villager/AskService.java`
- Modify: `Villaging.java` (register payloads, answer receiver), `client/VillagingClient.java` (open the screen)
- Test: `src/test/java/com/villaging/ai/PendingQuestionsTest.java`, `ToolsTest` additions, in-game checks

**Interfaces:**
- Produces:
  - `PendingQuestions.open(UUID kingId, int villagerId, String question, List<String> options, long nowNanos) -> Question(id long, ...)` returning a `CompletableFuture<String>` via `Question.future()`; `answer(UUID player, long questionId, String text, long nowNanos) -> boolean` (true only the first time, only from the asked king, only before expiry); `cancel(long id)`; `expire(long nowNanos)`; `cancelFor(int villagerId)`. Expiry is `PendingQuestions.TIMEOUT_NANOS = 120s`; expired and cancelled questions complete the future with `"no answer"`.
  - Tool `ask(question: string, options: string[0..4])`: question 1..300 chars, each option 1..60 chars.
  - `AskPayload(long questionId, int villagerId, String villagerName, String question, List<String> options)` S2C; `AnswerPayload(long questionId, String answer)` C2S (answer 1..300 chars; empty means cancel).
  - `AskService.ask(ToolContext, String question, List<String> options) -> CompletableFuture<String>`; also `AskService.confirm(ctx, question) -> CompletableFuture<Boolean>` offering "Yes"/"No".
  - `AskScreen(AskPayload)`: option buttons, an `EditBox` for a custom answer, Enter sends, Escape cancels.

- [ ] **Step 1: Write failing tests** `PendingQuestionsTest`: first correct answer completes the future with the text and returns true; a second answer returns false and changes nothing; an answer from a different player returns false and does not complete it; an unknown id returns false; an answer after `TIMEOUT_NANOS` returns false and the future already completed with `"no answer"`; `cancelFor(villagerId)` completes all of that villager's questions with `"no answer"`. `ToolsTest`: `ask` rejects blank question, 5 options, over-long option.
- [ ] **Step 2: Run** `./gradlew test --tests PendingQuestionsTest --tests ToolsTest`. Expected: FAIL.
- [ ] **Step 3: Implement** `PendingQuestions` (synchronized, injected clock), the tool, payloads and screen. The `ask` handler uses `AskService`; the server tick expires questions; `ActionManager`/executor `cancel` and villager unload call `cancelFor`. The answer receiver calls `PendingQuestions.answer(player.getUUID(), ...)` only.
- [ ] **Step 4: In-game checks**: a fake-model reply with an `ask` call opens the screen (screenshot), clicking an option returns that option to the model; typing a custom answer returns the text; Escape returns `no answer`; a forged `AnswerPayload` from a second client/player or with a stale id does nothing.
- [ ] **Step 5: Run** `./gradlew test` and `./gradlew runClientGameTest`. Expected: pass.
- [ ] **Step 6: Commit** `feat: ask tool and question dialog`.

### Task 8: Risk gate in the executor

**Files:**
- Modify: `villager/MinecraftActionExecutor.java`, `villager/ToolHandlers.java`
- Test: in-game checks (policy logic already covered by Task 6)

**Interfaces:**
- Consumes: `RiskPolicy`, `AskService.confirm`, `VillagerBrain.askOff`, `Tools` handlers.
- Produces: in `execute`, before running any handler: classify; if `mustAsk`, call `AskService.confirm` with a message naming the villager, the action and where; run the handler only on yes; otherwise return `failed: the king said no` (or `failed: no answer`). `set_ask_preference` handler: turning asking OFF always confirms; turning ON applies at once and saves to the brain via `VillagerBrains.set`.

- [ ] **Step 1: Write the in-game checks first**: with a scripted model calling `place_block` of TNT, the dialog opens; "No" returns `failed: the king said no` and no TNT is placed; "Yes" places it; after `set_ask_preference(explosive, false)` is confirmed, a later TNT placement does not ask; `set_ask_preference(..., true)` restores asking.
- [ ] **Step 2: Run** `./gradlew runClientGameTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the gate (tools it depends on arrive in Task 10; until then register a temporary test-only handler for a risky tool name inside the game test source set, not in `src/main`).
- [ ] **Step 4: Run** `./gradlew test` and `./gradlew runClientGameTest`. Expected: pass.
- [ ] **Step 5: Commit** `feat: risky acts ask the king first`.

### Task 9: Look and inventory tools

**Files:**
- Create: `ai/tools/LookTools.java`, `ai/tools/AbilityTools.java`, `villager/LookHandlers.java`
- Modify: `Villaging.java` (`AbilityTools.registerAll()` before the runtime starts)
- Test: `src/test/java/com/villaging/ai/tools/LookToolsTest.java`, in-game check

**Interfaces:**
- Produces tools: `look_around(radius: integer 1..16, default 8)` returning a text summary of notable blocks (counts by type, excluding air/stone/dirt noise but including ores, wood, containers, stations, crops, water/lava), entities (type, count, nearest distance, flag `tamed`/`player`), dropped items, and containers with coordinates; `look_at(x,y,z)` returns the block and any block entity at a position within `actionRange`; `check_inventory()` returns item counts. `AbilityTools.registerAll()` calls every `*Tools.register()` once (idempotent).

- [ ] **Step 1: Write failing tests**: validators reject radius 0, 17, fractional; accept 1 and 16; `look_at` rejects far coordinates; `registerAll()` called twice leaves one entry per tool name (no duplicates in `Tools.all()`).
- [ ] **Step 2: Run** `./gradlew test --tests LookToolsTest`. Expected: FAIL.
- [ ] **Step 3: Implement** validators and handlers (read-only, bounded work: iterate at most `(2r+1)^3` positions, `r<=16`, cap output at 1500 characters).
- [ ] **Step 4: In-game check**: place a chest and an iron ore beside the villager; the scripted model calls `look_around`; the result mentions both.
- [ ] **Step 5: Run** `./gradlew test` and `./gradlew runClientGameTest`. Expected: pass.
- [ ] **Step 6: Commit** `feat: look_around, look_at, check_inventory`.

### Task 10: World tools (place, use, break area)

**Files:**
- Create: `ai/tools/WorldTools.java`, `villager/actions/PlaceBlockAction.java`, `UseBlockAction.java`, `BreakAreaAction.java`
- Test: `src/test/java/com/villaging/ai/tools/WorldToolsTest.java`, in-game checks

**Interfaces:**
- Produces tools: `place_block(item: string, x,y,z: integer)` (item id such as `minecraft:oak_planks`), `use_block(x,y,z)`, `break_area(x1,y1,z1,x2,y2,z2)` with each box side at most 16 and every corner within `actionRange`. Handlers run through `VillagerHands` (`gameMode.useItemOn` for place/use; reuse `MineAction`'s rules per block for break). All three go through `Protection.blockProblem`. Placing consumes the item from the villager inventory (fails with `failed: I don't have <item>`).

- [ ] **Step 1: Write failing tests**: validators reject non-namespaced item strings, fractional coordinates, boxes over 16 on any side, far corners; accept valid calls.
- [ ] **Step 2: Run** `./gradlew test --tests WorldToolsTest`. Expected: FAIL.
- [ ] **Step 3: Implement** validators then actions. `break_area` breaks block by block with the existing swing/progress animation, skips air, applies `Protection` per block, stops at the first protection failure, and reports how many it broke.
- [ ] **Step 4: In-game checks**: place a block from inventory (item count drops by one); placing in a protected spot fails with `respectProtection=true`; `use_block` toggles a lever/opens a door; `break_area` over a 3x3x3 of dirt clears it and a 17-wide box is rejected by validation.
- [ ] **Step 5: Run** `./gradlew test` and `./gradlew runClientGameTest`. Expected: pass.
- [ ] **Step 6: Commit** `feat: place_block, use_block, break_area`.

### Task 11: Item tools

**Files:**
- Create: `ai/tools/ItemTools.java`, `villager/actions/PickUpAction.java`
- Test: `src/test/java/com/villaging/ai/tools/ItemToolsTest.java`, in-game checks

**Interfaces:**
- Produces tools: `pick_up(item?: string)` (walk to and collect nearby item entities within 12 blocks, optionally filtered), `drop(item, count)`, `give_to_king(item, count)` (walks to the king and hands the items over), `take_from_king(item, count)` (always confirms through `AskService.confirm`, then moves exactly that many of exactly that item from the king's inventory), `equip(item)` (armor/tool/hand slot), `use_item(item)`, `eat(item?)`. `count` is 1..2304.

- [ ] **Step 1: Write failing tests**: validators reject count 0 / 2305 / fractional, blank item; `take_from_king` and `give_to_king` require `item` and `count`.
- [ ] **Step 2: Run** `./gradlew test --tests ItemToolsTest`. Expected: FAIL.
- [ ] **Step 3: Implement** validators and handlers. `use_item` and `eat` run through the hands; risk classification applies to `use_item`.
- [ ] **Step 4: In-game checks**: dropped item is picked up; `give_to_king` puts the exact stack in the king's inventory; `take_from_king` opens the dialog and, on "Yes", moves exactly the named amount, on "No" moves nothing; `equip` puts a sword in the main hand; `eat` raises food or removes the item.
- [ ] **Step 5: Run** `./gradlew test` and `./gradlew runClientGameTest`. Expected: pass.
- [ ] **Step 6: Commit** `feat: item tools`.

### Task 12: Container tools

**Files:**
- Create: `ai/tools/ContainerTools.java`, `villager/actions/ContainerAction.java`
- Test: `src/test/java/com/villaging/ai/tools/ContainerToolsTest.java`, in-game checks

**Interfaces:**
- Produces tools: `open_container(x,y,z)` (returns contents), `put_in(x,y,z,item,count)`, `take_out(x,y,z,item,count)`. Opening goes through the hands (`useItemOn` / menu) and `Protection.blockProblem`; counts 1..2304.

- [ ] **Step 1: Write failing tests**: validators reject missing coordinates, bad counts, blank item.
- [ ] **Step 2: Run** `./gradlew test --tests ContainerToolsTest`. Expected: FAIL.
- [ ] **Step 3: Implement** and close the menu when finished or cancelled.
- [ ] **Step 4: In-game checks**: `put_in` moves items villager -> chest; `take_out` moves chest -> villager; taking from a chest in a protected area fails with `respectProtection=true`.
- [ ] **Step 5: Run** both suites. Expected: pass.
- [ ] **Step 6: Commit** `feat: container tools`.

### Task 13: Crafting and processing

**Files:**
- Create: `ai/tools/CraftTools.java`, `villager/actions/CraftAction.java`, `StationAction.java`
- Test: `src/test/java/com/villaging/ai/tools/CraftToolsTest.java`, in-game checks

**Interfaces:**
- Produces tools: `craft(item, count)` (uses a recipe from the recipe manager the villager can satisfy; needs a nearby crafting table if the recipe is 3x3), `smelt(item, count)`, `brew(item)`, `enchant(item, level)`, `anvil(item, rename?)`, each walking to a station within 16 blocks and failing with `failed: no <station> nearby` otherwise. Counts 1..64. Fuel and ingredients are taken from the villager inventory.

- [ ] **Step 1: Write failing tests**: validators reject counts outside 1..64, blank items, enchant level outside 1..5.
- [ ] **Step 2: Run** `./gradlew test --tests CraftToolsTest`. Expected: FAIL.
- [ ] **Step 3: Implement** `craft` against the recipe manager (2x2 anywhere, 3x3 near a crafting table); `smelt` places ingredient and fuel into a furnace via the hands and collects output when done; `brew`, `enchant`, `anvil` use the matching menus. Each reports exactly what it produced.
- [ ] **Step 4: In-game checks**: craft sticks from planks with no table; craft a pickaxe near a table; `smelt` turns raw iron into ingots with coal; missing-station failure message appears.
- [ ] **Step 5: Run** both suites. Expected: pass.
- [ ] **Step 6: Commit** `feat: craft and station tools`.

### Task 14: Gathering tools

**Files:**
- Create: `ai/tools/GatherTools.java`, `villager/actions/ChopTreeAction.java`, `FarmAction.java`, `FishAction.java`, `AnimalAction.java`
- Test: `src/test/java/com/villaging/ai/tools/GatherToolsTest.java`, in-game checks

**Interfaces:**
- Produces tools: `chop_tree(x,y,z)` (fells the connected logs above the chosen log, up to 64 blocks), `till(x,y,z)`, `plant(x,y,z,item)`, `harvest(x,y,z)` (mature crops only, replants when it has seeds), `fish(seconds: 5..120)`, `shear(entity_id?)`, `milk()`, `breed(entity_id?)`, `tame(entity_id?)`. All world changes go through `Protection`; entities must be passive animals within range.

- [ ] **Step 1: Write failing tests**: validators reject bad coordinates, `seconds` outside 5..120, non-integer entity ids.
- [ ] **Step 2: Run** `./gradlew test --tests GatherToolsTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the actions; `chop_tree` mines logs top-down with the same swing animation and respects protection per block; `fish` needs a rod and water within reach.
- [ ] **Step 4: In-game checks**: chop a small tree (logs collected into inventory); till, plant, and harvest wheat; shear a sheep (wool dropped); a protected log is not felled when `respectProtection=true`.
- [ ] **Step 5: Run** both suites. Expected: pass.
- [ ] **Step 6: Commit** `feat: gathering tools`.

### Task 15: Combat tools

**Files:**
- Create: `ai/tools/CombatTools.java`, `villager/actions/AttackAction.java`, `ShootAction.java`, `GuardAction.java`, `FleeAction.java`
- Test: `src/test/java/com/villaging/ai/tools/CombatToolsTest.java`, in-game checks

**Interfaces:**
- Consumes: `Protection.entityProblem`.
- Produces tools: `attack(entity_id)`, `shoot(entity_id)` (needs a bow/crossbow and arrows), `guard()` (stays near the king and attacks hostile mobs within 12 blocks, continuous like `follow_me`), `flee(seconds: 1..60)`. `attack`/`shoot` refuse players, tamed or owned entities, and the king's own entities with `failed: I won't hurt that`; they also refuse non-living targets.

- [ ] **Step 1: Write failing tests**: validators reject missing/fractional `entity_id`, `seconds` outside 1..60.
- [ ] **Step 2: Run** `./gradlew test --tests CombatToolsTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the actions with the hands' attack cooldown; `guard` re-checks every candidate target through `Protection.entityProblem` each tick.
- [ ] **Step 4: In-game checks**: attack kills a zombie; attack on a tamed wolf, on the king, and on an owned horse each fail without damage; `guard` kills an approaching zombie and ignores a nearby tamed cat; `flee` moves it away.
- [ ] **Step 5: Run** both suites. Expected: pass.
- [ ] **Step 6: Commit** `feat: combat tools`.

### Task 16: Movement extras

**Files:**
- Create: `ai/tools/MoveTools.java`, `villager/actions/SitAction.java`, `SleepAction.java`
- Test: `src/test/java/com/villaging/ai/tools/MoveToolsTest.java`, in-game checks

**Interfaces:**
- Produces tools: `sit()` (stays put until `stop`), `sleep(x,y,z)` (walks to a bed and sleeps if it is night or storming, else fails with the reason). Both continuous/blocking like existing actions.

- [ ] **Step 1: Write failing tests**: `sleep` rejects missing/far coordinates; `sit` takes no arguments.
- [ ] **Step 2: Run** `./gradlew test --tests MoveToolsTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the actions; `stop` cancels them.
- [ ] **Step 4: In-game checks**: sleep at night in a bed (state `isSleeping`); sleeping in daytime fails with a clear message.
- [ ] **Step 5: Run** both suites. Expected: pass.
- [ ] **Step 6: Commit** `feat: sit and sleep`.

### Task 17: Task scripts

**Files:**
- Create: `ai/script/Script.java`, `ScriptParser.java`, `ScriptRunner.java`, `Condition.java`, `ai/tools/ScriptTools.java`, `villager/ScriptManager.java`
- Test: `src/test/java/com/villaging/ai/script/ScriptParserTest.java`, `ScriptRunnerTest.java`, in-game checks

**Interfaces:**
- Produces:
  - Script JSON: `{"steps":[{"tool":"mine_block","args":{...}}, {"repeat":3,"steps":[...]}, {"until":{"have":{"item":"minecraft:oak_log","count":10}},"steps":[...]}, {"if":{"has_block":{"x":..,"y":..,"z":..}},"then":[...],"else":[...]}]}`.
  - `ScriptParser.parse(JsonObject, int maxDepth) -> Script` throws `ScriptException` (message for the model) on unknown keys, unknown tools, `repeat` outside 1..100, nesting over `maxDepth`, or more than 200 steps in the source.
  - `Condition`: `have{item,count}`, `has_block{x,y,z}`, `health_below{value}`, `night` (facts only, no code). Evaluation reads through a `Facts` interface `{int count(String item); boolean hasBlock(int,int,int); double health(); boolean isNight();}`.
  - `ScriptRunner.run(Script, ActionExecutor, Facts, int maxSteps, long maxNanos, LongSupplier clock, BooleanSupplier cancelled) -> String` runs steps in order, validating each via `executor.validate` and executing via `executor.execute`, stopping at the first `failed:`, at the step/time caps, or when cancelled; returns a summary `ok: ran N steps` or `failed: step N <tool>: <reason>`.
  - Tools: `run_script(script: object)` (validated with `ScriptParser`, runs in the background through `ScriptManager`, returns when finished or stopped), `stop_script()`; `stop` also cancels the running script. Risky steps use the same risk gate in the executor.

- [ ] **Step 1: Write failing tests** `ScriptParserTest`: valid script parses; unknown tool, unknown key, `repeat` 0 and 101, depth over limit, >200 source steps each raise `ScriptException` with a useful message. `ScriptRunnerTest` (fake executor and facts): steps run in order; `repeat 3` runs the body 3 times; `until` stops as soon as the fact becomes true and never exceeds `maxSteps`; `if` takes the right branch; first `failed:` stops the run and is reported with its step number; `maxNanos` exceeded stops with a time message; `cancelled` stops immediately; a step the executor's `validate` rejects stops the run without executing it.
- [ ] **Step 2: Run** `./gradlew test --tests ScriptParserTest --tests ScriptRunnerTest`. Expected: FAIL.
- [ ] **Step 3: Implement** parser, conditions, runner and tools; `ScriptManager` keeps one running script per villager and cancels it on `stop`, on villager death/unload, and on a new command.
- [ ] **Step 4: In-game checks**: a scripted model submits `run_script` that mines three marked blocks (all three gone, no further model calls were made during the run); a script with a failing middle step stops there; `stop` mid-script halts it; a risky step in a script asks.
- [ ] **Step 5: Run** both suites. Expected: pass.
- [ ] **Step 6: Commit** `feat: safe task scripts`.

### Task 18: Commander relay

**Files:**
- Create: `ai/tools/CommandTools.java`, `villager/RelayService.java`
- Test: `src/test/java/com/villaging/ai/tools/CommandToolsTest.java`, in-game checks

**Interfaces:**
- Produces tool `relay_order(villager_name: string, order: string)`: only offered/accepted when `VillagerBrain.commander()` is true; looks up another smart villager by name within `actionRange`; asks the king through `AskService.confirm` ("<commander> wants <name> to: <order>"); on yes, starts that villager's command loop as if the king said it (rate-limited and per-villager cancelled as usual). Ranks that set `commander` come in sub-project 4; this task only adds the flag use and tool. Without commander status the tool is rejected with a clear message.

- [ ] **Step 1: Write failing tests**: validator rejects blank name/order and orders over 500 characters; the handler (with a fake `RelayService`) refuses when not commander, refuses when the king answers no, and calls the service exactly once on yes.
- [ ] **Step 2: Run** `./gradlew test --tests CommandToolsTest`. Expected: FAIL.
- [ ] **Step 3: Implement** the tool and `RelayService` (uses `VillagingRuntime`'s command path with the king's UUID after approval).
- [ ] **Step 4: In-game checks**: set `commander` via test helper; relay with approval makes the second villager act; "No" does nothing; a non-commander call fails.
- [ ] **Step 5: Run** both suites. Expected: pass.
- [ ] **Step 6: Commit** `feat: commander relays orders with king approval`.

### Task 19: Prompt, docs and end-to-end

**Files:**
- Modify: `ai/SystemPrompt.java`, `README.md`, `src/test/java/com/villaging/ai/SystemPromptTest.java`, `src/gametest/.../VillagingClientGameTest.java`

**Interfaces:**
- Consumes: all tools above via `Tools.all()`.
- Produces: the prompt explains inventory, `look_around` first when unsure, scripts for long jobs, the never-hurt-players/pets rule, the ask dialog, that taking from the king needs an order, and how to say it cannot do something.

- [ ] **Step 1: Write failing tests**: the prompt contains each registered tool name, the phrases about not hurting players or pets, asking before risky acts, and using `run_script` for long jobs; it still tells the villager to be honest about what it cannot do.
- [ ] **Step 2: Run** `./gradlew test --tests SystemPromptTest`. Expected: FAIL.
- [ ] **Step 3: Implement** prompt text and README (new tools, settings table rows for `respectProtection`, `allowBreakBedrock`, `maxScriptSteps`, `maxScriptSeconds`, `maxScriptDepth`, the ask dialog, risky acts).
- [ ] **Step 4: End-to-end in-game check** with a scripted model: "get ten logs and make a chest": chop trees, craft planks, craft a chest, place it; screenshot at each stage.
- [ ] **Step 5: Run the full verification**: `./gradlew build` then `./gradlew runClientGameTest`. Expected: all unit tests and all in-game checks pass.
- [ ] **Step 6: Commit** `docs: abilities prompt, README and end-to-end check`.
