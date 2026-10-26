package com.villaging.villager;

import com.google.gson.JsonObject;
import com.villaging.ai.ActionExecutor;
import com.villaging.ai.ToolCall;
import com.villaging.ai.Tools;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.npc.villager.Villager;

/** Runs the model's tool calls for one villager on the server main thread. */
public final class MinecraftActionExecutor implements ActionExecutor {
	private final MinecraftServer server;
	private final Villager villager;
	private final UUID kingId;
	private final int range;

	public MinecraftActionExecutor(MinecraftServer server, Villager villager, UUID kingId, int range) {
		this.server = server;
		this.villager = villager;
		this.kingId = kingId;
		this.range = range;
	}

	@Override
	public Optional<String> validate(ToolCall call) {
		return Tools.validate(call, range, villager.getX(), villager.getY(), villager.getZ());
	}

	@Override
	public CompletableFuture<String> execute(ToolCall call) {
		CompletableFuture<String> result = new CompletableFuture<>();
		server.execute(() -> {
			if (villager.isRemoved() || !villager.isAlive()) {
				result.completeExceptionally(new IllegalStateException("villager is gone"));
				return;
			}
			JsonObject args = call.args();
			switch (call.name()) {
				case "say" -> {
					VillagerSpeech.say(server, villager, kingId, args.get("text").getAsString());
					result.complete("ok");
				}
				case "stop" -> {
					ActionManager.INSTANCE.stop(villager);
					result.complete("ok: stopped");
				}
				case "follow_me" -> chain(ActionManager.INSTANCE.run(villager, new FollowAction(kingId)), result);
				case "go_to" -> chain(ActionManager.INSTANCE.run(villager,
						new GoToAction(args.get("x").getAsDouble(), args.get("y").getAsDouble(), args.get("z").getAsDouble())),
						result);
				case "mine_block" -> chain(ActionManager.INSTANCE.run(villager,
						new MineAction(new BlockPos(args.get("x").getAsInt(), args.get("y").getAsInt(), args.get("z").getAsInt()))),
						result);
				default -> result.complete("failed: unknown tool " + call.name());
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
