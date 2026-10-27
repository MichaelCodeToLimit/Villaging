# Villaging sub-project 2: Player abilities

Status: draft for review. Builds on the Foundation (`2026-10-26-villaging-foundation-design.md`).

## Goal

A smart villager can do, with its hands, what a player can do, when the king tells it to in plain language.
The AI chooses from a fixed set of safe tools. It never runs arbitrary code. Building structures
(sub-project 3) and the rank ladder (sub-project 4) are out of scope; this sub-project only lets commanders
relay orders.

## Decisions (from the king)

- All ability groups are designed in this one spec; the plan builds them in stages, each tested in game.
- Villagers act through a hidden fake player (`VillagerHands`), so vanilla player code does the work.
- Each villager has its own saved inventory.
- Long jobs run as safe task scripts: restricted plans made only of the same tools, run by the mod with no
  further model calls.
- Orders come from the king. Commanders may relay orders, but only with the king's approval.
- Villagers never hurt players or pets, whatever they are told.

## 1. VillagerHands (hidden fake player)

- One fake server player per active smart villager, created on demand and discarded when the villager
  unloads or dies. It is never shown to clients, never saved as an entity, and never in the player list.
- Each tick of an action it is moved to the villager's position and facing. Its inventory is the villager's
  inventory (section 2), so there is one source of truth.
- Placing, using items and blocks, attacking, opening containers and crafting call vanilla player code, so
  results and protection events match a real player's.
- Actions are rate-limited to what a player could do (reach distance, cooldowns, attack charge).
- Risk: other mods that watch player events may see the fake player. `respectProtection` (section 5) and a
  list of tools the fake player may use are configurable.

## 2. Inventory

- Each smart villager has a real inventory (36 slots plus armor and hand slots), saved with the villager
  via a data attachment next to `VillagerBrain`.
- Items come from gathering, crafting, pickup and hand-over. Nothing is conjured.
- `check_inventory` tells the AI what it holds.

## 3. Tools

All tools are validated before they run (`ArgValidator` plus an executor check), run on the server thread
through `ActionExecutor`, and return a result string the model reads. Failures return `failed: <reason>`.

- **Look:** `look_around` (nearby blocks, entities, dropped items, containers, within a radius), `look_at`.
- **World:** `mine_block` (exists), `place_block`, `use_block` (doors, levers, buttons, beds, crafting
  stations), `break_area` (bounded box, goes through the risky-act check).
- **Items:** `pick_up`, `drop`, `give_to_king`, `take_from_king` (section 4), `equip`, `use_item`, `eat`,
  `check_inventory`.
- **Containers:** `open_container`, `put_in`, `take_out`.
- **Crafting and processing:** `craft`, `smelt`, `brew`, `enchant`, `anvil`, used on a nearby station.
- **Gathering:** `chop_tree`, `till`, `plant`, `harvest`, `fish`, `shear`, `milk`, `breed`, `tame`.
- **Combat:** `attack` (hostile mobs and targets the king names; never players or pets), `shoot`, `guard`,
  `flee`.
- **Movement:** `go_to`, `follow_me`, `stop` (exist), `sit`, `sleep`.
- **Talk:** `say` (exists), `ask` (section 6).
- A request outside these tools gets an honest "I can't do that yet" and an offer of the closest thing.

## 4. Giving orders and taking items

- The king gives orders. A commander's relayed order runs only after the king approves it through an `ask`
  dialog (section 6).
- A villager takes items from a player's inventory only when the king says so, and only the items named.
  It never does it on its own, and a commander cannot make it. Taking from chests follows the protection
  setting.

## 5. Settings (`config/villaging.json`)

| Option | Default | Meaning |
|---|---|---|
| `respectProtection` | `true` | `true`: spawn protection, protection mods, world border and `actionRange` apply to villagers as they do to players. `false`: those checks are skipped. |
| `allowBreakBedrock` | `false` | Bedrock can only be broken by a creative-mode player in vanilla. `true`: villagers may break bedrock as a creative player can (including other unbreakable blocks such as barriers). `false`: survival rules, so they cannot. Separate from `respectProtection`. |

Never hurting players or pets is not a setting.

## 6. Risky acts and questions

- **Risky acts** (lava, TNT, fire, mass destruction, other items marked risky) always ask the king first.
  The king can switch asking off per villager by telling it ("you don't need to ask about fire"), and back
  on ("ask me again"). A single "yes, this time" also works. The per-villager choice is saved in
  `VillagerBrain`.
- **Questions:** whenever a villager needs an answer, a dialog opens on the king's client with a few
  options and a box for their own answer. This covers risky-act confirmations, commander approvals, and
  ordinary questions ("which chest should I use?").
- `ask(question, options[])` is a tool. The villager pauses until the king answers or cancels; the answer
  is the tool result. A cancel is reported as such; the villager does not proceed with the risky act.
- Network: a clientbound payload opens the dialog (villager id, question, options) and a serverbound payload
  returns the answer. The server only accepts an answer from the king for a question it actually asked,
  once.

## 7. Task scripts

- For a big job the model submits a plan in a restricted format: a list of steps from the tool set above,
  with `repeat n`, `until <condition>` and `if <condition>`. Conditions read facts only (item counts, block
  present, health).
- The mod runs it itself with no further model calls, validates every step before running it, and stops at
  the first failure with a clear reason.
- Limits: maximum steps per script, maximum total run time, maximum nesting. All configurable with safe
  defaults.
- The king stops a script by telling the villager to stop; the model can also stop it. Nothing in a script
  can execute code.
- Risky steps in a script still stop and ask.

## 8. Testing

- The AI layer (tools, validation, scripts, ask flow) stays pure Java and is tested with scripted fake
  models and a fake executor.
- Each ability group gets in-game checks with screenshots via the existing client game tests and fake
  model.
- Specific checks: the fake player never appears to clients and is removed on unload; protection setting
  on and off; bedrock setting on and off; players and pets are never attacked; `take_from_king` only
  works when ordered; the ask dialog accepts options, custom text and cancel; a forged answer packet is
  rejected; a script stops on a failing step.
- All Foundation tests must stay green.

## Out of scope

Structure building and blueprints (sub-project 3). Ranks, gear and stats progression, and chains of command
beyond relaying orders (sub-project 4).
