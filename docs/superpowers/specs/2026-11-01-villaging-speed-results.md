# 1.0.4 speed results

Reply seconds were **not measured**: Ollama was not running on the build machine (`http://localhost:11434` gave no answer), so the plan's Task 4 records sizes only. Run a game with `-Dvillaging.debug=true` (or any round slower than 1.5 s) to see `AI round: ... s, ... chars, ... tool calls` lines in the log, and fill the seconds in here.

Text sent to the model per request (instructions + tool names, descriptions and schemas; conversation not counted):

| | Before (1.0.3) | After (1.0.4) |
|---|---|---|
| Instructions | 11,690 chars | about 4,000 chars |
| Tools (64) | about 18,900 chars | about 18,100 chars |
| Total | about 30,500 chars (about 7,600 tokens) | 22,129 chars (about 5,500 tokens) |

That is about 28% less text. The instructions no longer repeat every tool, and the fixed part comes first so a provider can reuse it between steps.

The original 18,700 target is not reachable without removing tool schemas: the schema structure alone is about 12,000 characters. If the measured warm first reply on `llama3.1:8b` is still above 5 seconds, the next step is approach 2 from the spec (send only the tool groups a request is likely to need, falling back to all tools when the model asks for an unknown one).

Side effect worth knowing: Ollama's own default context is far smaller than the old 8,500-token request, so it may have been cutting the start of the instructions. 1.0.4 asks for a 12,288-token context.

## Task idle waits

Nothing to trim. The slowest in-game checks from a full run were mechanics-bound: smelting, fishing and brewing take real game time, and `break_area`, `guard`, `attack` and `chop_tree` take 13 to 22 seconds because of mining speed and walking. No fixed idle wait (a pause that keeps going after the target is reached) was found, so no action timing was changed. The visible gain for tasks is the default build speed (6 to 3 ticks per block).
