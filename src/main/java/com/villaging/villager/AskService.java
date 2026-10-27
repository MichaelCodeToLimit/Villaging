package com.villaging.villager;

import com.villaging.ai.PendingQuestions;
import com.villaging.net.AskPayload;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/** Puts a question to the king as a pop-up dialog and waits for the answer. Main thread. */
public final class AskService {
	private AskService() {
	}

	/** Completes with the king's answer, or {@link PendingQuestions#NO_ANSWER} if cancelled, unanswered or absent. */
	public static CompletableFuture<String> ask(ToolContext ctx, String question, List<String> options) {
		ServerPlayer king = ctx.server().getPlayerList().getPlayer(ctx.kingId());
		if (king == null) {
			return CompletableFuture.completedFuture(PendingQuestions.NO_ANSWER);
		}
		PendingQuestions.Question q = PendingQuestions.INSTANCE.open(ctx.kingId(), ctx.villager().getId(), question,
				options, System.nanoTime());
		String name = VillagerBrains.get(ctx.villager()).name();
		ServerPlayNetworking.send(king, new AskPayload(q.id(), ctx.villager().getId(), name, question, options));
		q.future().whenComplete((answer, error) -> {
			if (q.future().isCancelled()) {
				PendingQuestions.INSTANCE.cancel(q.id());
			}
		});
		return q.future();
	}

	/** Asks a yes/no question; true only for an explicit yes. */
	public static CompletableFuture<Boolean> confirm(ToolContext ctx, String question) {
		return ask(ctx, question, List.of("Yes", "No")).thenApply(answer -> answer.equalsIgnoreCase("yes"));
	}
}
