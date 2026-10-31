# Villaging Teams Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The king groups smart helpers into a named team with a leader; the leader's AI hands out work (assign, split builds) and members work at the same time. Release 1.2.0.

**Architecture:** Pure rules and splitter in `com.villaging.team` (no game classes). A `Team` record is saved as a Fabric attachment on every member (the leader's copy is authoritative). New tools follow the existing `ai/tools/*Tools` (definition + validator) and `villager/*Handlers` (game side) split. `assign` re-uses `VillagingRuntime.handleAnnouncement`-style delivery so every existing skill works; `assign_build` slices a blueprint and starts one `BuildAction` per member via the existing `BuildHandlers.begin` path.

**Tech Stack:** Java 25, Fabric 26.2 (Loom, attachments, Brigadier), JUnit 5, Fabric client game tests.

**Spec:** `docs/superpowers/specs/2026-10-31-villaging-teams-design.md`

## Global Constraints

- Team name regex `[a-z0-9_]{1,32}`; `teams.maxSize` default 5, clamped 2..20; a team needs >= 2 members to receive jobs.
- A helper is in at most one team; the leader is always a member; only the king creates or changes teams.
- Never answer "can't do that"; refusals say what to do instead. Never echo or store API keys.
- Pure code (team rules, splitter, config, validators) is unit-tested without the game; game code is checked in `VillagingClientGameTest` (`VILLAGING_ONLY="<substring>"` filter).
- Commit trailer: `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Do not push unasked.
- Run `./gradlew build --offline -q` (no output = pass) after each task; `./gradlew runClientGameTest --offline` only in Task 7.
- Version becomes `1.2.0` in `gradle.properties` (Task 8).

## Review Focus

1. A member already in another team (or the leader's copy no longer lists it): refuse to add it, and treat the stale copy as "not in a team".
2. A team that shrinks to 1 member: jobs are refused with a plain sentence, not a crash.
3. More members than there are blueprint layers: splitter uses fewer slices; idle members are told, not given empty builds.
4. `build.maxActiveBuilds` smaller than the team: extra slices wait, are reported, and start when a build finishes (never silently dropped).
5. Orders from a non-king player or from another member must never create/alter teams or reach `assign`.

---

### Task 1: Team rules (pure)

**Files:**
- Create: `src/main/java/com/villaging/team/Team.java`, `src/main/java/com/villaging/team/TeamException.java`
- Test: `src/test/java/com/villaging/team/TeamTest.java`

**Interfaces:**
- Produces: `record Team(String name, UUID leader, List<UUID> members)` with `static boolean isValidName(String)`, `static Team create(String name, UUID leader, List<UUID> others, int maxSize) throws TeamException` (leader first, duplicates removed, order kept), `Team with(UUID helper, int maxSize) throws TeamException`, `Team without(UUID helper) throws TeamException` (removing the leader is refused: "disband instead or name a new leader"), `boolean has(UUID)`, `boolean canWork()` (members >= 2). `TeamException extends Exception` with a plain-words message.

- [ ] **Step 1: Write failing tests** in `TeamTest`: `createPutsLeaderFirstAndDropsDuplicates`, `rejectsBadNames` ("Bad Name", "", 33 chars), `rejectsMoreThanMaxSize` (max 3, four people), `withRefusesDuplicateAndOverflow`, `withoutRefusesLeader`, `canWorkNeedsTwo` (leader only = false, leader + one = true), `leaderIsAlwaysMember`.
- [ ] **Step 2:** Run `./gradlew test --offline --tests com.villaging.team.TeamTest`. Expected: FAIL (class missing).
- [ ] **Step 3:** Implement `Team` and `TeamException` per Interfaces; messages in plain words naming the limit.
- [ ] **Step 4:** Re-run the test. Expected: PASS.
- [ ] **Step 5:** Commit `Add team rules`.

### Task 2: TeamSplitter (pure)

**Files:**
- Create: `src/main/java/com/villaging/team/TeamSplitter.java`
- Test: `src/test/java/com/villaging/team/TeamSplitterTest.java`

**Interfaces:**
- Consumes: `Blueprint` (`cells()`, `sizeX/Y/Z`, `palette()`, `Blueprint.create(...)`), `Cell(x,y,z,key)`, `BuildOrder.order`.
- Produces: `static List<Blueprint> split(Blueprint whole, int parts, String namePrefix)` — returns 1..parts blueprints with the same size/palette, each holding a subset of the cells, named `namePrefix + (i+1)` (valid blueprint names). Cuts at layer (y) boundaries, filling slices in order to about `ceil(total/parts)` cells; never splits a layer except when one layer alone exceeds a slice, then that layer is cut by x.

- [ ] **Step 1: Write failing tests**: `unionEqualsWholeAndNoOverlap` (cottage template, parts 3), `singlePartReturnsAllCells`, `fewerLayersThanPartsUsesFewerSlices` (1-layer floor, parts 4 -> slices cut by x, still 4 non-empty slices or fewer, never an empty slice), `slicesAreBalanced` (largest - smallest <= one layer's cell count), `namesAreValid` (`Blueprint.isValidName`), `partsBelowOneBecomesOne`.
- [ ] **Step 2:** Run the test class. Expected: FAIL.
- [ ] **Step 3:** Implement `split`; each slice built with `Blueprint.create` (same dims, so anchor and rotation work unchanged).
- [ ] **Step 4:** Re-run. Expected: PASS.
- [ ] **Step 5:** Commit `Add blueprint splitter for teams`.

### Task 3: Team settings

**Files:**
- Create: `src/main/java/com/villaging/config/TeamSettings.java`
- Modify: `src/main/java/com/villaging/config/VillagingConfig.java` (new component `team`, keep secondary constructors; json key `"teams"`), 
- Test: `src/test/java/com/villaging/config/TeamSettingsTest.java`

**Interfaces:**
- Produces: `record TeamSettings(int maxSize)`, `static TeamSettings defaults()` (5), `static TeamSettings from(Raw)`: missing/non-positive -> 5, clamp 2..20. `VillagingConfig.team()`. Follow `BuildSettings` exactly.

- [ ] **Step 1:** Tests: `defaultIsFive`, `clampsLowAndHigh` (1 -> 2, 99 -> 20), `missingFallsBack`, `configFileRoundTrip` (a `villaging.json` with `"teams":{"maxSize":8}` loads 8; absent loads 5).
- [ ] **Step 2:** Run. Expected: FAIL. **Step 3:** Implement. **Step 4:** Run, then full `./gradlew build --offline -q`. Expected: PASS, no output.
- [ ] **Step 5:** Commit `Add teams.maxSize setting`.

### Task 4: Saving teams and the king's team tools

**Files:**
- Create: `src/main/java/com/villaging/team/TeamData.java` (Codec for `Team`), `src/main/java/com/villaging/team/Teams.java` (attachment `villaging:team`, `init()`, `get(PathfinderMob)`, `save(Team, ServerLevel)` writes the record to every loaded member, `clear`, `teamOf` resolving leader-authoritative copy per Review Focus 1), `src/main/java/com/villaging/ai/tools/TeamTools.java`, `src/main/java/com/villaging/villager/TeamHandlers.java`
- Modify: `Villaging.java` (`Teams.init()`), `ai/tools/AbilityTools.java`, `villager/ToolHandlers.java` (`TeamHandlers.register()`), `ai/SystemPrompt.java` (TEAMS section)
- Test: `src/test/java/com/villaging/team/TeamDataTest.java`, `src/test/java/com/villaging/ai/tools/TeamToolsTest.java`, `SystemPromptTest` (TEAMS text present)

**Interfaces:**
- Consumes: Task 1 `Team`; Task 3 `ctx.config().team().maxSize()`; `Kings.isKing(server, uuid)`; `HelperRoster.find(List<Helper>, String)` for name matching; `VillagerBrains.get(m).name()`; `Kinds.isSmart`.
- Produces tools (king only, validated in `TeamTools`): `create_team(name, leader, members[])`, `add_to_team(team, helper)`, `remove_from_team(team, helper)`, `disband_team(team)`, `list_teams()`. `Teams.teamOf(PathfinderMob) -> Optional<Team>`; `Teams.members(ServerLevel, Team) -> List<PathfinderMob>` (loaded ones). Handlers return `ok:`/`failed:` text; names are matched with `HelperRoster.find` among smart helpers within 512 blocks of the king; ambiguity lists the names.

- [ ] **Step 1:** Tests first: `TeamDataTest.codecRoundTrips`; `TeamToolsTest` validators (`create_team` missing leader, bad name, members not an array of strings, > 20 members; `list_teams` no args; `disband_team` blank team); `SystemPromptTest.teamsSectionMentionsLeaderAndAssign`.
- [ ] **Step 2:** Run those tests. Expected: FAIL.
- [ ] **Step 3:** Implement the codec, tool definitions, registration, prompt section (short: teams exist; only a leader assigns; use the team tools when the king asks to make or change a team), and `TeamHandlers` (non-king callers get "failed: only the king changes teams"). `disband_team` also deletes saved team parts (`team_*` blueprints, see Task 6) and clears every member's attachment.
- [ ] **Step 4:** Run the three tests, then `./gradlew build --offline -q`. Expected: PASS.
- [ ] **Step 5:** Commit `Add team saving and the king's team tools`.

### Task 5: /villaging team commands

**Files:**
- Modify: `src/main/java/com/villaging/command/HelperCommands.java` (register `team` literal under the existing `villaging` root: `list`, `create <name> <leader> <members...>`, `add <team> <helper>`, `remove <team> <helper>`, `disband <team>`)
- Create: `src/main/java/com/villaging/team/TeamCommands.java` (pure parse of the greedy argument into leader + members using ` and ` / `,` separators: `static Parsed parse(String)` with `record Parsed(String leader, List<String> members)`)
- Test: `src/test/java/com/villaging/team/TeamCommandsTest.java`

**Interfaces:**
- Consumes: Task 4 `TeamHandlers` helper methods (extract the same create/add/remove/disband logic into static methods `TeamHandlers.create(ServerPlayer-or-ctx...)` so command and tool share one path; no AI needed for commands).
- Produces: `TeamCommands.parse(String text) -> Parsed`; command feedback uses `player.sendSystemMessage`.

- [ ] **Step 1:** Tests: `parsesLeaderAndMembers` ("Bob, Alice, Rex" -> leader Bob, members Alice and Rex; the first name is the leader), `acceptsAndSeparator`, `trimsAndDropsEmpty`, `emptyIsRejected`.
- [ ] **Step 2:** Run. Expected: FAIL. **Step 3:** Implement parse + command wiring (king-only via the existing `onlyKing`). **Step 4:** Run test + `./gradlew build --offline -q`. Expected: PASS.
- [ ] **Step 5:** Commit `Add /villaging team commands`.

### Task 6: Leader tools: assign, team_status, assign_build

**Files:**
- Create: `src/main/java/com/villaging/team/TeamJobs.java` (pure: `static List<Integer> startOrder(int slices, int free)` -> how many slices start now vs wait, used for Review Focus 4)
- Modify: `ai/tools/TeamTools.java` (leader-only tools), `villager/TeamHandlers.java`, `villager/BuildHandlers.java` (make `begin(...)` package-visible and let it take a `Blueprint` plus a `BuildJob` for a member), `ai/SystemPrompt.java` (leaders get a TEAM block: members' names, "delegate with assign / assign_build, then summarise to the king once")
- Test: `src/test/java/com/villaging/team/TeamJobsTest.java`, extend `TeamToolsTest`

**Interfaces:**
- Consumes: Tasks 1, 2, 4; `BuildJobs.set`, `BuildJob`, `BuildRegistry.tryStart/isBuilding/count`, `BlueprintStore.save(dir, bp)` / `names`, `BuildHandlers.load`.
- Produces tools: `assign(member, task)`: leader only; delivers `"(Your team leader <name> passes on the king's order) " + task + ". Tell the leader when you are done."` to the member through the same path as `handleAnnouncement`; refuses if the caller is not a leader, the team `canWork()` is false, or the member is not on the team. `team_status()`: one line per member: name, build percent or "idle/working". `assign_build(blueprint)`: requires the leader to have a planned `BuildJob` for `blueprint`; splits with `TeamSplitter.split(bp, memberCount, "team_" + team + "_")`, saves parts in the blueprint dir (bypassing the saved-blueprint cap), gives each member a `BuildJob` with the leader's anchor/rotation/stockpile, asks the king once (same rule as `start_build`, text names the team), and starts as many as `maxActiveBuilds` allows; the rest are reported "waiting". `TeamJobs.startOrder(slices, free)` returns indices to start now.

- [ ] **Step 1:** Tests: `TeamJobsTest` (`startsAllWhenRoom`, `startsOnlyFreeAndKeepsOrder`, `zeroFreeStartsNone`); `TeamToolsTest` validators for `assign` (blank member/task, task > 500 chars), `assign_build` (bad blueprint name).
- [ ] **Step 2:** Run. Expected: FAIL. **Step 3:** Implement. **Step 4:** Run, then `./gradlew build --offline -q`. Expected: PASS.
- [ ] **Step 5:** Commit `Add team leader tools: assign, team_status, assign_build`.

### Task 7: In-game checks

**Files:**
- Modify: `src/gametest/java/com/villaging/gametest/VillagingClientGameTest.java`

**Interfaces:** Consumes everything above; follow the style of the existing blueprint checks (scripted AI replies, `VILLAGING_ONLY` filter, stock inventories, a chest).

- [ ] **Step 1:** Add checks named with the word "team": (a) king says make a team of two helpers -> team saved on both; (b) non-king cannot create a team; (c) leader `assign`s a member "say hello" and the member speaks; (d) `assign_build` of the cottage with two members: every cell of the blueprint is built (count blocks vs `BuildOrder.order`), both members built part; (e) leader death disbands (members lose the team); (f) 3 members with `maxActiveBuilds` 1: extra slices wait then finish.
- [ ] **Step 2:** Run `VILLAGING_ONLY=team ./gradlew runClientGameTest --offline`. Expected: all team checks PASS; fix what they find (test-first for any unit-testable cause).
- [ ] **Step 3:** Run the full suite once. Expected: only the two known environment misses (golem "go to", clipboard) or fewer.
- [ ] **Step 4:** Commit `Add in-game checks for teams`.

### Task 8: Docs and release 1.2.0

**Files:**
- Modify: `gradle.properties` (1.2.0), `README.md` (Teams section, `teams.maxSize` config row, commands table), `docs/modrinth/LISTING.md` (1.2.0 section), `docs/modrinth/DESCRIPTION.md` ("Coming later" -> teams shipped; blueprint capture/import and server plugin remain), `docs/wiki/*` (Teams page), `src/main/java/com/villaging/guide/GuideContent.java` ("Teams" page + commands page), `AGENTS.md`

- [ ] **Step 1:** Update docs and guide text (plain words, no "can't"). Mention `/villaging team ...`, `teams.maxSize`, and that capture/import is now 1.3.0.
- [ ] **Step 2:** `./gradlew clean build --offline -q` Expected: no output; check `build/libs/villaging-1.2.0.jar` exists.
- [ ] **Step 3:** Commit `Release 1.2.0: teams`.
