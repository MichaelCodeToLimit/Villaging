# Villaging: Building from Blueprints (sub-project 3) - Design

Status: draft for the owner's review. Nothing in this document is built yet.
Builds on: the Foundation and Abilities specs in this folder (`place_block`, `break_area`, `craft`, scripts, the ask dialog, protection).

## 1. Goal

The king says "build a small stone castle with two towers" (or "build the cottage here", or "copy my tower over there") and one smart
helper does the whole job: it works out a plan, checks the materials, asks the king before a big job, then physically places the
blocks from the bottom up, where the king can watch. The AI decides **what** to build and **where**; the game places the blocks.

Success looks like:
- A built-in cottage appears, block by block, in under a few minutes of game time, with the AI making only a handful of model calls.
- A helper that runs short of a material stops, says exactly what it needs, and carries on from where it stopped once it has it.
- The king can stop the build at any time, and a build survives a world save or restart.
- Nothing is conjured: every placed block comes from the helper's inventory, a chest the king pointed at, or what the helper gathered or crafted.

## 2. Decisions already made with the owner

| Question | Decision |
|---|---|
| Where do blueprints come from? | All three: the AI designs from the king's words, built-in designs the AI adjusts, and the king's own saved builds / schematic files. |
| Where do materials come from? | Stockpile first (carried items, then a chest the king points at), then gather or craft what is missing. |
| Teamwork? | One builder per job in this feature. Splitting work among gatherers and builders comes with the Ranks sub-project. |
| How is it built? | A real build engine in Java. The AI does not place blocks one call at a time and does not write per-build scripts. |

## 3. Non-goals (for this sub-project)

- Several helpers building one structure at once (Ranks).
- Litematica (`.litematic`) and Sponge (`.schem`) schematic files. Only vanilla `.nbt` structure files are read and written.
- Building in unloaded chunks, across dimensions, or underwater/in lava without the king's approval.
- Furniture, redstone logic, or block entity contents (chest items, sign text, banner patterns) beyond the block state itself.
- Replacing or breaking existing blocks by default. Clearing a site is a separate, explicit step (section 8).

## 4. Concepts and data

### 4.1 Blueprint (pure Java, `blueprint/Blueprint`)

```
Blueprint {
  String name            // lowercase id, e.g. "stone_cottage"; unique per world
  int sizeX, sizeY, sizeZ   // bounding box, each 1..MAX_SIDE (48), sizeY up to 64
  Map<Character,String> palette  // 'S' -> "minecraft:stone_bricks[...]" (block id with optional state properties)
  List<Cell> cells       // only non-air cells: (x, y, z, paletteKey), relative to the blueprint's minimum corner
  String origin          // "ai", "template:<id>", "captured", "imported"
}
```

Rules enforced when a blueprint is created (all pure logic, unit tested):
- At most `maxBlueprintBlocks` cells (default 20,000) and the side limits above.
- Every palette entry is a block that exists and is **placeable by a helper**: not in the deny list (`tnt`, `lava`, `fire`, `bedrock`,
  `barrier`, `command_block*`, `structure_block`, `jigsaw`, `spawner`, `end_portal*`, `nether_portal`, `respawn_anchor`, `water`
  and other fluids, `piston_head` and `moving_piston`).
- Block states (facing, half, waterlogged...) are kept as strings and validated against the block's real properties at build time.

### 4.2 Operations on a blueprint (pure Java)

- `rotate(quarterTurns)` and `mirror(axis)`: states with `facing`/`axis` are transformed too.
- `materials()`: a map of item id to count (e.g. `minecraft:stone_bricks` -> 312). Block-to-item mapping covers double slabs (2 slab items for one cell), doors, beds and tall plants (1 item for 2 cells),
  and blocks that drop something else when broken (the planner counts the item that places the block, never the item the block drops).
- `order()`: the build order, bottom layer first; within a layer, supports and full blocks before attachments (torches, doors, buttons,
  ladders, signs) and before blocks that need a neighbour (stairs, slabs are fine); from the nearest corner outward to minimise walking.

### 4.3 Shape plan (pure Java, `blueprint/ShapePlan`)

The AI does not list blocks. It sends a **shape plan**: a small JSON list of primitives that the mod compiles into a Blueprint.

Primitives: `box` (solid), `hollow_box` (walls + optional floor/roof), `floor`, `walls`, `roof_gable`, `roof_flat`, `roof_pyramid`,
`cylinder` (solid/hollow), `line`, `stairs_run`, `door`, `window`, `fill_palette_pattern` (checker/stripe). Each has integer coordinates
relative to the plan's origin, a palette key, and optional state. A plan has at most 60 primitives. Overlaps resolve in list order
(later wins). Compilation fails with a precise message ("primitive 3: x range 0..60 is wider than 48") so the model can fix it.

### 4.4 Built-in templates (pure Java, `blueprint/Templates`)

Parameterised generators that return a ShapePlan: `cottage`, `tower`, `wall`, `bridge`, `castle` (keep + curtain wall + two towers),
`gatehouse`. Parameters: `width`, `depth`, `height` (each bounded), `style` (`stone`, `wood`, `brick`, `sandstone`, `dark`), and `roof`
(`gable`, `flat`, `pyramid`). Every template has unit tests that check it compiles within limits for the whole parameter range, is
enclosed (a flood fill from outside cannot reach the interior except through the door), and has a door and at least one light source.

### 4.5 Capture and import (Minecraft layer, step 2)

- `capture_area(x1,y1,z1,x2,y2,z2,name)`: records an existing region as a Blueprint (air skipped, deny-listed blocks replaced by air with
  a warning, block entities recorded as their block only).
- `.nbt` import/export through the game's own `StructureTemplate` reader/writer, from/to `<world>/villaging/structures/<name>.nbt`.

### 4.6 Storage

Blueprints live as JSON files in `<world>/villaging/blueprints/<name>.json` (palette + run-length encoded layers, so a 20,000-block
blueprint stays well under 1 MB). A world can hold at most `maxSavedBlueprints` (default 50); the oldest unused one is offered for
deletion when the limit is reached.

## 5. How the king talks to it (AI tools)

All new tools are registered like the existing ones (`Tools.register`) with a JSON schema, argument validation that returns a precise
error string, and an entry in the in-game guide. Tool names (new):

| Tool | Does |
|---|---|
| `design_blueprint` | Takes a name and a shape plan; compiles and saves a blueprint; returns size, block count and the material list. |
| `use_template` | Takes a template id and parameters; saves the blueprint; returns the same summary. |
| `capture_area` | (Step 2) Saves an existing region as a blueprint. |
| `list_blueprints` | Names, sizes, block counts. |
| `show_blueprint` | Returns a small ASCII layer map (one layer at a time, at most 24x24) so the AI can check its own design; in step 3 also shows an outline. |
| `plan_build` | Takes blueprint, anchor position (x,y,z), rotation, and optional stockpile chest position. Checks the site and the materials and returns a report: blocks needed, what the helper carries, what the stockpile holds, what is missing, any obstructed cells. Starts nothing. |
| `start_build` | Starts the job from the last plan (needs the king's yes for big jobs, section 8). |
| `build_status` | Percent done, current layer, what is missing, estimated time. |
| `stop_build` | Pauses the job (the existing `stop` also pauses it). The job stays saved and `start_build` with no arguments resumes it. |
| `cancel_build` | Ends and forgets the job. Placed blocks stay. |

The system prompt gets a short BUILDING section: use `use_template` for common structures, `design_blueprint` for anything else
(keep plans simple: boxes, hollow boxes, roofs), always call `plan_build` first, report what is missing in plain words, gather or
craft it with the normal tools, then `start_build`. It must never say it cannot build; if a plan fails validation it fixes the plan.

## 6. The build engine (Minecraft layer, `build/BuildJob`, `build/BuildAction`)

One `BuildJob` per helper, stored in the helper's persisted data next to its `VillagerBrain` (a new codec field `build`, optional and
defaulting to none, so old worlds load unchanged). It holds: blueprint name, anchor, rotation, the ordered cell list index
(`done` count), the stockpile position, and a status (`RUNNING`, `PAUSED_MISSING_MATERIAL`, `PAUSED_BLOCKED`, `PAUSED_BY_KING`).

`BuildAction` is a `VillagerAction` (the existing tick-based action interface, so the existing `ActionManager` pause/stop, mixin AI
pausing and `stop` behaviour all apply). Each placement cycle:

1. Take the next unplaced cell in `order()`.
2. If the cell already holds the right block state, count it as done and continue (this is what makes resuming cheap and safe).
3. Pick a standing spot within reach of the cell (using the existing `Approach` walking helper) and walk there. Reach is `PlaceBlockAction.REACH`
   (4.5). If no path exists after the existing timeout, mark the cell `BLOCKED`, skip it, and keep going; blocked cells are reported at the end.
4. Get the item: from the helper's inventory; else walk to the stockpile chest and take what the next batch (up to 64 per item) needs;
   else pause with status `PAUSED_MISSING_MATERIAL` and a message naming the item and how many are short.
5. Place the block through the same code path as `place_block` (`Protection.blockProblem`, hands, fake player, sound, swing) so protection mods and
   spawn protection apply exactly as before. Honour block state (facing/half) by placing against a neighbour with the right hit result.
6. Wait `buildTicksPerBlock` (config, default 6 ticks, i.e. about 3 blocks per second) then repeat.

Every 25 blocks the job saves its progress. The helper speaks a short progress line at 25%, 50%, 75% and 100% and on any pause.
A pause by missing material wakes the AI: the engine sends the helper one synthetic king-less system message ("the build needs 64
cobblestone; gather or craft it, then say start_build") so the loop continues without the king retyping. At most one such wake-up per
pause, and none if the king's AI is unusable.

If the helper dies, the job stays saved against its UUID and is discarded after one in-game day if the helper does not return.

## 7. Materials

- `plan_build` reads three sources: the helper's inventory, the stockpile chest (any single container the king names by position, or the
  nearest chest within 8 blocks of the anchor if none is named), and nothing else. It never reads other players' containers.
- Missing materials are returned as `{item, needed, have, short}` rows so the AI can pick the right existing tool (`chop_tree`, `mine_block`,
  `craft`, `smelt`, `take_out`).
- Substitution: the planner never swaps materials silently. It may offer, in the report, a same-family substitute (e.g. spruce planks for
  oak) that the AI can accept by editing the palette and re-planning.
- Everything the helper takes from the chest is a real transfer, so the chest ends up with fewer items.

## 8. Safety, limits and approval

- Size limit per blueprint (section 4.1) and per world (`maxSavedBlueprints`). Builds only inside loaded chunks and within
  `actionRange` of the king at start (anchors farther away are refused with a clear message; the helper walks as far as needed once started).
- **Asking first:** a build asks the king (the existing ask dialog) when it has more than `buildAskThreshold` blocks (default 64, the same
  number the mass-break rule uses), when the site is not empty, or when any cell is in water or near lava. The dialog states the name,
  block count, materials short, and whether blocks will be replaced. A king who said "stop asking" for `mass_destruction` is not asked
  about size, but is always asked about replacing existing blocks.
- **Site not empty:** by default existing non-air, non-replaceable blocks are **kept** and the cell is reported as `BLOCKED`. The king can
  answer "clear it first" in the dialog; then the engine runs the existing `break_area` machinery (with its own approval rule) for the
  footprint before building. It never breaks blocks silently.
- Protection: every placement goes through `Protection` as today; refused cells are reported, not retried forever.
- Per-world cap: one active build per helper, at most `maxActiveBuilds` (default 3) in the world at once, so one king cannot freeze the server.
- Server load: placements are one block per `buildTicksPerBlock` ticks per job, never a bulk `setBlock` of the whole structure.
- The AI-facing text never contains the king's API key (unchanged rule); blueprint files contain no secrets.

## 9. Configuration (`config/villaging.json`, all optional)

| Option | Default | Meaning |
|---|---|---|
| `maxBlueprintBlocks` | 20000 | largest blueprint |
| `maxSavedBlueprints` | 50 | per world |
| `buildTicksPerBlock` | 6 | build speed (higher is slower) |
| `buildAskThreshold` | 64 | builds bigger than this ask the king first |
| `maxActiveBuilds` | 3 | at once in the world |

## 10. Testing

Pure unit tests (fast, in `src/test`):
- ShapePlan compile: every primitive, overlap order, bounds errors with precise messages, deny-listed blocks rejected.
- Blueprint: size limits, palette validation, `rotate`/`mirror` (4 rotations return the original; stairs/doors face correctly), `materials()` for slabs, doors, beds, `order()` (bottom-up, supports before attachments).
- Templates: for a grid of parameters, compiles within limits, is enclosed, has a door and a light.
- Storage: JSON round trip, run-length encoding of layers, 20,000-block blueprint size, corrupt file handled.
- Material planner: have/short arithmetic, stockpile reading with a fake container, substitution offer.
- BuildJob state machine with a fake world: resume after pause, blocked-cell reporting, progress save points, missing-material pause message.
- Tools: schema + validation for each new tool, and `GuideContentTest` (every registered tool is mentioned in the guide).

In-game checks (real game, `VillagingClientGameTest`, scripted `FakeOllama`):
- `use_template cottage` then `plan_build` then `start_build`: the cottage's blocks exist at the anchor within a time limit, the helper's inventory/chest shrank by the planned amounts.
- A build with too little stone pauses with the right "needs N" message, then resumes after the chest is topped up and finishes.
- A rotated build (90 degrees) puts the door where the plan says.
- A site with an existing block keeps it and reports `BLOCKED`; "clear first" removes it only after a yes.
- Spawn protection refuses a cell and the build reports it.
- `stop` mid-build pauses it; restart (save and reload the world) resumes it.
- Big build asks the king; answering no starts nothing.

## 11. Delivery in three releases

1. **Engine and designs** (`1.1.0`): Blueprint, ShapePlan, Templates, storage, `design_blueprint`, `use_template`, `list_blueprints`, `show_blueprint`, `plan_build`, `start_build`, `build_status`, `stop_build`, `cancel_build`, BuildJob/BuildAction, stockpile, ask rules, config, guide, README, tests.
2. **Your own builds** (`1.2.0`): `capture_area`, `.nbt` import/export, `/villaging blueprint save|load|list|delete <name>` commands.
3. **Preview and polish** (`1.3.0`): outline preview before building (particles along the bounding box and layer), ETA, better progress messages, extra templates.

Each release passes the whole unit suite and the whole in-game suite before it is built, and has its own changelog.

## 12. Risks and open questions

- **AI design quality.** Small local models will produce plain plans. Mitigation: templates cover the common cases; `show_blueprint` lets
  the AI check; compile errors are precise. Expect simple buildings from AI designs.
- **Block state placement** (facing, half, stairs shape, door hinges) is the fiddliest part of the engine; it gets the most in-game checks.
- **Pathing around a half-built structure** can strand the helper. Mitigation: standing spots are chosen outside the footprint when possible,
  and `BLOCKED` cells are skipped, not retried forever.
- **Server mode:** the engine uses only server-side code, so it will carry over to the planned Paper plugin by being moved behind the same
  interfaces; it adds no client packets in step 1.
- Open for the owner: the default `buildTicksPerBlock` (6) and whether a king may be refused a build when the helper's AI quota is empty
  (current answer: yes, a build can *start* only after an AI call, but once started it needs no AI until it pauses).
