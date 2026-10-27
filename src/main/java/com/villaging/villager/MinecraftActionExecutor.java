package com.villaging.villager;

import com.google.gson.JsonObject;
import com.villaging.ai.ActionExecutor;
import com.villaging.ai.ToolCall;
import com.villaging.ai.Tools;
import com.villaging.config.VillagingConfig;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.villager.Villager;

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
				chain(handler.get().run(new ToolContext(server, villager, kingId, config), args), result);
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
}
