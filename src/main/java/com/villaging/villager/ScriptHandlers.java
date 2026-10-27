package com.villaging.villager;

import com.villaging.ai.script.Script;
import com.villaging.ai.script.ScriptException;
import com.villaging.ai.tools.ScriptTools;
import java.util.concurrent.CompletableFuture;

/** Handlers for running and stopping task scripts. */
final class ScriptHandlers {
	private ScriptHandlers() {
	}

	static void register() {
		ToolHandlers.register("run_script", (ctx, args) -> {
			Script script;
			try {
				script = ScriptTools.parse(args.get("script").getAsString(), ctx.config().maxScriptDepth());
			} catch (ScriptException e) {
				return CompletableFuture.completedFuture("failed: " + e.getMessage());
			}
			return ScriptManager.INSTANCE.start(ctx, script);
		});
		ToolHandlers.register("stop_script", (ctx, args) -> CompletableFuture.completedFuture(
				ScriptManager.INSTANCE.cancel(ctx.villager()) ? "ok: script stopped" : "ok: no script was running"));
	}
}
