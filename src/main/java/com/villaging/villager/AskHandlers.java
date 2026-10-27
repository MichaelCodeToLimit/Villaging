package com.villaging.villager;

import com.google.gson.JsonElement;
import com.villaging.ai.PendingQuestions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Handlers for the tools that talk to the king through the question dialog. */
final class AskHandlers {
	private AskHandlers() {
	}

	static void register() {
		ToolHandlers.register("ask", (ctx, args) -> {
			List<String> options = new ArrayList<>();
			JsonElement given = args.get("options");
			if (given != null && given.isJsonArray()) {
				given.getAsJsonArray().forEach(o -> options.add(o.getAsString()));
			}
			return AskService.ask(ctx, args.get("question").getAsString(), options).thenApply(answer ->
					answer.equals(PendingQuestions.NO_ANSWER)
							? "failed: the king did not answer"
							: "ok: the king answered \"" + answer + "\"");
		});
	}
}
