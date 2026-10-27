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
		ToolHandlers.register("set_ask_preference", (ctx, args) -> {
			String category = args.get("category").getAsString();
			boolean ask = args.get("ask").getAsBoolean();
			if (ask) {
				save(ctx, category, false);
				return CompletableFuture.completedFuture("ok: I will ask the king first about " + category + " acts");
			}
			// The model cannot prove the king said so, so switching asking off always needs the king's own yes.
			return AskService.confirm(ctx, "Should " + VillagerBrains.get(ctx.villager()).name()
					+ " stop asking you before " + category + " acts?").thenApply(yes -> {
						if (!yes) {
							return "failed: the king said no";
						}
						save(ctx, category, true);
						return "ok: I will no longer ask the king about " + category + " acts";
					});
		});
	}

	private static void save(ToolContext ctx, String category, boolean askOff) {
		if (!ctx.villager().isRemoved()) {
			VillagerBrains.set(ctx.villager(), VillagerBrains.get(ctx.villager()).withAskOff(category, askOff));
		}
	}
}
