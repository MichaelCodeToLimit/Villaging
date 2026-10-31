# Villaging Blueprints Engine (release 1.1.0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A smart helper can design or pick a blueprint, check materials against its inventory and a chest, ask the king, and build it block by block, pausing and resuming when materials run short.

**Architecture:** A pure-Java `blueprint` package (model, shape-plan compiler, templates, ordering, material planner, file store) with no Minecraft imports and full unit tests. A Minecraft-layer `build` set of classes (job saved as an attachment, a continuous `BuildAction` that reuses `PlaceBlockAction` and `ContainerAction` cell by cell) and nine new AI tools registered like the existing ones.

**Tech Stack:** Java 25, Fabric (Loom, Mojang names, Minecraft 26.2), Gson, JUnit 5, Fabric client game tests.

**Spec:** `docs/superpowers/specs/2026-10-31-villaging-blueprints-design.md` (sections 1 to 11, release 1.1.0 only; `capture_area`, `.nbt`, preview outline and extra templates are releases 1.2.0 and 1.3.0 and get their own plans).

## Global Constraints

- Java 25, TAB indentation, package root `com.villaging`. Pure code lives in `com.villaging.blueprint` and `com.villaging.ai.tools` and must not import `net.minecraft.*` or `net.fabricmc.*`.
- Limits: blueprint side at most 48, `sizeY` at most 64, at most 20,000 cells (`maxBlueprintBlocks`), at most 60 shapes per plan, at most 50 saved blueprints (`maxSavedBlueprints`), build speed 6 ticks per block (`buildTicksPerBlock`), ask the king above 64 blocks (`buildAskThreshold`), at most 3 active builds in a world (`maxActiveBuilds`). Blueprint names match `[a-z0-9_]{1,32}`.
- Banned blocks (never in a blueprint): `tnt`, `lava`, `fire`, `soul_fire`, `bedrock`, `barrier`, `light`, `command_block`, `chain_command_block`, `repeating_command_block`, `structure_block`, `structure_void`, `jigsaw`, `spawner`, `trial_spawner`, `vault`, `end_portal`, `end_portal_frame`, `end_gateway`, `nether_portal`, `respawn_anchor`, `water`, `bubble_column`, `piston_head`, `moving_piston`, `air`, `cave_air`, `void_air`.
- Never store, print or echo an API key. A helper never says "I can't do that right now"; a failing plan is fixed, a real AI failure gets the existing quota/key messages.
- Nothing is conjured: every placed block is taken from the helper's inventory (after fetching from the stockpile chest). Existing blocks are never broken silently.
- Commit messages end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`. Do not push; the owner pushes.
- Build and test: `./gradlew build --offline -q` (unit tests, no output means pass); `VILLAGING_ONLY="<text>" ./gradlew runClientGameTest --offline` for one in-game check; the full in-game suite takes 6 to 10 minutes and nothing heavy may run beside it.
- Windows shell quirks: use the editor tool for multi-line edits (not `sed a\`); verify Minecraft API signatures with `javap` on `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-{common,clientonly}-deobf/26.2/*.jar` (classpath separator `;`).

## Rulings made while reading the code (they refine the spec)

- **R1 Continuous build action.** The AI loop waits on a tool's future for at most `max(120, maxScriptSeconds)+30` seconds and then cancels the action. A build can run far longer, so `BuildAction.isContinuous()` is `true`: `start_build` returns `ok` at once and the build keeps ticking until it finishes, pauses or is replaced (like `FollowAction`). Progress and pauses are spoken with `VillagerSpeech.say`.
- **R2 Job saved as its own attachment**, `BuildJobs.ATTACHMENT` (persistent, codec of plain ints/strings), not a new `VillagerBrain` field, so the many `VillagerBrain` constructors stay untouched.
- **R3 Rotation.** Pure code rotates cell positions (`Blueprint.rotated`); block states (facing, half, shape) are rotated by Minecraft's `BlockState.rotate(Rotation)` at placement time.
- **R4 State fix-up.** Each cell is placed with the existing `PlaceBlockAction` (walking, reach, protection, hands). If the placed block's state differs from the wanted one (stairs facing, door hinge), `BuildAction` sets the wanted state with `level.setBlock` right after, only for a block it has just placed. The item was already taken from the inventory, so nothing is conjured.
- **R5 Shape plan travels as a JSON string** (`plan` argument of type `string`), because Gemini rejects `object` parameters without properties.
- **R6 Config.** The five build options live in a nested `build` object in `config/villaging.json` (`BuildSettings` record), added as one new component of `VillagingConfig` with the existing constructors kept.
- **R7 Primitives for 1.1.0:** `box`, `hollow_box`, `floor`, `walls`, `roof_gable`, `roof_flat`, `roof_pyramid`, `cylinder`, `line`, `door`, `window`. `stairs_run`, `fill_palette_pattern` and the `gatehouse` template move to release 1.3.0. Storage writes layers as strings without run-length encoding (a 48x48x64 box is under 150 KB).
- **R8 Wake-up after a material pause** uses the existing `VillagingRuntime.handleAnnouncement(king, helperId, message)` (one synthetic king message, one model call, rate limits apply).
- **R9 After a restart a saved running job is demoted to paused**; the helper says it can carry on and the king (or the AI) says `start_build`. It never resumes by itself.
- **R10 "Clear the site first" is deferred to release 1.2.0.** In 1.1.0 occupied cells are kept and reported as blocked (the safe default in spec section 8); the AI can still clear a spot with the existing `break_area` tool (which has its own approval rule) and run `plan_build` again.
- **R11 No one-day expiry for a dead helper's job.** The job is an attachment on the helper entity, so it disappears with the helper; nothing needs expiring.

## Review Focus

The spec is silent on these inputs; a person using the mod will meet them. Each line has a test in the named task.

1. A shape plan from the AI that overflows a limit, uses an unknown shape or palette key, or is not valid JSON: a precise error naming the shape number, never a crash or a half-built blueprint (Tasks 3, 8).
2. A banned block (TNT, lava, bedrock...) in a palette, template or AI plan: rejected with its reason (Tasks 1, 2).
3. A build interrupted by `stop`, a pause or a reload: resumes without re-placing finished cells and without losing the count (Tasks 10, 11).
4. Materials run short mid-build: pause, say exactly what is short, resume after a top-up (Tasks 5, 10, 11).
5. Occupied, unsupported or protected cells: skipped and reported as blocked, never broken or retried forever (Tasks 10, 11).

---

### Task 1: Block states, banned blocks, item cost

**Files:**
- Create: `src/main/java/com/villaging/blueprint/BlockRules.java`
- Test: `src/test/java/com/villaging/blueprint/BlockRulesTest.java`

**Interfaces:**
- Produces:
  ```java
  public final class BlockRules {
      public record State(String id, java.util.Map<String, String> properties) {}   // id always namespaced, e.g. "minecraft:oak_door"
      public record ItemCost(String item, int count) {}
      public static State parse(String blockState);          // "oak_door[half=upper,facing=north]"; throws IllegalArgumentException(message)
      public static String denyReason(String blockId);       // null when allowed, else a short reason
      public static ItemCost cost(State state);              // the item that places the block and how many for this cell (0 for the second half)
  }
  ```

- [ ] **Step 1: Write the failing tests**

```java
@Test void parsesIdsWithAndWithoutNamespaceAndProperties() {
    assertEquals("minecraft:stone", BlockRules.parse("stone").id());
    BlockRules.State s = BlockRules.parse("minecraft:oak_stairs[facing=north,half=bottom]");
    assertEquals("north", s.properties().get("facing"));
    assertEquals(2, s.properties().size());
}
@Test void badSyntaxIsAPreciseError() {
    assertTrue(assertThrows(IllegalArgumentException.class, () -> BlockRules.parse("stone[facing")).getMessage().contains("stone[facing"));
    assertThrows(IllegalArgumentException.class, () -> BlockRules.parse(""));
}
@Test void bannedBlocksHaveReasons() {
    for (String id : List.of("tnt", "minecraft:lava", "bedrock", "spawner", "water", "command_block", "fire"))
        assertNotNull(BlockRules.denyReason(id), id);
    assertNull(BlockRules.denyReason("minecraft:stone_bricks"));
}
@Test void costCountsItemsNotCells() {
    assertEquals(new ItemCost("minecraft:stone", 1), BlockRules.cost(BlockRules.parse("stone")));
    assertEquals(2, BlockRules.cost(BlockRules.parse("oak_slab[type=double]")).count());
    assertEquals(1, BlockRules.cost(BlockRules.parse("oak_slab[type=bottom]")).count());
    assertEquals(1, BlockRules.cost(BlockRules.parse("oak_door[half=lower]")).count());
    assertEquals(0, BlockRules.cost(BlockRules.parse("oak_door[half=upper]")).count());
    assertEquals(0, BlockRules.cost(BlockRules.parse("red_bed[part=head]")).count());
    assertEquals("minecraft:torch", BlockRules.cost(BlockRules.parse("wall_torch[facing=east]")).item());
    assertEquals("minecraft:oak_sign", BlockRules.cost(BlockRules.parse("oak_wall_sign")).item());
}
```

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew test --offline -q`. Expected: compile errors "cannot find symbol BlockRules".
- [ ] **Step 3: Implement `BlockRules`** with the signatures above and the banned list from Global Constraints (a `Set<String>` of ids without namespace, with a reason string each). Cost rules: `type=double` slabs count 2; `half=upper` and `part=head` cost 0; `wall_torch`/`soul_wall_torch`/`redstone_wall_torch` map to `torch`/`soul_torch`/`redstone_torch`; `*_wall_sign`, `*_wall_hanging_sign`, `*_wall_banner` map to the same name without `wall_`; everything else costs the block id as the item.
- [ ] **Step 4: Run tests.** Run: `./gradlew test --offline -q`. Expected: no output (pass).
- [ ] **Step 5: Commit** `git add src/main/java/com/villaging/blueprint src/test/java/com/villaging/blueprint && git commit -m "Add block rules for blueprints"` (with the trailer).

---

### Task 2: Blueprint model with validation and rotation

**Files:**
- Create: `src/main/java/com/villaging/blueprint/Blueprint.java`, `Cell.java`, `BlueprintException.java`
- Test: `src/test/java/com/villaging/blueprint/BlueprintTest.java`

**Interfaces:**
- Consumes: `BlockRules.parse`, `BlockRules.denyReason` (Task 1).
- Produces:
  ```java
  public record Cell(int x, int y, int z, char key) {}
  public class BlueprintException extends Exception { public BlueprintException(String message); }
  public record Blueprint(String name, int sizeX, int sizeY, int sizeZ, java.util.Map<Character, String> palette,
                          java.util.List<Cell> cells, String origin) {
      public static final int MAX_SIDE = 48, MAX_HEIGHT = 64, MAX_CELLS = 20_000;
      public static Blueprint create(String name, int sx, int sy, int sz, java.util.Map<Character, String> palette,
                                     java.util.List<Cell> cells, String origin, int maxCells) throws BlueprintException;
      public Blueprint rotated(int quarterTurns);   // 0..3 clockwise looking down; sizes swap for odd turns; positions renormalised to >= 0
      public String stateAt(Cell c);                // palette lookup for a cell
  }
  ```

- [ ] **Step 1: Write the failing tests**

```java
@Test void validBlueprintIsCreated() throws Exception {
    Blueprint b = Blueprint.create("hut", 3, 2, 3, Map.of('S', "minecraft:stone"), List.of(new Cell(0,0,0,'S'), new Cell(1,1,2,'S')), "ai", 100);
    assertEquals(2, b.cells().size());
}
@Test void badNamesAndSizesAreRejectedWithTheReason() {
    assertMessage("name", () -> Blueprint.create("Bad Name", 1,1,1, PAL, List.of(), "ai", 10));
    assertMessage("48", () -> Blueprint.create("a", 49,1,1, PAL, List.of(), "ai", 10));
    assertMessage("64", () -> Blueprint.create("a", 1,65,1, PAL, List.of(), "ai", 10));
}
@Test void bannedBlocksAreRejectedByName() {
    assertMessage("tnt", () -> Blueprint.create("a", 1,1,1, Map.of('T', "minecraft:tnt"), List.of(new Cell(0,0,0,'T')), "ai", 10));
}
@Test void cellProblemsAreRejected() {
    assertMessage("outside", () -> Blueprint.create("a", 2,2,2, PAL, List.of(new Cell(2,0,0,'S')), "ai", 10));
    assertMessage("palette", () -> Blueprint.create("a", 2,2,2, PAL, List.of(new Cell(0,0,0,'Q')), "ai", 10));
    assertMessage("twice", () -> Blueprint.create("a", 2,2,2, PAL, List.of(new Cell(0,0,0,'S'), new Cell(0,0,0,'S')), "ai", 10));
    assertMessage("blocks", () -> Blueprint.create("a", 2,2,2, PAL, List.of(new Cell(0,0,0,'S'), new Cell(1,0,0,'S')), "ai", 1));
}
@Test void fourQuarterTurnsGiveTheOriginalAndOddTurnsSwapSizes() throws Exception {
    Blueprint b = Blueprint.create("a", 4,2,3, PAL, List.of(new Cell(0,0,0,'S'), new Cell(3,1,2,'S')), "ai", 10);
    assertEquals(b.cells().stream().sorted(CMP).toList(), b.rotated(1).rotated(1).rotated(1).rotated(1).cells().stream().sorted(CMP).toList());
    assertEquals(3, b.rotated(1).sizeX());
    assertEquals(4, b.rotated(1).sizeZ());
    assertTrue(b.rotated(1).cells().stream().allMatch(c -> c.x() >= 0 && c.x() < 3 && c.z() >= 0 && c.z() < 4));
}
```
(`assertMessage(fragment, ThrowingRunnable)` is a test helper asserting a `BlueprintException` whose message contains the fragment.)

- [ ] **Step 2: Run to verify failure.** Run: `./gradlew test --offline -q`. Expected: `cannot find symbol Blueprint`.
- [ ] **Step 3: Implement `Blueprint`, `Cell`, `BlueprintException`.** `create` validates name `[a-z0-9_]{1,32}`, size bounds, every palette state through `BlockRules.parse` and `denyReason`, then each cell (bounds, key present, no duplicate position), then `cells.size() <= maxCells`. Messages must contain the quoted fragments above. `rotated(n)`: one clockwise turn maps `(x, z)` to `(sizeZ - 1 - z, x)` and swaps `sizeX`/`sizeZ`; apply `n % 4` times.
- [ ] **Step 4: Run tests.** Expected: pass.
- [ ] **Step 5: Commit** "Add the blueprint model".

---

### Task 3: Shape plan compiler

**Files:**
- Create: `src/main/java/com/villaging/blueprint/ShapePlan.java`
- Test: `src/test/java/com/villaging/blueprint/ShapePlanTest.java`

**Interfaces:**
- Consumes: `Blueprint.create`, `Cell`, `BlockRules` (Tasks 1 and 2).
- Produces:
  ```java
  public final class ShapePlan {
      public static final int MAX_SHAPES = 60;
      /** Plan JSON: {"size":[w,h,d],"palette":{"S":"minecraft:stone_bricks"},"shapes":[{"type":"hollow_box","x":0,"y":0,"z":0,"w":9,"h":5,"d":7,"block":"S"}]} */
      public static Blueprint compile(String name, com.google.gson.JsonObject plan, int maxCells) throws BlueprintException;
  }
  ```
  Shape types and fields (all integers relative to the plan origin; `block` is a palette key; optional `state` is a property string such as `facing=north`):
  `box` (x,y,z,w,h,d), `hollow_box` (same plus optional `floor`/`roof` booleans, default true/true... the hollow box keeps its walls always), `floor` (x,y,z,w,d: one layer), `walls` (x,y,z,w,h,d: hollow without floor or roof), `roof_gable` (x,y,z,w,d, `ridge`: "x"|"z", `block` is the stairs/slab family, `fill` palette key for the gable ends), `roof_flat` (x,y,z,w,d), `roof_pyramid` (x,y,z,w,d), `cylinder` (x,y,z,radius,h, optional `hollow`), `line` (x1,y1,z1,x2,y2,z2), `door` (x,y,z,facing: n|e|s|w, `block` is a door family: places lower and upper), `window` (x,y,z,w,h on a wall: clears the cells to the `glass` palette key).

- [ ] **Step 1: Write the failing tests**

```java
@Test void hollowBoxHasWallsFloorAndRoofButNoInterior() throws Exception {
    Blueprint b = ShapePlan.compile("h", json("""
        {"size":[5,4,5],"palette":{"S":"minecraft:stone"},"shapes":[{"type":"hollow_box","x":0,"y":0,"z":0,"w":5,"h":4,"d":5,"block":"S"}]}"""), 1000);
    assertEquals(5*4*5 - 3*2*3, b.cells().size());           // 100 - interior 18
}
@Test void laterShapesOverwriteEarlierOnes() throws Exception {
    Blueprint b = compile(plan(box(0,0,0,2,1,1,"S"), box(1,0,0,1,1,1,"G")));
    assertEquals("minecraft:glass", b.stateAt(cellAt(b, 1,0,0)));
}
@Test void roofGableStepsUpToARidgeAndFillsTheEnds() throws Exception { /* 7x5 roof, ridge along x: ridge row at the middle z, stairs facing outward on both slopes */ }
@Test void doorPlacesLowerAndUpperHalves() throws Exception { /* lower half=lower and upper half=upper, same facing */ }
@Test void preciseErrorsNameTheShapeNumber() {
    assertMessage("shape 2", () -> compile(plan(box(0,0,0,1,1,1,"S"), json("{\"type\":\"sphere\"}"))));            // unknown type
    assertMessage("shape 1", () -> compile(plan(box(0,0,0,60,1,1,"S"))));                                         // wider than 48
    assertMessage("shape 1", () -> compile(plan(box(0,0,0,1,1,1,"Q"))));                                          // unknown palette key
    assertMessage("JSON", () -> ShapePlan.compile("a", null, 100));                                              // missing plan
    assertMessage("61", () -> compile(planWith(61 shapes)));                                                      // too many shapes
    assertMessage("tnt", () -> compile(paletteOf("minecraft:tnt")));                                              // banned block
}
@Test void cellBudgetIsEnforcedBeforeBuildingAHugeList() {
    assertMessage("blocks", () -> ShapePlan.compile("a", json(BIG_BOX_47x47x47), 5000));
}
```

- [ ] **Step 2: Run to verify failure.** Expected: `cannot find symbol ShapePlan`.
- [ ] **Step 3: Implement `ShapePlan.compile`.** Parse the JSON defensively (every field type-checked, messages like `shape 2: unknown type "sphere"` and `shape 1: x range 0..59 is wider than 48`). Rasterise each shape into a `Map<position, stateString>` in list order (later wins), mapping palette keys to full state strings (adding `state` properties, stairs `facing`/`half`, door halves). The plan's `size` bounds everything and must satisfy `Blueprint` limits. Assign internal palette characters per distinct state string from `A-Za-z0-9` and so on. Reject before rasterising when the sum of shape volumes would exceed 4 times `maxCells`. Finish with `Blueprint.create(name, ..., "ai", maxCells)`.
- [ ] **Step 4: Run tests.** Expected: pass.
- [ ] **Step 5: Commit** "Compile AI shape plans into blueprints".

---

### Task 4: Built-in templates

**Files:**
- Create: `src/main/java/com/villaging/blueprint/Templates.java`
- Test: `src/test/java/com/villaging/blueprint/TemplatesTest.java`

**Interfaces:**
- Consumes: `ShapePlan.compile`, `Blueprint`.
- Produces:
  ```java
  public final class Templates {
      public record Params(int width, int depth, int height, String style, String roof) {}
      public static java.util.List<String> ids();                      // cottage, tower, wall, bridge, castle
      public static Params defaults(String id);
      public static String bounds(String id);                          // human text, e.g. "width 5..16, depth 5..16, height 3..8"
      public static Blueprint build(String id, String name, Params p, int maxCells) throws BlueprintException;
  }
  ```
  Styles: `stone`, `wood`, `brick`, `sandstone`, `dark`. Roofs: `gable`, `flat`, `pyramid` (cottage and castle keep only).

- [ ] **Step 1: Write the failing tests** (for every id and every style over a grid of parameters inside the bounds):

```java
@Test void everyTemplateCompilesWithinLimitsAcrossItsWholeParameterRange() { /* grid: min, default, max per dimension, all styles */ }
@Test void enclosedBuildingsKeepTheWeatherOutExceptThroughTheDoor() {
    // flood fill from outside through air cells of the bounding box plus a 1-cell margin: for cottage, tower, castle keep the interior is reachable
    // only via the door cell(s); with the door cells filled in, no interior air cell is reachable.
}
@Test void cottagesAndTowersHaveADoorAndALight() { /* a *_door lower half exists at ground level; a torch, lantern or glowstone exists inside */ }
@Test void outOfBoundsParametersAreCorrectedNotRejected() { assertEquals(16, Templates.build("cottage","c", new Params(99,5,4,"stone","gable"), 20000).sizeX()); }
@Test void unknownTemplateOrStyleSaysWhatExists() { assertMessage("cottage", () -> Templates.build("igloo", ...)); assertMessage("stone", () -> style "pink"); }
@Test void castleStaysUnderTheDefaultCellLimit() { assertTrue(Templates.build("castle","k", Templates.defaults("castle"), 20000).cells().size() < 20000); }
```

- [ ] **Step 2: Run to verify failure.** Expected: `cannot find symbol Templates`.
- [ ] **Step 3: Implement `Templates`.** Each template builds a plan `JsonObject` (palette per style: wall, floor, roof stairs, window glass, door, light) and passes it through `ShapePlan.compile`. Default and bound sizes: cottage 7x7x4 (5..16, 5..16, 3..8), tower 5x5x12 (4..12, 4..12, 6..40) with a ladder column, wall 12x1x5 with a top walkway and crenellations (length 4..48, height 3..12), bridge 12x3x1 (length 5..48, width 3..7) with railings, castle (width 24..48, depth 24..48, height 8..20; keep in the middle, curtain wall, four corner towers). Clamp out-of-range parameters to the nearest bound.
- [ ] **Step 4: Run tests.** Expected: pass.
- [ ] **Step 5: Commit** "Add built-in blueprint templates".

---

### Task 5: Build order, material planner, ASCII view

**Files:**
- Create: `src/main/java/com/villaging/blueprint/BuildOrder.java`, `MaterialPlanner.java`, `AsciiView.java`
- Test: `BuildOrderTest.java`, `MaterialPlannerTest.java`, `AsciiViewTest.java` in `src/test/java/com/villaging/blueprint/`

**Interfaces:**
- Consumes: `Blueprint`, `Cell`, `BlockRules`.
- Produces:
  ```java
  public final class BuildOrder { public static java.util.List<Cell> order(Blueprint b); }       // cells with cost 0 (upper door halves, bed heads) are left out
  public final class MaterialPlanner {
      public record Row(String item, int needed, int have, int missing) {}
      public record Report(java.util.List<Row> rows, int blocks) { public boolean complete(); public java.util.List<Row> missingRows(); public String describeMissing(); }
      public static Report plan(Blueprint b, java.util.Map<String,Integer> carried, java.util.Map<String,Integer> stockpile);
  }
  public final class AsciiView { public static String layer(Blueprint b, int y); }              // one row per z, '.' for air, palette letter otherwise, plus a legend
  ```

- [ ] **Step 1: Write the failing tests**

```java
@Test void orderIsBottomUpAndIncludesEveryPlaceableCellOnce() { /* y never decreases; set of cells equals blueprint cells minus zero-cost cells */ }
@Test void attachmentsComeAfterFullBlocksInTheSameLayer() { /* a torch cell and a stone cell on the same y: stone first */ }
@Test void orderIsDeterministic() { assertEquals(order(b), order(b)); }
@Test void havePlusStockpileCoverNeedAndTheRestIsMissing() {
    Report r = MaterialPlanner.plan(b312Stone, Map.of("minecraft:stone", 100), Map.of("minecraft:stone", 150));
    assertEquals(62, r.rows().get(0).missing());
    assertFalse(r.complete());
    assertEquals("62 minecraft:stone", r.describeMissing());   // plain words, e.g. "62 stone" in the final text
}
@Test void carriedItemsAreUsedBeforeTheStockpileAndNothingIsDoubleCounted() { /* have = min(needed, carried + stockpile) */ }
@Test void completeWhenEverythingIsAvailable() { /* describeMissing() is empty */ }
@Test void asciiLayerShowsRowsAndALegend() { /* 3x3 hollow layer prints "SSS\nS.S\nSSS" and "S = minecraft:stone" */ }
```

- [ ] **Step 2: Run to verify failure.** Expected: `cannot find symbol BuildOrder`.
- [ ] **Step 3: Implement the three classes.** Order: sort by `y`, then attachment flag (torch, lantern, door, button, ladder, sign, banner, carpet, pressure plate, flower pot, redstone parts last within the layer), then distance from the (0,0) corner, then `x`, `z`. Planner sums `BlockRules.cost` per item.
- [ ] **Step 4: Run tests.** Expected: pass.
- [ ] **Step 5: Commit** "Add build order, material planning and ASCII view".

---

### Task 6: Blueprint store (JSON files)

**Files:**
- Create: `src/main/java/com/villaging/blueprint/BlueprintStore.java`
- Test: `src/test/java/com/villaging/blueprint/BlueprintStoreTest.java`

**Interfaces:**
- Consumes: `Blueprint`, `Cell`, `BlueprintException`.
- Produces:
  ```java
  public final class BlueprintStore {
      public static void save(java.nio.file.Path dir, Blueprint b) throws java.io.IOException;
      public static java.util.Optional<Blueprint> load(java.nio.file.Path dir, String name, int maxCells);   // empty when missing or corrupt
      public static java.util.List<String> names(java.nio.file.Path dir);                                   // sorted
      public static boolean delete(java.nio.file.Path dir, String name);
  }
  ```
  File format: `{"name","size":[x,y,z],"origin","palette":{"S":"state"},"layers":[["row", ...], ...]}` where each layer is a list of z rows of x characters, `.` for air; layers listed from y = 0.

- [ ] **Step 1: Write the failing tests** with `@TempDir`:

```java
@Test void roundTripKeepsEveryCell() { /* a 10x6x8 blueprint with several keys saves and loads equal (cells compared sorted) */ }
@Test void namesAreSortedAndDeleteRemovesTheFile() { }
@Test void aCorruptOrTruncatedFileLoadsAsEmptyInsteadOfThrowing() { Files.writeString(dir.resolve("bad.json"), "{\"name\":"); assertTrue(BlueprintStore.load(dir, "bad", 100).isEmpty()); }
@Test void aFileWithABannedBlockIsRefusedOnLoad() { /* hand-written file with tnt in the palette loads as empty */ }
@Test void pathTricksInNamesAreRefused() { assertTrue(BlueprintStore.load(dir, "../evil", 100).isEmpty()); assertFalse(BlueprintStore.delete(dir, "../evil")); }
@Test void aFullSizeBlueprintStaysUnder200Kilobytes() { /* 48x64x48 filled to 20,000 cells */ }
```

- [ ] **Step 2: Run to verify failure.** Expected: `cannot find symbol BlueprintStore`.
- [ ] **Step 3: Implement `BlueprintStore`** with Gson; validate through `Blueprint.create` on load; names must match the name rule before touching the file system.
- [ ] **Step 4: Run tests.** Expected: pass.
- [ ] **Step 5: Commit** "Store blueprints as JSON files".

---

### Task 7: Build settings in the config

**Files:**
- Modify: `src/main/java/com/villaging/config/VillagingConfig.java`
- Test: `src/test/java/com/villaging/config/VillagingConfigTest.java`

**Interfaces:**
- Produces:
  ```java
  public record BuildSettings(int maxBlueprintBlocks, int maxSavedBlueprints, int buildTicksPerBlock, int buildAskThreshold, int maxActiveBuilds) {
      public static BuildSettings defaults();    // 20000, 50, 6, 64, 3
  }
  // VillagingConfig gains a final component: BuildSettings build. The existing 16-argument and 17-argument constructors keep working (build = defaults()).
  // withKey, backupConfig and load carry it along. JSON: {"build": {"buildTicksPerBlock": 4, ...}}; missing or non-positive values fall back to the defaults.
  ```

- [ ] **Step 1: Write the failing tests**

```java
@Test void buildSettingsDefaultToTheSpecValues() { assertEquals(new BuildSettings(20000,50,6,64,3), VillagingConfig.defaults().build()); }
@Test void buildSettingsAreReadFromTheFileAndBadValuesFallBack() { /* {"build":{"buildTicksPerBlock":2,"maxActiveBuilds":0}} -> 2 and 3 */ }
@Test void savingAKeyKeepsTheBuildSettings() { }
```

- [ ] **Step 2: Run to verify failure.** Expected: `cannot find symbol build`.
- [ ] **Step 3: Implement** (record component, secondary constructors, `Raw` nested class with `Integer` fields, `withKey`/`backupConfig` pass `build`).
- [ ] **Step 4: Run tests.** Run: `./gradlew build --offline -q`. Expected: no output (the gametest sources still compile through the kept constructors).
- [ ] **Step 5: Commit** "Add build settings to the config".

---

### Task 8: The nine tools, prompt section and guide page

**Files:**
- Create: `src/main/java/com/villaging/ai/tools/BuildTools.java`
- Modify: `src/main/java/com/villaging/ai/tools/AbilityTools.java` (call `BuildTools.register()`), `src/main/java/com/villaging/ai/SystemPrompt.java` (BUILDING section), `src/main/java/com/villaging/guide/GuideContent.java` (new page "Building", at most 650 characters, mentions every new tool)
- Test: `src/test/java/com/villaging/ai/tools/BuildToolsTest.java`; the existing `GuideContentTest` and `SystemPromptTest` must stay green.

**Interfaces:**
- Produces tool definitions (argument names exact):
  | Tool | Required | Optional |
  |---|---|---|
  | `design_blueprint` | `name`, `plan` (string holding the shape plan JSON) | |
  | `use_template` | `name`, `template` (enum from `Templates.ids()`) | `width`, `depth`, `height` (integers), `style` (enum), `roof` (enum) |
  | `list_blueprints` | | |
  | `show_blueprint` | `name`, `layer` (integer) | |
  | `plan_build` | `name`, `x`, `y`, `z` (integers) | `rotation` (enum "0","90","180","270"), `stockpile_x`, `stockpile_y`, `stockpile_z` (integers) |
  | `start_build` | | |
  | `build_status` | | |
  | `stop_build` | | |
  | `cancel_build` | | |

- [ ] **Step 1: Write the failing tests**

```java
@Test void everyNewToolIsRegisteredWithItsRequiredArguments() { /* names present in Tools.all() */ }
@Test void designBlueprintNeedsANameAndAPlanString() {
    assertEquals(Optional.empty(), validate("design_blueprint", "{\"name\":\"hut\",\"plan\":\"{}\"}"));
    assertTrue(validate("design_blueprint", "{\"name\":\"Bad Name\",\"plan\":\"{}\"}").isPresent());
    assertTrue(validate("design_blueprint", "{\"name\":\"hut\"}").isPresent());
    assertTrue(validate("design_blueprint", "{\"name\":\"hut\",\"plan\":\"" + "x".repeat(20001) + "\"}").isPresent());   // plan text longer than 20,000 characters
}
@Test void useTemplateOnlyAcceptsKnownTemplatesStylesAndRoofs() { /* cottage ok; igloo, pink, spire rejected; width 0 or 100 rejected */ }
@Test void planBuildWantsWholeNumberCoordinatesWithinRangeAndAKnownRotation() { /* x,y,z integers within range of the king via ToolSchema.coordinates; rotation 45 rejected; stockpile coordinates all-or-none */ }
@Test void buildToolsWithoutArgumentsAcceptAnEmptyObject() { }
@Test void theBuildingToolsAreExplainedInThePromptAndGuide() { /* SystemPrompt.build(...) contains "plan_build" and "start_build"; GuideContent text contains every new tool name */ }
```

- [ ] **Step 2: Run to verify failure.** Expected: tools missing.
- [ ] **Step 3: Implement `BuildTools.register()`** following `PeopleTools`/`ContainerTools` (use `ToolSchema.prop`, `enumProp`, `stringNonBlank`, `intIn`, `oneOf`, `coordinates`). Add to `AbilityTools.registerAll()`. The prompt's BUILDING section (kept short): prefer `use_template` for common structures, `design_blueprint` for anything else with simple shapes (give the plan JSON shape and one example), always `plan_build` first and tell the king what is missing in plain words, gather or craft with the normal tools, then `start_build`, never say it cannot build and fix a failing plan instead. Guide page "Building" lists the tools and one sentence on the stockpile chest.
- [ ] **Step 4: Run tests.** Run: `./gradlew build --offline -q`. Expected: no output.
- [ ] **Step 5: Commit** "Add the building tools, prompt section and guide page".

---

### Task 9: Minecraft glue: job data, storage, block states, stockpile, planning tools

**Files:**
- Create: `src/main/java/com/villaging/build/BuildJob.java`, `BuildJobs.java`, `Blocks.java`, `Stockpile.java`, `BlueprintFiles.java`; `src/main/java/com/villaging/villager/BuildHandlers.java`
- Modify: `src/main/java/com/villaging/villager/ToolHandlers.java` (`registerAbilities` calls `BuildHandlers.register()`), `src/main/java/com/villaging/Villaging.java` (force `BuildJobs` class init next to `VillagerBrains.init()`)
- Test: `src/test/java/com/villaging/build/BuildJobTest.java` (pure: the job record and its codec use only ints and strings)

**Interfaces:**
- Consumes: Tasks 1 to 8.
- Produces:
  ```java
  public record BuildJob(String blueprint, int x, int y, int z, int rotation, java.util.Optional<int[]> stockpile, int done, int total, Status status, String note) {
      public enum Status { PLANNED, RUNNING, PAUSED_MISSING_MATERIAL, PAUSED_BLOCKED, PAUSED_BY_KING }
      public static final com.mojang.serialization.Codec<BuildJob> CODEC;
      public BuildJob withDone(int done);  public BuildJob withStatus(Status s, String note);
  }
  public final class BuildJobs {
      public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<BuildJob> ATTACHMENT;   // persistent, Identifier "villaging:build_job"
      public static java.util.Optional<BuildJob> get(net.minecraft.world.entity.PathfinderMob helper);
      public static void set(net.minecraft.world.entity.PathfinderMob helper, BuildJob job);  public static void clear(net.minecraft.world.entity.PathfinderMob helper);
  }
  public final class BlueprintFiles {   // the world's <world>/villaging/blueprints folder and its limits from VillagingConfig
      public static java.nio.file.Path dir(net.minecraft.server.MinecraftServer server);
  }
  public final class Blocks {           // parse and place states
      public static java.util.Optional<net.minecraft.world.level.block.state.BlockState> parse(net.minecraft.server.level.ServerLevel level, String state);
      public static net.minecraft.world.level.block.state.BlockState rotate(net.minecraft.world.level.block.state.BlockState s, int quarterTurns);   // uses BlockState.rotate(Rotation)
  }
  public final class Stockpile {        // chest contents at a position (double chests combined), item id -> count; empty map when there is no container
      public static java.util.Map<String,Integer> read(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos);
  }
  ```
  `BuildHandlers` registers handlers for `design_blueprint`, `use_template`, `list_blueprints`, `show_blueprint`, `plan_build`. `plan_build` loads the blueprint, applies the rotation, checks the site (counts non-air, non-replaceable cells and cells in water), reads the helper's inventory (`VillagerInventories.get(villager)`), reads the stockpile at the given position or the nearest chest within 8 blocks of the anchor, builds a `MaterialPlanner.Report`, saves a `PLANNED` job on the helper and returns a result like `ok: 312 blocks, need 62 stone more (carrying 100, chest has 150), site: 3 cells blocked, say start_build to go`. Verify `BlockStateParser.parseForBlock(HolderLookup<Block>, String, boolean)` with `javap` before using it.

- [ ] **Step 1: Write the failing tests** (pure):

```java
@Test void jobCodecRoundTripsEveryField() { /* encode with JsonOps and decode: equal, with and without a stockpile */ }
@Test void withDoneAndWithStatusReturnUpdatedCopies() { }
@Test void aJobSavedAsRunningIsDemotedToPausedWhenReloaded() { assertEquals(Status.PAUSED_BY_KING, BuildJob.afterReload(runningJob).status()); }
```
  Add `static BuildJob afterReload(BuildJob)` (ruling R9) to the interface.
- [ ] **Step 2: Run to verify failure.** Expected: `cannot find symbol BuildJob`.
- [ ] **Step 3: Implement** the classes and `BuildHandlers` (enforce `maxSavedBlueprints` on save and the limits in `config.build()`). Handlers return plain-English results; failures start with `failed:`.
- [ ] **Step 4: Run tests.** Run: `./gradlew build --offline -q`. Expected: no output. Then run the planning tools once in the real game: `VILLAGING_ONLY="blueprint planning" ./gradlew runClientGameTest --offline` after Task 11 adds the check.
- [ ] **Step 5: Commit** "Add build jobs, storage glue and the planning tools".

---

### Task 10: The build engine and the start, stop, status, cancel tools

**Files:**
- Create: `src/main/java/com/villaging/build/BuildAction.java`, `BuildRegistry.java`
- Modify: `src/main/java/com/villaging/villager/BuildHandlers.java` (add `start_build`, `build_status`, `stop_build`, `cancel_build`), `src/main/java/com/villaging/villager/ToolHandlers.java` (the existing `stop` tool also pauses a running build: it already replaces the action, so `BuildAction.cancel` saves `PAUSED_BY_KING`), `Villaging.java` (on `ENTITY_LOAD` of a helper with a saved job apply `BuildJob.afterReload`; clear jobs on `SERVER_STOPPING`)
- Test: `src/test/java/com/villaging/build/BuildEngineTest.java` (pure state machine via an interface, see below)

**Interfaces:**
- Consumes: `BuildJob`, `BuildOrder`, `MaterialPlanner`, `PlaceBlockAction`, `ContainerAction(Mode.TAKE, ...)`, `AskService.confirmThen`, `VillagingRuntime.handleAnnouncement`.
- Produces: to keep the engine unit-testable, the decision logic is a pure class:
  ```java
  public final class BuildEngine {            // pure, in com.villaging.build, no Minecraft imports
      public interface World { boolean isAir(int x,int y,int z); boolean matches(int x,int y,int z,String state); boolean supported(int x,int y,int z); boolean mayPlace(int x,int y,int z); }
      public interface Items { int have(String item); boolean take(String item, int n); int stockpile(String item); }
      public enum Step { PLACE, SKIP_DONE, SKIP_BLOCKED, FETCH, PAUSE_MISSING, FINISHED }
      public record Next(Step step, int cellIndex, String item, int count, String note) {}
      public BuildEngine(java.util.List<Cell> orderedCells, Blueprint rotatedBlueprint, int anchorX, int anchorY, int anchorZ);
      public Next next(World w, Items items);          // decides the step for the cell at the current index
      public void placed();  public void blocked(String why);   public int done();  public int blockedCount();  public java.util.List<String> blockedNotes();
      public void startAt(int done);                  // resume: continue at the saved count (finished cells are re-checked and skipped as SKIP_DONE)
  }
  ```
  `BuildAction implements VillagerAction` (`isContinuous()` true, ruling R1) drives one cell at a time: `PLACE` runs an inner `PlaceBlockAction(item, pos, kingId, config)` until done, then applies the state fix-up (R4) with `level.setBlock(pos, wanted, 3)`; `FETCH` runs an inner `ContainerAction(TAKE, stockpilePos, item, min(64, remaining), ...)`; `PAUSE_MISSING` saves `PAUSED_MISSING_MATERIAL`, says what is short and, once per pause, calls `handleAnnouncement` with the synthetic message; a wait of `config.build().buildTicksPerBlock()` ticks follows each placement; every 25 blocks it saves `done`; it speaks progress at 25, 50, 75 and 100 percent. `BuildRegistry` counts active builds and refuses a start above `maxActiveBuilds`. `start_build` asks the king with `confirmThen` when the blueprint has more than `buildAskThreshold` cells or the site had blocked cells, with a question like `Build stone_cottage (312 blocks, 62 stone short, 3 cells blocked)?`.

- [ ] **Step 1: Write the failing tests** with fake `World` and `Items`:

```java
@Test void placesEveryCellInOrderAndFinishes() { /* 5 cells, plenty of items: PLACE x5 then FINISHED, done()==5 */ }
@Test void cellsThatAlreadyMatchAreSkippedSoAResumeNeverReplacesThem() { /* world.matches true for cells 0-2: SKIP_DONE x3 first, done counts them */ }
@Test void anOccupiedCellIsBlockedWithANoteNotBroken() { /* world not air and not matching: SKIP_BLOCKED, blockedCount 1, note mentions the position */ }
@Test void anUnsupportedCellIsBlockedAndAProtectedCellIsToo() { }
@Test void whenItemsAreMissingItFetchesFromTheStockpileFirst() { /* have 0, stockpile 64: FETCH(item, 64) */ }
@Test void whenNothingIsLeftAnywhereItPausesNamingTheItem() { /* PAUSE_MISSING, note "I need 12 more minecraft:stone" */ }
@Test void aResumedEngineStartsFromTheSavedCount() { /* new BuildEngine(...).startAt(done) continues at cell done */ }
@Test void blockedCellsAreRetriedOnlyOnceAtTheEnd() { /* an unsupported cell becomes supported after its neighbour is placed: placed on the second pass, never a third */ }
```

- [ ] **Step 2: Run to verify failure.** Expected: `cannot find symbol BuildEngine`.
- [ ] **Step 3: Implement `BuildEngine`** (pure) then `BuildAction`, `BuildRegistry` and the four handlers. Finishing reports `ok` text through speech: `Done: 305 of 312 blocks placed, 7 blocked (...)`. `stop_build` pauses; `start_build` with a paused or planned job resumes it (ruling R9); `cancel_build` clears the job and leaves placed blocks. Verify the existing `stop` tool path leaves the job `PAUSED_BY_KING`.
- [ ] **Step 4: Run tests.** Run: `./gradlew build --offline -q`. Expected: no output.
- [ ] **Step 5: Commit** "Add the build engine, build action and build tools".

---

### Task 11: In-game checks

**Files:**
- Modify: `src/gametest/java/com/villaging/gametest/VillagingClientGameTest.java` (new `attempt(...)` lines and methods; helpers `smartVillager`, `makeKing`, `resetArena`, `ollama.script(...)` already exist)

**Interfaces:**
- Consumes: everything above; the fake model scripts tool calls with Ollama JSON as in the existing checks.

- [ ] **Step 1: Write the checks** (names are the `attempt` titles, each a separate method):
  1. `blueprint planning: template, plan_build report and the ASCII view`: scripted `use_template` (cottage 5x5x3) then `plan_build` with a chest holding half the stone; the tool results contain the block count and the missing amount; `show_blueprint` layer 0 returns rows.
  2. `blueprint build: a cottage appears block by block and the chest shrinks`: stock the helper and a chest with enough blocks, `start_build`; wait until the helper reports done; every cell of the rotated blueprint equals the wanted block state in the world; items removed from the chest/inventory equal the planned amounts.
  3. `blueprint build: a rotated build puts the door where the plan says`: rotation 90, door facing checked.
  4. `blueprint build: missing stone pauses with a message and resumes after a top-up`: only 20 blocks available; the helper pauses (`BuildJobs.get(...).status()` is `PAUSED_MISSING_MATERIAL`), says what is short, the wake-up request reaches `ollama.requests`; add stone to the chest and `start_build`: finishes with no cell placed twice (count placements through block-change counting or the job's `done`).
  5. `blueprint build: occupied and protected cells are kept and reported`: a stone block in the site stays, the final speech mentions it blocked; with `respectProtection` on and a protected cell (existing test helper for protection) the cell is reported.
  6. `blueprint build: stop pauses and a fresh start resumes without redoing blocks`: stop mid-build, job `PAUSED_BY_KING`, then `start_build` finishes; simulate a reload by clearing the in-memory action and applying `BuildJob.afterReload`.
  7. `blueprint build: a big build asks the king and a no starts nothing`: more than 64 cells, the ask dialog appears (existing test pattern for `AskScreen`), answer No: no cell placed, job stays `PLANNED`.
  8. `blueprint safety: banned blocks and oversized plans are refused with a reason`: `design_blueprint` with TNT and with a 60-wide box return `failed:` texts containing `tnt` and `48`; the helper's next model request contains that text (the AI can fix its plan).
- [ ] **Step 2: Run each new check alone to see it fail, then pass.** Run: `VILLAGING_ONLY="blueprint" ./gradlew runClientGameTest --offline`. Expected: eight `PASS` lines.
- [ ] **Step 3: Run the whole suite.** Run: `./gradlew runClientGameTest --offline > /tmp/gt.txt 2>&1; grep -c "gametest\] PASS" /tmp/gt.txt; grep "gametest\] FAIL" /tmp/gt.txt`. Expected: all checks pass (the known intermittent "smart golems obey orders" check may need one rerun alone; report it if it fails twice).
- [ ] **Step 4: Commit** "Add in-game checks for blueprints".

---

### Task 12: Docs, version 1.1.0 and release build

**Files:**
- Modify: `README.md` (Building section: the tools, the stockpile chest, limits, config table rows for `build.*`), `docs/modrinth/LISTING.md` (1.1.0 section and changelog), `docs/wiki/What-Helpers-Can-Do.md`, `AGENTS.md` (state and next work: blueprints 1.2.0 and 1.3.0), `gradle.properties` (`version=1.1.0`)

- [ ] **Step 1: Write the docs** (what a king says, the nine tools by name, the safety rules, the config table, the limits).
- [ ] **Step 2: Full verification.** Run: `./gradlew clean build --offline -q` then the full in-game suite as in Task 11 Step 3. Expected: no output from the build; all in-game checks pass.
- [ ] **Step 3: Check the jar.** Run: `unzip -p build/libs/villaging-1.1.0.jar fabric.mod.json | grep '"version"'`. Expected: `"version": "1.1.0"`.
- [ ] **Step 4: Commit** "Release 1.1.0: blueprints engine" and tell the owner the jar path; the owner pushes and uploads to Modrinth.
