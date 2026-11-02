## Faster helpers and small fixes

**Faster**

- Helpers send the AI about a quarter less text on every request, so answers start sooner (more on a local Ollama model).
- Ollama now gets a proper context size, so it no longer risks cutting off the start of the instructions.
- Builds go twice as fast by default (build.buildTicksPerBlock is now 3; set it back to 6 for the old pace).
- If an AI call is slow, the log now says how long it took and how big it was. Set -Dvillaging.debug=true to log every call.

**Fixed**

- Weapons wear out when helpers fight with them, and a helper tells you when its weapon breaks.
- Shooting with an Infinity bow no longer reports a failed shot.
- Using a bed at night now puts the helper itself to sleep.
