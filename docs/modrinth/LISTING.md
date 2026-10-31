# Modrinth listing for Villaging (ready to paste)

Nothing here has been published. Create the project at https://modrinth.com/dashboard/projects ("Create a project"),
then copy these fields.

## Project

| Field | Value |
|---|---|
| Name | Villaging |
| URL slug | `villaging` |
| Summary (max 256) | Turn villagers and golems into smart AI helpers you command in plain words. You are the king: they mine, build, farm, craft, fight, recruit more helpers and answer you in a full chat. |
| Project type | Mod |
| Loader | Fabric |
| Game version | 26.2 |
| Client / server | Required on both (the game logic runs on the server, the chat screens on the client) |
| Categories | Adventure, Mobs, Utility |
| License | MIT (the `LICENSE` file and `fabric.mod.json` both say MIT now; choose "MIT License" on Modrinth) |
| Icon | `src/main/resources/assets/villaging/icon.png` |
| Source link | add your repository URL if you publish the code |

## Description (Markdown)

```markdown
# Villaging

Villagers (and iron and copper golems) become **smart helpers** that you command in plain words. You are the king.

## What you can do
- **Talk normally.** Right-click a smart villager for a full chat that stays open. Slang, typos and other languages work.
  The villager stands still while you talk.
- **Any villager can join.** Sneak + right-click an ordinary villager and say anything: it becomes smart and gets a name.
  The first person to do this in a new world becomes king. Or use the Crown (in the creative inventory, or craft it).
- **They do real work:** mine, place, break areas, chop trees, farm, fish, craft, smelt, brew, fight, guard, carry
  items, work with chests, and run long jobs as safe step-by-step plans.
- **Recruit more:** "recruit the iron golem". You approve, then up to 5 villagers or golems become smart.
- **Rename them** and choose what they call you ("call me Boss").
- **Safe by design:** risky acts (lava, fire, TNT, wrecking big areas) always ask you first in a pop-up. They never hurt
  players or pets, and they respect spawn protection and protection mods.
- A built-in **guide** opens the first time you play (`/villaging guide`).

## Setup
1. Install Fabric Loader 0.20.1+ and **Fabric API**.
2. Join a world. A box asks for your AI key (Claude, Gemini, or free local Ollama). `/villaging key` opens it again.

## About the AI and your key
Villagers think using an AI service you choose. Your messages and what a villager sees are sent to that service. The key
is stored in `config/villaging.json` on the computer or server running the world; never share that file. On a
multiplayer server only the owner or an admin can set it. Ollama runs on your own machine and sends nothing online.

## Coming later
Saving your own builds as blueprints and a server plugin.
```

## Version

| Field | Value |
|---|---|
| Version number | 1.0.0 |
| Release channel | Release (or Beta for a first upload) |
| File | `build/libs/villaging-1.0.0.jar` (do not upload the `-sources` jar as the primary file) |
| Loaders | Fabric |
| Game versions | 26.2 |
| Dependency | Fabric API: required |

### Changelog

```markdown
First release.
- Smart villagers, iron golems and copper golems you command in plain words.
- Full chat screen; the villager stands still while you talk.
- In-game AI key box (`/villaging key`) and a first-time guide (`/villaging guide`).
- Recruit, rename, and custom king title.
- Crown in the creative inventory.
```

## Version 1.0.1 (update)

File: `build/libs/villaging-1.0.1.jar`. Same loader, game version and Fabric API dependency as 1.0.0.

```markdown
Fixes and improvements.
- Select and copy text in the villager chat: drag to select, double-click a word, triple-click a message, Ctrl+C to copy, right-click to copy a whole message. Ctrl+V pastes into the box.
- A villager's words are now saved as soon as they are said, so closing and reopening the chat keeps the whole conversation.
- Villagers answer faster: they say they are thinking after 3 seconds, retry failed AI calls sooner, and skip an extra AI round when they only talk.
- Clear messages when the AI service has run out of free usage (quota) or rejects your key, instead of retrying for a minute.
```

## Version 1.0.2 (update)

File: `build/libs/villaging-1.0.2.jar`. Same loader, game version and Fabric API dependency.

```markdown
New commands.
- `/announcements <message>` speaks to every smart villager and golem within 64 blocks at once (up to 12, asked one after another so the AI service is not flooded). King only.
- `/villaging summon <name>` brings one helper to your side and stops what it was doing. A start of the name is enough, and it needs no AI. King only.
```

## Version 1.0.3 (update)

File: `build/libs/villaging-1.0.3.jar`. Same loader, game version and Fabric API dependency.

```markdown
Backup AI and a warmer local model.
- Ollama now keeps your local model loaded for 30 minutes between messages, so only the first answer is slow.
- New optional setting `fallbackProvider` in `config/villaging.json` (`ollama`, `gemini` or `cloud`). When your main AI runs out of free usage, is overloaded or unreachable, villagers switch to the backup automatically. The main AI rests for a minute after a failure so messages are not delayed. Off by default.
```

## Version 1.1.0 (update)

File: `build/libs/villaging-1.1.0.jar`. Same loader, game version and Fabric API dependency.

```markdown
Building from blueprints.
- Tell a helper to build: "build a stone cottage by the river". It designs the plan (or uses a ready-made cottage, tower, wall, bridge or castle), counts the materials against what it carries and a chest, asks you, and builds it block by block from the bottom up.
- If it runs out of a material it stops, says exactly what it needs ("I need 12 more stone"), and carries on when you say start again.
- Nothing is forced: occupied or protected spots are kept and reported. Big builds ask you first. Builds survive a restart.
- New tools: use_template, design_blueprint, list_blueprints, show_blueprint, plan_build, start_build, stop_build, cancel_build, build_status.
- Helpers now sneak while placing blocks, so a block placed next to a door or chest is built against it and does not open it.
- New "build" settings in config/villaging.json (speed, ask limit, builds at once, limits).
```

## Version 1.2.0 (update)

File: `build/libs/villaging-1.2.0.jar`. Same loader, game version and Fabric API dependency.

```markdown
Teams.
- Group helpers into a team with one leader: "make a team called builders with Bob as leader, add Alice and Rex". Also `/villaging team list|create|add|remove|disband` (king only, no AI needed).
- Tell the leader a job and its AI hands out the work. `assign` passes a job to one member; `assign_build` splits a planned build into vertical slabs so the members build the same structure side by side. `team_status` shows what each is doing.
- Builds beyond the "builds at once" limit wait and start by themselves when one finishes.
- A helper is in one team at a time; a team needs two or more helpers to take jobs. New `teams.maxSize` setting in config/villaging.json (default 5, 2 to 20).
- If the leader dies the team ends; if a member dies the leader says so.
```

## Before you upload (checklist)
- [x] License: MIT everywhere (done).
- [x] Automated checks: unit tests and the in-game suite (65 checks) pass on the release jar's code.
- [ ] Play-test the jar once with your own key.
- [ ] Add screenshots in the gallery (the chat window, the guide, a golem at work) once you have taken them.
- [ ] Upload the sources jar as a secondary file if you want it (optional).
