## Building, teams and backup AI

**Build for you**

- Say "build a stone cottage by the river" and a helper designs it, counts the materials and builds it block by block.
- Ready-made designs: cottage, tower, wall, bridge, castle.
- If it runs out of a material it says exactly what it needs and carries on when you say start again.
- Big builds ask you first. Builds survive a restart.

**Teams**

- Say "make a team called builders with Bob as leader, add Alice and Rex".
- Or use /villaging team list, create, add, remove and disband (king only).
- Tell the leader a job and it hands out the work. The team can build one structure side by side.
- Setting: teams.maxSize (default 5).

**Backup AI**

- Optional fallbackProvider in config/villaging.json (ollama, gemini or cloud).
- If your main AI runs out of free usage or is down, villagers switch to the backup.
- Ollama now stays loaded for 30 minutes, so only the first answer is slow.

**Also**

- Helpers sneak while placing blocks, so they do not open doors or chests by accident.
- 17 new tools for building and teams.
