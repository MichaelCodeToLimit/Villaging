# Villaging 1.0.4 Speed and Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Faster first replies (smaller, cache-friendly AI prompt, measured), faster tasks (measured trims), and three polish fixes (weapon wear, Infinity bow, bed). Release 1.0.4.

**Architecture:** Pure changes in `ai` (SystemPrompt, OllamaProvider body, a timing formatter) with unit tests; action-tick changes in `villager`/`config` with in-game checks. No tool names, arguments or safety rules change.

**Tech Stack:** Java 25, Fabric 26.2, JUnit 5, Fabric client game tests, local Ollama (`llama3.1:8b`) for measuring.

**Spec:** `docs/superpowers/specs/2026-11-01-villaging-speed-design.md`

## Global Constraints

- Today: system prompt 11,690 chars + tool list 21,931 chars (64 tools). After Task 1 the sum must be under 55% of that (<= 18,700 chars), enforced by a unit test.
- Every registered tool stays callable with the same name and arguments; no safety/ask/protection rule is weakened; "never say can't" wording stays.
- Never log message text or keys. Never echo or store API keys.
- Run `./gradlew build --offline -q` after each task (no output = pass); the in-game suite (`./gradlew runClientGameTest --offline`, `VILLAGING_ONLY="<substring>"`) in Tasks 5-8 and 10. Known environment misses allowed: the golem "go to" check (intermittent) and the clipboard read-back.
- Commit trailer `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Do not push unasked.
- Version becomes `1.0.4` in `gradle.properties` (Task 10).

## Review Focus

1. A small local model that used to learn tool names from the prompt text now sees them only in the tool list: reply still names the right tool in the existing scripted game checks, and the prompt keeps one line naming the groups.
2. Prompt order: the fixed text is identical for two different situations/king contexts (cache reuse), and golem and villager prompts both still build.
3. `num_predict`/`num_ctx` must not cut a tool call or a long build plan short: values leave room for the largest tool argument (`design_blueprint` plan up to 20,000 chars).
4. Lower `buildTicksPerBlock` must not break placing against doors or two-part blocks (the existing blueprint checks stay green).
5. Weapon wear must never break a weapon the helper is using mid-fight silently: the helper says so and the king is told.

---

### Task 1: Smaller, cache-friendly prompt

**Files:**
- Modify: `src/main/java/com/villaging/ai/SystemPrompt.java`, the longest descriptions in `src/main/java/com/villaging/ai/tools/*Tools.java`
- Test: `src/test/java/com/villaging/ai/SystemPromptTest.java` (change `mentionsAllRegisteredTools`), new `src/test/java/com/villaging/ai/PromptBudgetTest.java`

**Interfaces:**
- Consumes: `Tools.all()`, `SystemPrompt.build(VillagerBrain, SmartKind, String villagerState, String kingContext, String worldTitle)`.
- Produces: `SystemPrompt.build` with the same signature; no per-tool lines; adds one line naming the groups (looking, items and chests, crafting, farming, fighting, moving, scripts, people, building, teams).

- [ ] **Step 1: Write failing tests.** `PromptBudgetTest`: `promptPlusToolsStaysUnderBudget` (register all tools; `SystemPrompt.build(...)` length + sum of `ToolDef.toString().length()` <= 18_700); `fixedPartIsIdenticalForDifferentSituations` (build twice with different `villagerState` and `kingContext`; the text before the line `YOUR SITUATION` is equal); `noPerToolDescriptionLines` (prompt does not contain any `"- " + tool.name() + ": "`). In `SystemPromptTest` replace `mentionsAllRegisteredTools` with `namesEveryToolGroup` (prompt contains "building", "teams", "scripts").
- [ ] **Step 2:** Run `./gradlew test --offline --tests 'com.villaging.ai.*' -q`. Expected: the three new tests FAIL.
- [ ] **Step 3: Implement.** In `SystemPrompt.build` delete the loop that appends every tool; keep "HOW TO ACT" text and add the one groups line. Shorten the longest tool descriptions (find them by sorting `ToolDef.description().length()` in a throwaway test run) keeping every rule a description states (risk, ask, units, "when the king asks"). Keep `YOUR SITUATION` and `THE KING` as the last two sections.
- [ ] **Step 4:** Run the same test command, then `./gradlew build --offline -q`. Expected: PASS, no output.
- [ ] **Step 5:** Commit `Shrink the AI prompt and keep the changing parts last`.

### Task 2: Round timing line

**Files:**
- Create: `src/main/java/com/villaging/ai/RoundTimer.java`
- Modify: `src/main/java/com/villaging/ai/AgentLoop.java` (around the `provider.complete(...)` call at the line that calls `answer.complete(provider.complete(system, messages, Tools.all()))`)
- Test: `src/test/java/com/villaging/ai/RoundTimerTest.java`

**Interfaces:**
- Produces: `RoundTimer.line(int requestChars, long millis, int toolCalls) -> String` ("AI round: 4.2 s, 31,200 chars, 2 tool calls"); `RoundTimer.shouldLog(long millis) -> boolean` (true when `millis >= 1500` or system property `villaging.debug` is `true`).

- [ ] **Step 1:** Tests: `lineHasSecondsCharsAndCalls`, `lineNeverContainsAnyText` (the output matches `AI round: [0-9.]+ s, [0-9,]+ chars, [0-9]+ tool calls?`), `logsSlowRoundsOnly` (1499 false, 1500 true), `debugPropertyLogsEverything` (set/clear the property in the test).
- [ ] **Step 2:** Run `./gradlew test --offline --tests com.villaging.ai.RoundTimerTest -q`. Expected: FAIL (class missing).
- [ ] **Step 3:** Implement `RoundTimer`; in `AgentLoop` measure `System.nanoTime()` around the provider call, request chars = `system.length()` + sum of message text lengths + tool definitions' `toString().length()`, and `Villaging.LOGGER.info(RoundTimer.line(...))` when `shouldLog`.
- [ ] **Step 4:** Run the test, then `./gradlew build --offline -q`. Expected: PASS.
- [ ] **Step 5:** Commit `Log slow AI rounds with their size and time`.

### Task 3: Ollama request options

**Files:**
- Modify: `src/main/java/com/villaging/ai/OllamaProvider.java` (extract `static JsonObject buildBody(String model, String system, List<ChatMessage> messages, List<ToolDef> tools)` package-private from `complete`)
- Test: `src/test/java/com/villaging/ai/OllamaProviderTest.java` (create if missing)

**Interfaces:**
- Produces: body has `"options": {"num_ctx": 12288, "num_predict": 1500}` plus the existing fields (`model`, `stream:false`, `keep_alive`, `messages`, `tools`). `num_ctx` covers the shrunk prompt (about 4,700 tokens) with room for chat history; `num_predict` leaves room for the largest tool call (a 20,000-char plan is about 6,000 tokens, so cap `design_blueprint` replies by raising to 8192 only when the tool list contains `design_blueprint`... **simplify:** use `num_predict` 8192; it only ends runaway text, normal replies stop earlier).

- [ ] **Step 1:** Tests: `bodyKeepsExistingFields`, `bodyHasContextAndPredictLimits` (`options.num_ctx == 12288`, `options.num_predict == 8192`), `messagesAndToolsAreEncoded` (one tool, one user message).
- [ ] **Step 2:** Run the test class. Expected: FAIL. **Step 3:** Implement `buildBody` and call it from `complete`. **Step 4:** Run the test, then `./gradlew build --offline -q`. Expected: PASS.
- [ ] **Step 5:** Commit `Give Ollama a context size and an output cap`.

### Task 4: Measure replies before and after

**Files:**
- Create: `docs/superpowers/specs/2026-11-01-villaging-speed-results.md` (the measured numbers)

**Interfaces:** none (measurement only, no production code).

- [ ] **Step 1:** Start nothing new: check `curl -s http://localhost:11434/api/tags` lists `llama3.1:8b`; if Ollama is not running, record "not measured" and stop this task (the changelog then states sizes, not seconds).
- [ ] **Step 2:** Baseline: `git stash`-free approach: build the prompt of commit `1e4de4e` (before Task 1) and of HEAD with a throwaway test that prints the request JSON to a file in the scratchpad (never committed); send each with `curl -s -w "%{time_total}\n"` to `/api/chat` three times warm (first call excluded) with the user message "hello, what can you do?".
- [ ] **Step 3:** Write the table (chars, tokens, seconds before and after) into the results file. Expected: after is clearly faster; if the warm first reply on `llama3.1:8b` is still above 5 seconds, add a follow-up line recommending approach 2 (do not build it in this plan).
- [ ] **Step 4:** Commit `Record the measured reply times`.

### Task 5: Faster build speed

**Files:**
- Modify: `src/main/java/com/villaging/config/BuildSettings.java` (default `buildTicksPerBlock` 6 -> 3), `README.md` and `docs` where the default 6 is written
- Test: `src/test/java/com/villaging/config/VillagingConfigTest.java` (`buildSettingsDefaultToTheSpecValues` expects `new BuildSettings(20000, 50, 3, 64, 3)`)

- [ ] **Step 1:** Change the test's expected value to 3. **Step 2:** Run `./gradlew test --offline --tests com.villaging.config.VillagingConfigTest -q`. Expected: FAIL. **Step 3:** Change the default. **Step 4:** Run the test; then `VILLAGING_ONLY=blueprint ./gradlew runClientGameTest --offline`. Expected: all blueprint checks PASS (door and two-part blocks still correct; the checks use their own 1-tick config, so also build the cottage once with default settings through `use_template` + `plan_build` + `start_build` in the new check `blueprint build: the default speed still builds every block`).
- [ ] **Step 5:** Commit `Build twice as fast by default`.

### Task 6: Trim idle waits in actions

**Files:**
- Modify: only the actions this task's measurement names in `src/main/java/com/villaging/villager/` (candidates: `MineAction`, `ChopTreeAction`, `GoToAction`, `Approach`, `FollowAction`; constants such as `START_GRACE_TICKS`)

- [ ] **Step 1:** Run the full in-game suite and note the ten slowest checks from the log timestamps (`grep PASS` times); for each, read the action behind it and write down any wait that does nothing (fixed `ticks % N` pauses, grace periods that continue after the target is reached).
- [ ] **Step 2:** For each idle wait found, write the in-game check or adjust the existing one so it fails if the wait is still there (for example a mine or go_to check that must finish within a stated number of ticks), run it, watch it fail.
- [ ] **Step 3:** Shorten the wait. Safety waits (sneak, protection checks, door checks, ask pop-ups) are never touched.
- [ ] **Step 4:** Run the full in-game suite and `./gradlew build --offline -q`. Expected: only the known misses; the total suite time is lower than in Step 1.
- [ ] **Step 5:** Commit `Remove idle waits from actions`. If Step 1 finds no idle wait worth removing, ledger "Task 6: nothing to trim, evidence: <checks and times>" and skip Steps 2-5.

### Task 7: Weapons wear out

**Files:**
- Modify: `src/main/java/com/villaging/villager/CombatActions.java` (Attack, around `player.attack(target)` at line 48), possibly `HandsManager`/`VillagerInventories` if the stack in the helper's hand is a copy that is never written back
- Test: `src/gametest/java/com/villaging/gametest/VillagingClientGameTest.java` (new check `fighting: a weapon loses durability when it hits`)

- [ ] **Step 1:** Write the check: a helper with a sword (damage 0) attacks a zombie until it dies via the `attack` tool; assert the sword in the helper's inventory has `getDamageValue() > 0`. Run `VILLAGING_ONLY=weapon ./gradlew runClientGameTest --offline`. Expected: FAIL (damage stays 0).
- [ ] **Step 2:** Find where the stack the fake player swings is not the inventory stack (read how hands are synced) and make the damage reach the inventory stack. When a weapon breaks, the helper says so in plain words.
- [ ] **Step 3:** Run the check again. Expected: PASS. Run `VILLAGING_ONLY=fight` (and `guard`) checks. Expected: PASS.
- [ ] **Step 4:** Commit `Weapons wear out when helpers use them`.

### Task 8: Infinity bow

**Files:**
- Modify: `src/main/java/com/villaging/villager/CombatActions.java` (Shoot, the arrow-count success test at line ~169)
- Test: in-game check `fighting: an Infinity bow shoots without a false failure`

- [ ] **Step 1:** Write the check: a helper with a bow enchanted Infinity and one arrow shoots at a target; expected result `ok`, arrow count unchanged. Run `VILLAGING_ONLY=infinity ./gradlew runClientGameTest --offline`. Expected: FAIL (failure message although an arrow flew).
- [ ] **Step 2:** Decide success by whether an arrow entity was spawned by the shot (count arrows of the shooter in the level before and after), not only by the inventory count.
- [ ] **Step 3:** Run the check and the existing `shoot` check. Expected: PASS.
- [ ] **Step 4:** Commit `Infinity bows no longer report a false failure`.

### Task 9: Real sleeping in beds

**Files:**
- Modify: whichever code path lies the fake player down for `use_block` on a bed (find with `grep -rn "BedBlock" src/main`) so a villager or golem using a bed at night sleeps through `startSleeping` like `RestActions.Sleep` does
- Test: in-game check `rest: using a bed at night puts the helper to sleep`

- [ ] **Step 1:** Write the check: at night (`time set night`), `use_block` on a bed makes the helper `isSleeping()`; daytime gives a plain refusal ("it isn't night yet"). Run `VILLAGING_ONLY=bed ./gradlew runClientGameTest --offline`. Expected: FAIL.
- [ ] **Step 2:** Route bed use to `RestActions.Sleep` semantics (night check, occupied check, walk to it). **Step 3:** Run the check and the existing sleep checks. Expected: PASS.
- [ ] **Step 4:** Commit `Using a bed makes a helper really sleep`.

### Task 10: Docs and release 1.0.4

**Files:**
- Modify: `gradle.properties` (1.0.4), `README.md`, `docs/modrinth/LISTING.md` (new "Version 1.0.4" section), `docs/modrinth/CHANGELOG-1.0.4.md` (short, Modrinth-formatted like `CHANGELOG-1.0.3.md`, includes the measured before/after from Task 4), `AGENTS.md`, wiki if it mentions speeds

- [ ] **Step 1:** Update docs (plain words; build speed default 3; weapons wear; Infinity; beds). **Step 2:** `./gradlew clean build --offline -q`; check `build/libs/villaging-1.0.4.jar` and its `fabric.mod.json` version. **Step 3:** Full in-game suite once; only the known misses allowed. **Step 4:** Commit `Release 1.0.4: faster replies and tasks, polish`.
