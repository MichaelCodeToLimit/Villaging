# What helpers can do

Just say it normally. Examples:

- "Mine that block", "chop down that tree", "clear a 5 by 5 area"
- "Build a chest here and put the spare wood in it"
- "Craft a pickaxe", "smelt the iron", "brew a healing potion"
- "Farm the wheat", "fish for a while", "breed the cows"
- "Guard me", "follow me", "go to the house", "sleep"
- "Recruit two more villagers", "your name is Garrick", "call me Boss"
- "Build a stone cottage by the river": a helper designs it and builds it block by block
- "Make a team called builders with Bob as leader, add Alice and Rex", then tell Bob "build the castle" and the team builds it together
- Long jobs run as a step-by-step plan; say "stop" to end it.

Safety: lava, fire, TNT and wrecking big areas always ask you first in a pop-up. Helpers never hurt players or pets.

Not yet: enchanting and anvils, saving your own builds as blueprints.

## Commands

- `/announcements <message>`: speak to every helper within 64 blocks at once (up to 12). They answer one after another, 1.5 seconds apart. King only.
- `/villaging summon <name>`: bring one helper to your side. A start of the name is enough. King only, and it needs no AI.
- `/villaging key` opens the AI key box and `/villaging guide` opens the guide.

## Building

Say "build a stone cottage here" (or a tower, wall, bridge or castle) and a helper designs it, counts the materials, asks you, and builds it block by block.

- It uses what it carries and a chest nearby (or one you name). If something runs out it says exactly what it needs and carries on when you say `start_build`.
- Occupied or protected spots are kept and reported; nothing is broken to make room. Big builds ask you first.
- Ready-made designs come in five styles (stone, wood, brick, sandstone, dark). The AI can also draw its own plan from simple shapes.
- You can say stop to pause it and "start building again" to carry on.
- `/villaging team list`, `create <name> <leader>, <member>, ...`, `add <team> <helper>`, `remove <team> <helper>`, `disband <team>`: manage teams. King only, no AI needed. The team size limit is `teams.maxSize` in `config/villaging.json` (default 5).
