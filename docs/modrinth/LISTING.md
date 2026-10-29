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
Building from blueprints and ranks.
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

## Before you upload (checklist)
- [x] License: MIT everywhere (done).
- [x] Automated checks: unit tests and the in-game suite (65 checks) pass on the release jar's code.
- [ ] Play-test the jar once with your own key.
- [ ] Add screenshots in the gallery (the chat window, the guide, a golem at work) once you have taken them.
- [ ] Upload the sources jar as a secondary file if you want it (optional).
