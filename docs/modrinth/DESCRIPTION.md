# Villaging

Turn villagers and iron and copper golems into **smart AI helpers** that you command in plain words. You are the king.

## What you can do

- **Talk normally.** Right-click a smart villager to open a full chat that stays open. Slang, typos and other languages work, and the villager stands still while you talk.
- **Make any villager smart.** Sneak + right-click an ordinary villager and say anything. It becomes smart and gets a name. The first person to do this in a new world becomes the king. You can also use the **Crown**, found in the creative inventory or crafted.
- **They do real work.** Mine, place and break blocks, chop trees, farm, fish, craft, smelt, brew, fight, guard, carry items, work with chests, and run long jobs as step-by-step plans.
- **Build for you.** "Build a stone cottage by the river": a helper designs it (or uses a ready-made cottage, tower, wall, bridge or castle), counts the materials against what it carries and a chest, asks you, and builds it block by block. If it runs short it tells you exactly what it needs.
- **Recruit more helpers.** Say "recruit the iron golem". You approve, then up to 5 villagers or golems become smart.
- **Rename them** and choose what they call you ("call me Boss").
- **Safe by design.** Risky acts (lava, fire, TNT, wrecking big areas) always ask you first in a pop-up. They never hurt players or pets, and they respect spawn protection.
- **A built-in guide** opens the first time you play. Open it again with `/villaging guide`.

## Setup

1. Install Fabric Loader 0.20.1 or newer, and **Fabric API**.
2. Put the mod in your `mods` folder and join a world.
3. A box asks for your AI key (Claude, Gemini, or free local Ollama). Type `/villaging key` to open it again.

Needs Minecraft 26.2 and Java 25. Install it on both the client and the server.

## About the AI and your key

Villagers think using an AI service that you choose. Your messages, and what a villager can see, are sent to that service. Your key is saved in `config/villaging.json` on the computer or server running the world, so never share that file. On a multiplayer server only the owner or an admin can set it. Ollama runs on your own machine and sends nothing online.

## Coming later

Saving your own builds as blueprints, ranks (teams of helpers working together), and a server plugin.
