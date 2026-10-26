# Villaging Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Fabric mod where the king opens a chat box on a smart villager, an LLM (cloud or Ollama) answers and drives it through five safe actions (`say`, `follow_me`, `stop`, `go_to`, `mine_block`).

**Architecture:** Pure-Java AI layer (`ai` package: provider interface, two providers, agent loop) with no Minecraft imports, so it is unit-tested with a fake HTTP server and a scripted fake model. A thin Minecraft layer (`villager`, `king`, `net`, `client` packages) holds villager data, the crown, packets and the chat screen. The agent loop runs on a background executor and hands each tool call to the server main thread through an `ActionExecutor`.

**Tech Stack:** Java 25, Fabric Loader 0.20.1, Fabric API 0.161.0+26.2, Fabric Loom 1.19.3 (plugin id `net.fabricmc.fabric-loom`, no mappings: 26.x is unobfuscated), Minecraft 26.2, Gson (ships with Minecraft), JDK `java.net.http` and `com.sun.net.httpserver` (tests), JUnit 5, Fabric game tests.

**Spec:** `docs/superpowers/specs/2026-10-26-villaging-foundation-design.md`

## Global Constraints

- Minecraft 26.2 on Fabric; mod id `villaging`; Java package root `com.villaging`.
- No dependencies beyond Fabric API, Minecraft's bundled libraries and JUnit (test only). HTTP uses the JDK client.
- AI calls never run on the main game thread; world changes only on the server main thread.
- Tool menu is exactly `say`, `follow_me`, `stop`, `go_to`, `mine_block`. The model never runs code.
- Agent step cap default 20; rate limit default 10 AI calls per minute per villager; action range default 64 blocks.
- Cloud and Ollama selected by config; API key only in the local config file, never in the repo.
- Only the king can command smart villagers; ordinary right-click still trades.
- Missing config or key: the mod loads, smart villagers are disabled, the king is told what to fix.
- Network or provider failure: villager says it can't think right now; real error logged; at most one retry.

## Review Focus

1. Forged chat packet from a player who is not king (or for a non-smart / far-away villager) is ignored.
2. Model returns `go_to` / `mine_block` with out-of-range, unloaded or non-numeric coordinates: rejected with an error result, no crash.
3. King sends a second command while a plan runs: the first plan is cancelled, only the second continues.
4. Provider returns empty, non-JSON, or unknown-tool output: loop recovers or ends with the "can't think" reply.
5. Villager dies, is unloaded, or the world closes mid-action; and a message of 0 or over 500 characters.

## File Structure

```
build.gradle, settings.gradle, gradle.properties, gradlew(.bat)
src/main/resources/fabric.mod.json, assets/villaging/lang/en_us.json,
  data/villaging/recipe/crown.json, assets/villaging/items/crown.json (+ model, texture)
src/main/java/com/villaging/
  Villaging.java                 server/common entrypoint (wires everything)
  config/VillagingConfig.java    config record + load/save
  ai/ChatMessage.java ToolDef.java ToolCall.java AiResponse.java AiProvider.java
  ai/CloudProvider.java OllamaProvider.java ProviderFactory.java
  ai/AgentLoop.java ActionExecutor.java RateLimiter.java Tools.java
  villager/VillagerBrain.java         persisted data (smart, name, memory)
  villager/ActionManager.java VillagerAction.java + FollowAction, GoToAction, MineAction
  villager/MinecraftActionExecutor.java
  king/KingData.java CrownItem.java
  net/CommandPayload.java ReplyPayload.java OpenChatPayload.java ServerNetworking.java
  command/CommandService.java         packet -> validation -> AgentLoop -> reply
src/client/java/com/villaging/client/VillagingClient.java, ChatScreen.java
src/test/java/com/villaging/...  (JUnit, pure Java)
src/gametest/java/com/villaging/ActionGameTests.java
```

---

### Task 1: Project scaffold that builds and launches

**Files:** Create all build files and `fabric.mod.json`, `Villaging.java`, `VillagingClient.java`.

**Interfaces:**
- Produces: `Villaging implements ModInitializer` with `public static final String MOD_ID = "villaging"` and `public static final Logger LOGGER`; client entrypoint `VillagingClient implements ClientModInitializer`.

- [ ] **Step 1:** Generate the project from the Fabric example mod template (`FabricMC/fabric-example-mod`, `26.2` branch or latest) into `C:\Users\micha\minecraft mods\villaging`; use `git init` there (the spec/plan folder moves under it). Set `gradle.properties` to the pinned versions above and the Loom plugin id; enable split client source set.
- [ ] **Step 2:** Add JUnit 5 (`testImplementation`, `useJUnitPlatform()`) and a trivial `SmokeTest` asserting `Villaging.MOD_ID.equals("villaging")`.
- [ ] **Step 3:** Run `./gradlew test build` — Expected: BUILD SUCCESSFUL, `build/libs/villaging-*.jar` exists.
- [ ] **Step 4:** Run `./gradlew runClient`, create a creative world — Expected: game reaches the world with the mod listed in the Mods screen and the log line "Villaging loaded".
- [ ] **Step 5:** Commit `chore: scaffold Fabric mod for 26.2`.

### Task 2: Config

**Files:** Create `config/VillagingConfig.java`; Test `config/VillagingConfigTest.java`.

**Interfaces:**
- Produces: `record VillagingConfig(String provider, String apiKey, String model, String baseUrl, String ollamaModel, int maxSteps, int callsPerMinute, int actionRange)` with `static VillagingConfig load(Path file)` (writes defaults if missing), `static VillagingConfig defaults()`, `boolean isUsable()` (cloud needs non-blank key; ollama needs non-blank base URL), `String problem()` (human-readable fix hint, "" when usable).
- Defaults: provider `"cloud"`, model `"claude-sonnet-5-5"`, baseUrl `"http://localhost:11434"` (Ollama), ollamaModel `"llama3.1:8b"`, maxSteps 20, callsPerMinute 10, actionRange 64. File: `config/villaging.json` via `FabricLoader.getConfigDir()`.

- [ ] **Step 1:** Tests: `loadCreatesDefaultsWhenMissing`, `loadReadsValues`, `cloudWithoutKeyIsNotUsable` (problem() mentions "apiKey"), `ollamaWithBaseUrlIsUsable`, `malformedJsonFallsBackToDefaults`.
- [ ] **Step 2:** Run `./gradlew test --tests '*ConfigTest'` — Expected: FAIL (class missing).
- [ ] **Step 3:** Implement with Gson; malformed file is logged and treated as defaults, never thrown.
- [ ] **Step 4:** Run the tests — Expected: PASS. Commit `feat: config`.

### Task 3: AI core types and tool schema

**Files:** Create `ai/ChatMessage.java`, `ToolDef.java`, `ToolCall.java`, `AiResponse.java`, `AiProvider.java`, `Tools.java`; Test `ai/ToolsTest.java`.

**Interfaces:**
- Produces:
  - `record ChatMessage(Role role, String text, List<ToolCall> toolCalls, String toolCallId)`, `enum Role {USER, ASSISTANT, TOOL_RESULT}`, factories `user(String)`, `assistant(String, List<ToolCall>)`, `toolResult(String toolCallId, String text)`.
  - `record ToolDef(String name, String description, JsonObject parametersSchema)`.
  - `record ToolCall(String id, String name, JsonObject args)`.
  - `record AiResponse(String text, List<ToolCall> toolCalls)` (text never null).
  - `interface AiProvider { AiResponse complete(String system, List<ChatMessage> messages, List<ToolDef> tools) throws AiException; }` and `class AiException extends Exception`.
  - `Tools` is an open registry: `static void register(ToolDef def, ArgValidator validator)` and `static List<ToolDef> all()`, so later sub-projects add player-ability tools (place, use, attack, craft, ...) without touching the agent loop. Foundation registers five `ToolDef`s with JSON schemas: `say{text}`, `follow_me{}`, `stop{}`, `go_to{x,y,z}` numbers, `mine_block{x,y,z}` integers. `interface ArgValidator { Optional<String> check(JsonObject args, int range, double originX, double originY, double originZ); }`. And `Tools.validate(ToolCall, int range, double originX, originY, originZ) -> Optional<String>` returning an error message or empty.

- [ ] **Step 1:** Tests: `registeredToolIsListedAndValidated` (register a dummy tool, it appears in `all()` and its validator runs), `unknownToolIsRejected`, `goToMissingCoordinateRejected`, `goToNonNumericRejected`, `goToNaNOrInfiniteRejected`, `goToOutsideRangeRejected` (range 64, target 65 away), `mineBlockInsideRangeAccepted`, `sayWithBlankTextRejected`, `sayOver500CharsRejected`.
- [ ] **Step 2:** Run `./gradlew test --tests '*ToolsTest'` — Expected: FAIL. Implement types and `Tools`. Re-run — Expected: PASS. Commit `feat: ai core types and tool validation`.

### Task 4: CloudProvider (Anthropic Messages API)

**Files:** Create `ai/CloudProvider.java`; Test `ai/CloudProviderTest.java` (uses `com.sun.net.httpserver.HttpServer` on an ephemeral port).

**Interfaces:**
- Consumes: Task 3 types, config.
- Produces: `CloudProvider(String baseUrl, String apiKey, String model)` implementing `AiProvider`. POST `{baseUrl}/v1/messages` with headers `x-api-key`, `anthropic-version: 2023-06-01`, `content-type: application/json`; body `{model, max_tokens: 1024, system, messages, tools:[{name, description, input_schema}]}`. Response `content[]` blocks of `text` and `tool_use{id,name,input}`. Tool results are sent as a `user` message with `tool_result{tool_use_id, content}` blocks; assistant tool calls as `tool_use` blocks. Base URL default `https://api.anthropic.com`. 30 s timeout.

- [ ] **Step 1:** Tests against the fake server: `sendsKeyVersionAndToolsHeadersAndBody`, `parsesTextOnly`, `parsesTextAndToolUse` (id/name/args preserved), `encodesToolResultMessages`, `non200ThrowsAiExceptionWithStatus`, `emptyContentGivesEmptyResponse`, `nonJsonBodyThrowsAiException`, `timeoutThrowsAiException` (server sleeps beyond a 200 ms test timeout).
- [ ] **Step 2:** Run `./gradlew test --tests '*CloudProviderTest'` — Expected: FAIL. Implement with `java.net.http.HttpClient` + Gson. Re-run — Expected: PASS. Commit `feat: cloud provider`.

### Task 5: OllamaProvider and ProviderFactory

**Files:** Create `ai/OllamaProvider.java`, `ai/ProviderFactory.java`; Tests `ai/OllamaProviderTest.java`, `ai/ProviderFactoryTest.java`.

**Interfaces:**
- Produces: `OllamaProvider(String baseUrl, String model)`; POST `{baseUrl}/api/chat` body `{model, stream:false, messages:[{role, content, tool_calls?}], tools:[{type:"function", function:{name, description, parameters}}]}` with the system prompt as the first `system` message and tool results as `role:"tool"`. Response `message.content` and `message.tool_calls[].function{name, arguments}` (arguments is an object; generate ids `call_<n>` since Ollama gives none). `ProviderFactory.create(VillagingConfig) -> AiProvider` (throws `IllegalStateException` with `config.problem()` if not usable).

- [ ] **Step 1:** Tests: `parsesTextAndToolCalls`, `generatesStableUniqueToolCallIds`, `encodesToolResultAsToolRole`, `connectionRefusedThrowsAiException`, `factoryPicksProviderByConfig` (cloud vs ollama), `factoryRejectsUnusableConfig`.
- [ ] **Step 2:** Run tests — Expected: FAIL. Implement. Re-run — Expected: PASS. Commit `feat: ollama provider and factory`.

### Task 6: Agent loop

**Files:** Create `ai/AgentLoop.java`, `ai/ActionExecutor.java`, `ai/RateLimiter.java`; Tests `ai/AgentLoopTest.java`, `ai/RateLimiterTest.java`.

**Interfaces:**
- Consumes: `AiProvider`, `Tools`.
- Produces:
  - `interface ActionExecutor { CompletableFuture<String> execute(ToolCall call); boolean isAlive(); }` — result string is fed back to the model ("ok", or "failed: reason"); a future that completes exceptionally or `isAlive()==false` ends the loop.
  - `class AgentLoop { AgentLoop(AiProvider provider, int maxSteps); Handle start(String system, List<ChatMessage> history, String userMessage, ActionExecutor exec, Consumer<String> onReply); }` and `interface Handle { void cancel(); }`. `onReply` is called for each non-blank assistant text (and once with the can't-think message on unrecoverable failure). Provider is retried once on `AiException`. History passed in is not mutated; the loop returns new messages via a second callback `Consumer<List<ChatMessage>> onFinished`.
  - `class RateLimiter { RateLimiter(int callsPerMinute, LongSupplier nanoClock); boolean tryAcquire(); }` sliding window.
  - Constant `AgentLoop.CANT_THINK = "I can't think right now."`.

- [ ] **Step 1:** Tests with a scripted fake provider: `plainTextReplyEndsLoop`, `toolCallIsExecutedAndResultFedBack` (provider sees a TOOL_RESULT message with the executor's string), `stopsAtStepCap` (provider always asks for a tool; exactly `maxSteps` provider calls), `invalidToolCallReturnsErrorResultToModel` (executor never called), `providerFailureRetriesOnceThenSaysCantThink`, `emptyResponseEndsLoopSilently`, `cancelStopsFurtherProviderCalls`, `deadVillagerEndsLoop`, `executorFailureIsReportedToModelNotThrown`.
- [ ] **Step 2:** RateLimiter tests: `allowsUpToLimitThenBlocks`, `windowSlidesAfterSixtySeconds` (fake clock).
- [ ] **Step 3:** Run — Expected: FAIL. Implement; the loop runs on the caller's executor (tests call synchronously via a direct executor passed to `start`); validation uses `Tools.validate` before `execute`. Re-run — Expected: PASS. Commit `feat: agent loop and rate limiter`.

### Task 7: Villager brain data

**Files:** Create `villager/VillagerBrain.java`; Test (game test) in `ActionGameTests` later; unit test `villager/VillagerBrainTest.java` for the pure parts.

**Interfaces:**
- Produces: `record VillagerBrain(boolean smart, String name, List<String> memory)` with `static final Codec<VillagerBrain> CODEC`, `static VillagerBrain DEFAULT`, `VillagerBrain withMemory(String line)` (keeps the last 20 lines), `static final AttachmentType<VillagerBrain> ATTACHMENT` registered via `AttachmentRegistry.createPersistent(Identifier, CODEC)`; helpers `static VillagerBrain get(Villager)` and `static void set(Villager, VillagerBrain)`.

- [ ] **Step 1:** Unit tests: `memoryKeepsLastTwentyLines`, `codecRoundTrips`, `defaultIsNotSmart`.
- [ ] **Step 2:** Run — Expected: FAIL. Implement; register attachment in `Villaging.onInitialize`. Re-run — Expected: PASS.
- [ ] **Step 3:** Manual check later in Task 11 (smart flag survives save/quit/reload). Commit `feat: villager brain data`.

### Task 8: King system and crown item

**Files:** Create `king/KingData.java`, `king/CrownItem.java`, recipe/lang/model/texture resources; Test `king/KingDataTest.java` (pure parts).

**Interfaces:**
- Produces: `KingData` (a `SavedData` on the overworld) with `Optional<UUID> king()`, `boolean isKing(UUID)`, `boolean claim(UUID)` (false if another king already exists), `void abdicate()`. `CrownItem extends Item`: using the crown in the air claims kingship for the user; using it on a villager makes it smart (king only; default name from a small list, e.g. "Bob", "Alma"); non-king gets a chat message "Only the king may use this." Registered as `villaging:crown`, shaped recipe: gold ingots in a ring around one diamond (`GGG / GDG`, bottom row empty).

- [ ] **Step 1:** Tests: `firstClaimSucceeds`, `secondPlayerCannotClaim`, `isKingOnlyForKing`, `abdicateClearsKing`, `kingDataSurvivesCodecRoundTrip`.
- [ ] **Step 2:** Run — Expected: FAIL. Implement, add recipe JSON, lang entries, simple item model/texture. Re-run — Expected: PASS.
- [ ] **Step 3:** Commit `feat: king and crown`.

### Task 9: Networking, command service and chat screen

**Files:** Create `net/CommandPayload.java`, `ReplyPayload.java`, `OpenChatPayload.java`, `ServerNetworking.java`, `command/CommandService.java`, client `ChatScreen.java`, and update `VillagingClient.java`; Test `command/CommandServiceTest.java` using an interface-based seam.

**Interfaces:**
- Consumes: Tasks 2, 5, 6, 7, 8.
- Produces:
  - Payload records (`CustomPacketPayload`): `CommandPayload(int villagerId, String message)` C2S; `ReplyPayload(int villagerId, String text)` S2C; `OpenChatPayload(int villagerId)` S2C.
  - `CommandService` with a seam interface `CommandContext { boolean isKing(UUID); boolean isSmartAndNear(UUID player, int villagerId, int maxDistance); ... }`; `CommandResult handle(UUID player, int villagerId, String message)` returning `ACCEPTED`, `REJECTED_NOT_KING`, `REJECTED_NOT_SMART`, `REJECTED_TOO_FAR`, `REJECTED_BAD_MESSAGE` (blank or over 500 chars), `REJECTED_RATE_LIMITED`, `DISABLED_BAD_CONFIG`. Accepted commands cancel any running `Handle` for that villager and start a new `AgentLoop` on a single background executor.
  - Server hook: `UseEntityCallback` — sneak + interact with a smart villager as king sends `OpenChatPayload` and cancels trading; otherwise normal behavior.
  - Reply display: chat message `<Name> text` plus the villager's custom name set to the text for 6 seconds (speech bubble), then restored.

- [ ] **Step 1:** Tests: `nonKingRejected` (forged packet), `nonSmartVillagerRejected`, `tooFarRejected`, `blankAndOverlongMessagesRejected` (0 and 501 chars), `secondCommandCancelsFirst` (fake loop records cancel), `badConfigReturnsDisabledAndMessage`, `rateLimitedRejected`.
- [ ] **Step 2:** Run — Expected: FAIL. Implement `CommandService`, payload codecs and registration (`PayloadTypeRegistry`), `ServerPlayNetworking` receiver (hops to server thread for world access), `ChatScreen` (single-line `EditBox`, Enter sends, Esc closes), client receiver for `OpenChatPayload` and `ReplyPayload`. Re-run — Expected: PASS.
- [ ] **Step 3:** Commit `feat: networking, command service, chat screen`.

### Task 10: World actions and executor

**Files:** Create `villager/VillagerAction.java`, `ActionManager.java`, `FollowAction.java`, `GoToAction.java`, `MineAction.java`, `MinecraftActionExecutor.java`; Test `src/gametest/.../ActionGameTests.java`.

**Interfaces:**
- Consumes: `ActionExecutor`, `Tools.validate`.
- Produces:
  - `interface VillagerAction { Status tick(Villager v); void cancel(Villager v); }` with `record Status(State state, String message)`, `enum State {RUNNING, DONE, FAILED}`.
  - `ActionManager` (`ServerTickEvents.END_WORLD_TICK`): holds at most one running action per villager, ticks it, completes the matching `CompletableFuture<String>` with `"ok"` or `"failed: <reason>"`, completes exceptionally and drops the action if the villager is removed or dead. Every tick while an action runs, it clears the villager's walk-target/activity memories so vanilla brain AI does not override movement.
  - `MinecraftActionExecutor implements ActionExecutor` (`execute` schedules onto the server thread; `say` returns "ok" immediately and sends a `ReplyPayload`; `isAlive()` is `!villager.isRemoved() && villager.isAlive()`).
  - Behaviors: `follow_me` follows the king until `stop` or a new command; `go_to` fails after 30 s or if no path; `mine_block` requires the target within 5 blocks of the villager after pathing next to it, takes `destroyTime`-based ticks, drops the item into the world, refuses bedrock, and refuses blocks with a container block entity that has items (`failed: container has items`).

- [ ] **Step 1:** Game tests: `goToReachesTarget`, `goToUnreachableFailsWithReason`, `mineBlockBreaksStone`, `mineBlockRefusesBedrock`, `mineBlockRefusesFilledChest`, `followMeStaysWithinFiveBlocks`, `stopCancelsRunningAction`, `removingVillagerMidActionDoesNotThrow`.
- [ ] **Step 2:** Run `./gradlew runGameTest` — Expected: FAIL (classes missing). Implement actions; re-run — Expected: PASS. If vanilla brain overrides movement in a test, fix in `ActionManager` before moving on.
- [ ] **Step 3:** Commit `feat: villager actions and executor`.

### Task 11: Wiring, prompt, and end-to-end verification

**Files:** Modify `Villaging.java`; Create `ai/SystemPrompt.java`, `king/KingContext.java`; Create `README.md`.

**Interfaces:**
- Produces: `SystemPrompt.build(VillagerBrain brain, String stateSummary, String kingContext) -> String` (villager name, role "smart villager serving the king", tool-use rules, short in-character replies, villager state: position, inventory summary, nearby entities and notable blocks within 8 blocks); `stateSummary` built from a villager on the server thread. `kingContext` is built on the server thread by `KingContext.describe(ServerPlayer)`: the king's position, facing, held item, and the block they are looking at (raycast, up to 20 blocks, as coordinates plus block name) so "that block", "here" and "over there" resolve.
- The prompt must instruct the model to: accept any phrasing, language or typos; resolve vague references from king context and memory; answer chat and questions with `say` only; say honestly when something is not possible yet and offer the nearest action; ask one short clarifying question when an order is unclear or risky; never claim to have done something no tool result confirmed; plan multi-part orders as ordered tool calls.

- [ ] **Step 1:** Tests `SystemPromptTest`: `includesNameAndState`, `mentionsAllFiveTools`, `includesKingContextAndLookedAtBlock`, `instructsFreeFormUnderstandingAndHonestRefusal` (prompt contains the clarify, honesty and vague-reference rules). `KingContextTest`: `describesLookedAtBlockCoordinates`, `saysNothingLookedAtWhenAirOnly`.
- [ ] **Step 2:** Implement, wire config load, `ProviderFactory`, executor, `ActionManager`, `CommandService`, `KingData`, crown and attachments in `Villaging.onInitialize`; in a bad-config case log and message the king instead of throwing.
- [ ] **Step 3:** Run `./gradlew test runGameTest build` — Expected: all PASS.
- [ ] **Step 4 (manual, cloud):** `./gradlew runClient`, put your API key in `config/villaging.json`, craft the crown, claim it, use it on a villager, sneak-interact, type "follow me" then "mine the stone in front of you". Expected: replies in character, both actions happen, no stutter.
- [ ] **Step 5 (manual, Ollama):** switch config `provider` to `"ollama"`, repeat. Expected: same behavior (slower is fine).
- [ ] **Step 5b (manual, free-form language):** with either provider, try each of these in one session and confirm a sensible result: "yo come with me", "folow me plz" (typo), "stop", "go stand over there" (while looking at a spot), "break that block", "do it again", "how are you doing?", "what can you do?", "build me a castle" (expected: honest "can't build yet" plus an offer of what it can do), "mine something" (expected: one short clarifying question), "come here then mine that stone" (expected: two ordered actions), and a message in another language. Any miss is fixed in `SystemPrompt`, not with keyword matching.
- [ ] **Step 6 (manual, persistence):** save and quit, reload. Expected: villager is still smart and king is still you.
- [ ] **Step 7:** Commit `feat: wire foundation end to end`; tag `foundation-v0.1`.

---

## Self-Review Notes

- **Spec coverage:** project setup (1), config/missing key (2, 11), tool menu and validation (3), cloud + Ollama (4, 5), agent loop, step cap, retry, cancel, rate limit (6), persisted brain (7), king/crown (8), packets/chat screen/speech bubble/forged-packet protection (9), actions with range, protected containers (10), prompt and success criteria (11). Protected-area support beyond container checks is deferred (no protection mods are targeted yet); this is called out in the README task.
- **Types:** `ToolCall`, `ActionExecutor`, `AgentLoop.Handle`, `CommandResult` and `VillagerBrain` names are used identically across tasks.
- **Risks to watch:** vanilla villager brain overriding movement (handled in Task 10); 26.x API names differing from older Fabric docs, so implementers should check the Fabric docs for 26.2 when a signature does not compile.
