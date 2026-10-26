# Villaging — Sub-project 1: Foundation

Date: 2026-10-26
Status: Draft for review

## Vision (whole mod)

A Fabric mod that makes villagers "smart": AI-driven agents that obey the player as their king. The king types free-form commands into a text box; villagers can do what a player can (build, destroy, gather, fight), can be promoted through ranks, and promoted villagers can lead other villagers.

## Design principle: anything a player can do

The goal is that a villager can do anything a player can do. That is not achievable with a fixed list of hand-picked commands, so the design is:

1. **Tools mirror a player's own abilities**, not specific tasks: move, look, mine, place, use item, use/interact with blocks and entities, attack, pick up and drop, manage inventory, craft, smelt, open containers, eat, sleep, ride, trade. If a player can do it with their hands, there is a tool for it.
2. **Tasks are composed, not hard-coded.** "Build a castle" is not a tool. The AI plans it (designs a blueprint, gathers materials, places block after block) using the primitive tools, with a small safe scripting layer for loops and long coordinate lists so big jobs do not need thousands of model calls (sub-projects 2 and 3).
3. **The tool registry is open-ended.** Adding a tool never changes the agent loop, the chat or the prompt rules. Foundation ships five; the rest arrive in sub-projects 2 to 4.
4. **The AI never runs arbitrary code.** The player-equivalent tool set is the boundary, which keeps "anything" safe.
5. **Honest limits.** Until a tool exists the villager says it cannot do that yet and offers the closest thing. This is a temporary gap in the roadmap, not a design limit.

The whole mod is split into four sub-projects, each with its own spec, plan and build:

1. **Foundation** (this document)
2. **Player abilities** — every remaining thing a player's hands can do, as tools: pathfinding, mining, placing, using items and blocks, inventory, crafting, smelting, containers, farming, combat, trading, sleeping, riding, plus the safe scripting layer for long jobs
3. **Building** — blueprints, material gathering, physical block-by-block construction with a speed setting
4. **Ranks and hierarchy** — promotion ladder, gear/stats, titles, chain of command, smarter model at higher rank

## Decisions made so far

- Loader/version: Fabric, targeting Minecraft 26.2 (a Fabric 26.2 profile is already in the user's launcher). Exact Fabric Loader / Fabric API versions to be pinned in the implementation plan. Java 25 is installed.
- AI backend: both a cloud API (e.g. Claude, user-supplied key) and a local model (Ollama), behind one interface, chosen in config.
- Building style (later sub-project): physical, block-by-block, with a speed setting.
- Promotion (later sub-project): rank ladder with new abilities, better gear/stats, chain of command, smarter AI at higher rank.

## Goal of Foundation

Prove the full loop end to end: the king opens a text box on a villager, types a command, an LLM decides what to do through a fixed menu of safe actions, and the villager replies and performs a few simple actions.

### Success criteria

The king can phrase things any way they like (including typos, slang and vague references like "that block") and the villager understands or asks what they meant. In a real world on Minecraft 26.2 the user can: craft a crown, become king, open the chat on a villager, say "follow me" and "mine that block", and the villager answers in character and does both, without noticeable game stutter. Switching config between cloud and Ollama works.

## Scope

In scope:
- Fabric project setup and build
- Smart villager data (persisted)
- King claiming via a crown item
- Chat screen and client/server packets
- AI provider interface with cloud and Ollama implementations
- Agent loop with tool calling
- Actions: `say`, `follow_me`, `stop`, `go_to`, `mine_block`
- Config file, rate limiting, error handling, tests

Out of scope (later sub-projects): building, crafting, farming, combat, inventory management beyond what `mine_block` needs, ranks, hierarchy.

## Components

| Component | Responsibility | Depends on |
|---|---|---|
| **Villager brain** | Per-villager persisted data: smart flag, name, short chat memory (later: rank). | Fabric data attachment / NBT |
| **King system** | Crown item; claims the player as king (stored per world/player). Only the king can command smart villagers. Normal right-click still trades. | Item registry, world save data |
| **Chat screen** | Client UI text box opened by sneak + interact (or crown in hand) on a smart villager. Shows replies as chat and a speech bubble. | Networking |
| **Networking** | Packets: client to server `(villagerId, message)`; server to client `(villagerId, reply text)`. Server validates king + smart flag. | Fabric API networking |
| **AI provider interface** | `complete(messages, tools) -> response (text and/or tool calls)`. Implementations: `CloudProvider`, `OllamaProvider`. | HTTP client, config |
| **Agent loop** | Builds prompt (villager state, recent memory, tool menu), calls provider off the main thread, executes tool calls, feeds results back, repeats until no tool calls or step cap. | Provider, action runner |
| **Action runner** | Validates tool arguments and runs each action as a multi-tick goal on the server main thread; reports success/failure. | Villager AI goals |
| **Config** | Provider choice, API key, model, base URL, step cap, calls/minute, action range. | File on disk |

Each unit is testable independently: the agent loop with a scripted fake provider, providers against a fake HTTP server, actions in game tests.

## Data flow for one command

1. King opens the chat screen on a smart villager and sends a message.
2. Client sends `(villagerId, message)` to the server.
3. Server checks the sender is the king and the villager is smart; appends the message to the villager's memory.
4. Agent loop runs off the main thread: builds the prompt with villager state (position, inventory, nearby blocks and entities), recent memory and the tool menu; calls the provider.
5. The model replies with text and/or tool calls. Text is sent to the client for display as a speech bubble and chat message.
6. Each tool call is queued onto the server main thread as a multi-tick goal. On completion or failure, the result is returned to the loop.
7. The loop ends when the model makes no further tool calls, reaches the step cap (default 20), or the king sends a new command, which cancels the previous plan.

## Tool menu (Foundation)

- `say(text)`
- `follow_me()`
- `stop()`
- `go_to(x, y, z)`
- `mine_block(x, y, z)`

The model can only call these tools; it never runs code in the game. Later sub-projects add tools to this menu.

## Understanding anything the king says

The king never has to use special words or exact commands. The villager must handle:
- Any phrasing: casual talk, slang, typos, other languages, long rambling, one-word orders.
- Vague references: "that block", "here", "over there", "do it again", "the same but bigger". The prompt therefore carries the king's context (position, facing, the block they are looking at, held item) plus the recent conversation memory.
- Plain conversation and questions ("how are you?", "what can you do?"), answered in character with `say` and no action.
- Requests it cannot do yet (for example building, until sub-project 3): it says so honestly, explains what it can do instead, and offers the nearest useful action. It never pretends to have done something it did not.
- Unclear requests: it asks one short clarifying question with `say` instead of guessing something risky or destructive.
- Multi-part orders ("come here, then mine that stone"): it plans them as several tool calls in order.

This is achieved through the system prompt, king context and memory, not through keyword matching. Because the model understands language, adding tools in later sub-projects makes new orders work with no new parsing.

## Error handling and safety

- Network, key or timeout failures: villager says it can't think right now; the real error is logged. At most one retry.
- Malformed or unknown tool calls: an error result is returned to the model so it can correct itself. Arguments are validated before any action runs.
- Actions are limited to a configurable range around the villager/king and respect protected areas. Destructive actions on containers with items require an explicit instruction.
- AI calls never run on the main game thread.
- Per-villager rate limit (calls per minute) to protect the user's API bill.
- Missing config or key: the mod still loads, smart villagers are disabled, and the king is told what to fix.

## Testing

- Unit tests (plain Java): provider interface against a fake HTTP server; tool-call parsing and validation; agent loop with a scripted fake model.
- In-game tests (Fabric game tests): `follow_me`, `go_to`, `mine_block`.
- Manual test on the user's 26.2 profile: crown, chat screen, real API and Ollama.

## Assumptions to confirm

- Singleplayer and multiplayer both work, with commands limited to the king.
- King is claimed by using a crafted crown item.
- API keys live in a local config file, never in the repository.
- Exact opening gesture for the chat screen (sneak + interact) may change during implementation if it conflicts with trading.

## Open items for the implementation plan

- Pin Fabric Loader, Fabric API, Loom and mappings versions for Minecraft 26.2.
- Choose the HTTP/JSON libraries (prefer the JDK's built-in client to avoid dependencies).
- Decide exact crown recipe and the villager "make smart" mechanism (for example, crown used on a villager).
