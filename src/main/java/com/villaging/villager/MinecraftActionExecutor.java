package com.villaging.villager;

import com.google.gson.JsonObject;
import com.villaging.ai.ActionExecutor;
import com.villaging.ai.PendingQuestions;
import com.villaging.ai.RiskPolicy;
import com.villaging.ai.ToolCall;
import com.villaging.ai.Tools;
import com.villaging.config.VillagingConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/** Runs the model's tool calls for one villager on the server main thread. */
public final class MinecraftActionExecutor implements ActionExecutor {
	private final MinecraftServer server;
	private final Villager villager;
	private final UUID kingId;
	private final VillagingConfig config;

	public MinecraftActionExecutor(MinecraftServer server, Villager villager, UUID kingId, VillagingConfig config) {
		this.server = server;
		this.villager = villager;
		this.kingId = kingId;
		this.config = config;
	}

	@Override
	public Optional<String> validate(ToolCall call) {
		return Tools.validate(call, config.actionRange(), villager.getX(), villager.getY(), villager.getZ());
	}

	@Override
	public CompletableFuture<String> execute(ToolCall call) {
		CompletableFuture<String> result = new CompletableFuture<>();
		server.execute(() -> {
			if (villager.isRemoved() || !villager.isAlive()) {
				result.completeExceptionally(new IllegalStateException("villager is gone"));
				return;
			}
			Optional<ToolHandler> handler = ToolHandlers.get(call.name());
			if (handler.isEmpty()) {
				result.complete("failed: unknown tool " + call.name());
				return;
			}
			JsonObject args = call.args() == null ? new JsonObject() : call.args();
			try {
				ToolContext ctx = new ToolContext(server, villager, kingId, config);
				ToolHandler toRun = handler.get();
				Optional<RiskPolicy.Category> risk = RiskPolicy.classify(call.name(), args, heldItemId());
				if (RiskPolicy.mustAsk(risk, VillagerBrains.get(villager).askOff())) {
					chain(askThenRun(ctx, toRun, call, args, risk.get()), result);
				} else {
					chain(toRun.run(ctx, args), result);
				}
			} catch (RuntimeException e) {
				result.complete("failed: " + e.getMessage());
			}
		});
		return result;
	}

	@Override
	public boolean isAlive() {
		return !villager.isRemoved() && villager.isAlive();
	}

	/** Copies the action's outcome into {@code result}, and cancels the action if {@code result} is cancelled. */
	private static void chain(CompletableFuture<String> action, CompletableFuture<String> result) {
		action.whenComplete((value, error) -> {
			if (error != null) {
				result.completeExceptionally(error);
			} else {
				result.complete(value);
			}
		});
		result.whenComplete((value, error) -> {
			if (result.isCancelled()) {
				action.cancel(true);
			}
		});
	}

	private String heldItemId() {
		ItemStack held = villager.getMainHandItem();
		return held.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
	}

	/** Risky acts run only after the king says yes in the dialog; cancelling the call closes the question too. */
	private CompletableFuture<String> askThenRun(ToolContext ctx, ToolHandler handler, ToolCall call, JsonObject args,
			RiskPolicy.Category category) {
		String name = VillagerBrains.get(villager).name();
		String what = call.name() + " " + args;
		if (what.length() > 200) {
			what = what.substring(0, 200) + "...";
		}
		String question = name + " wants to do something risky (" + category.key() + "): " + what + ". Allow it?";
		CompletableFuture<String> answer = AskService.ask(ctx, question, List.of("Yes", "No"));
		CompletableFuture<String> composed = answer.thenCompose(a -> {
			if (a.equals(PendingQuestions.NO_ANSWER)) {
				return CompletableFuture.completedFuture("failed: the king did not answer");
			}
			if (!a.equalsIgnoreCase("yes")) {
				return CompletableFuture.completedFuture("failed: the king said no");
			}
			return handler.run(ctx, args);
		});
		composed.whenComplete((value, error) -> {
			if (composed.isCancelled()) {
				answer.cancel(true);
			}
		});
		return composed;
	}
}
