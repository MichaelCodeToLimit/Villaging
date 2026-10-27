package com.villaging.villager;

import com.villaging.ai.PendingQuestions;
import com.villaging.net.AskPayload;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
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

	/**
	 * Asks a question and turns the answer into the tool's result. The returned future owns the question: cancelling it
	 * (a newer command arrives) closes the dialog's question, so a later click cannot trigger the action.
	 */
	public static CompletableFuture<String> askThen(ToolContext ctx, String question, List<String> options,
			Function<String, String> next) {
		CompletableFuture<String> answer = ask(ctx, question, options);
		CompletableFuture<String> result = new CompletableFuture<>();
		answer.whenComplete((a, error) -> {
			if (result.isDone()) {
				return;
			}
			try {
				result.complete(next.apply(error == null ? a : PendingQuestions.NO_ANSWER));
			} catch (RuntimeException e) {
				result.completeExceptionally(e);
			}
		});
		result.whenComplete((value, error) -> {
			if (result.isCancelled()) {
				answer.cancel(true);
			}
		});
		return result;
	}

	/** Asks a yes/no question; {@code next} gets true only for an explicit yes. */
	public static CompletableFuture<String> confirmThen(ToolContext ctx, String question, Function<Boolean, String> next) {
		return askThen(ctx, question, List.of("Yes", "No"), answer -> next.apply(answer.equalsIgnoreCase("yes")));
	}
}
