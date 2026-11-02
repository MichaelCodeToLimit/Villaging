# Villaging

Villagers and golems become **smart AI helpers** that you command in plain words. Say what you want and they get it done. You are the king.

## What it feels like

- **Just talk.** Right-click a smart villager to open a chat. Slang, typos and other languages all work.
- **Make anyone smart.** Sneak and right-click a villager and say anything. It wakes up with a name. The first player to do this in a new world becomes the king. You can also use the **Crown**.
- **Real work, not tricks.** Helpers mine, chop, farm, fish, craft, smelt, brew, fight, guard, carry items and use chests. Long jobs run as step-by-step plans.
- **Builders.** Say "build a stone cottage by the river". A helper designs it (or uses a ready-made cottage, tower, wall, bridge or castle), counts the materials, asks you, and builds it block by block. If it runs short, it tells you exactly what it needs.
- **Teams.** Say "make a team called builders with Bob as leader, add Alice and Rex". The leader hands out the work, and the team can build one structure side by side.
- **Recruit.** "Recruit the iron golem": you approve, and up to 5 villagers or golems become smart.
- **Your names.** Rename helpers and choose what they call you ("call me Boss").
- **Safe.** Risky acts (lava, fire, TNT, wrecking big areas) always ask you first. They never hurt players or pets, and they respect spawn protection.
- **Never stuck.** Set a backup AI and helpers switch to it when your main one runs out or goes down.
- **Quick.** Small requests, faster builds, and helpers that keep up. Weapons wear out, Infinity bows and beds work like you expect.

A built-in guide opens the first time you play. Open it again with `/villaging guide`.

## Setup

1. Install Fabric Loader 0.20.1 or newer, and **Fabric API**.
2. Put the mod in your `mods` folder and join a world.
3. A box asks for your AI key (Claude, Gemini, or free local Ollama). Type `/villaging key` to open it again.

Needs Minecraft 26.2 and Java 25. Install it on both the client and the server.

## About the AI and your key

Villagers think using an AI service that you choose. Your messages, and what a villager can see, are sent to that service. Your key is saved in `config/villaging.json` on the computer or server running the world, so never share that file. On a multiplayer server only the owner or an admin can set it. Ollama runs on your own machine and sends nothing online.

## Coming later

Saving and sharing your own builds, previews and nicer designs, and a server plugin.

Questions or ideas? Join the Discord: https://discord.gg/V8zF3tuWW
