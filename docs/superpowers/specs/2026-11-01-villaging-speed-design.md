# Villaging 1.0.4: faster replies, faster tasks, polish

Status: draft for review. Target release: 1.0.4.

## Goal

The first reply from a helper arrives noticeably sooner (the king's main complaint), tasks finish sooner, and the known small defects are fixed. Behaviour stays the same: every tool, rule and safety check keeps working.

## Decisions already made with the king

- 1.0.4 is speed plus polish. Saving/importing own builds is 1.0.5, preview and nicer designs 1.0.6, the Paper plugin is a later separate project.
- The slowness that bothers most is waiting for the first reply (the king plays on Ollama `llama3.1:8b`).
- Approach 1 first (smaller prompt, same tools); approach 2 (send only likely-needed tool groups) only if replies are still slow after measuring.

## What was measured

Per AI request today: system prompt 11,690 characters (about 9,000 of them are the 64 tool descriptions) plus the tool list itself 21,931 characters. About 34,000 characters, roughly 8,500 tokens, of which the tool descriptions are sent twice. Every extra step of a job reads it all again. Building and teams added about 20 tools, so replies slowed with each release.

## Part A: smaller, cache-friendly prompt

- `SystemPrompt` stops listing every tool's name and description (they are already in the tool list the provider sends). It keeps the rules, the people/building/teams guidance, and one short line naming the tool groups.
- The longest tool descriptions are shortened without losing a rule the model needs (for example the risk and ask rules stay).
- Order for cache reuse: all fixed text first, then the situation and king context (the parts that change) last, so a provider that reuses a prefix can reuse it between steps of one job.
- Budget, enforced by a unit test: system prompt plus tool list stays under 55% of today's size (about 18,700 characters) and the fixed part of the prompt does not change between two builds of it for different states.
- Gemini and Claude providers get the same smaller prompt; no provider-specific forks.

## Part B: measure, then trim the replies path

- Add a debug timing line (logger, off unless `debug` is on) for each AI round: request size, time to first byte, total, tool calls. No keys or message text are logged.
- With it, measure on the king's Ollama before and after Part A and record the numbers in the spec's results section and the changelog.
- Cheap wins checked in this order: speak-only answers skip the extra round (already done in 1.0.1, verify), the 3-second "thinking" ack timing, Ollama `num_predict` cap and `num_ctx` sized to the prompt, and not rebuilding the situation text more than once per round.
- If the measured first reply on `llama3.1:8b` is still above 5 seconds warm, design approach 2 as a follow-up inside 1.0.4 with a fallback to all tools when the model asks for an unknown one. Otherwise not built (YAGNI).

## Part C: faster tasks

- Measure per action where time goes (ticks waited per step, walking, the AI wait between steps of a plan). Candidates: gather/mine/chop wait ticks, `buildTicksPerBlock` default (6), walking approach distance, and the gap between plan steps.
- Lower waits only where the measurement shows idle time. Anything tied to safety or placing against a block stays as it is (sneak, door checks, protection checks).
- `buildTicksPerBlock` default goes from 6 to a measured value (target 3) if in-game checks stay green.

## Part D: polish

Each item gets a failing test or in-game check first, then the fix:
- Weapons wear out when they hit (they never do now).
- Infinity bow: shooting with an Infinity bow reports a false failure.
- Bed use: sleeping should be real sleep where the game allows it, not only the fake player lying down.

## Non-goals

- Changing which AI is used, adding streaming to the chat screen, or changing tool names and arguments.
- Approach 2 unless the measurement says it is needed.

## Testing

Unit: prompt size budget; stable fixed prefix; every tool still reachable (the existing guide and tool tests); timing line formatting contains no message text; config defaults. In-game: the whole existing suite stays green (only the two known environment misses allowed), plus checks for weapon wear, the Infinity bow, and bed sleep.

## Release

Version 1.0.4. README, wiki, guide notes, Modrinth changelog (with the measured before/after reply time), AGENTS.md.
