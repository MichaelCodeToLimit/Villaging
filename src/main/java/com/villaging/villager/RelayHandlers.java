package com.villaging.villager;

import com.villaging.Villaging;
import com.villaging.command.CommandService.CommandResult;
import com.villaging.command.VillagingRuntime;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

/** Handler for the commander's relay_order tool: the king approves each relayed order. */
final class RelayHandlers {
	private RelayHandlers() {
	}

	private static CompletableFuture<String> failed(String reason) {
		return CompletableFuture.completedFuture("failed: " + reason);
	}

	static void register() {
		ToolHandlers.register("relay_order", (ctx, args) -> {
			if (!VillagerBrains.get(ctx.villager()).commander()) {
				return failed("only a commander can relay orders");
			}
			String wanted = args.get("villager_name").getAsString().trim();
			String order = args.get("order").getAsString().trim();
			Villager target = null;
			if (ctx.villager().level() instanceof ServerLevel level) {
				for (Villager other : level.getEntitiesOfClass(Villager.class,
						ctx.villager().getBoundingBox().inflate(ctx.config().actionRange()))) {
					VillagerBrain brain = VillagerBrains.get(other);
					if (other != ctx.villager() && brain.smart() && brain.name().equalsIgnoreCase(wanted)) {
						target = other;
						break;
					}
				}
			}
			if (target == null) {
				return failed("I can't find a smart villager called " + wanted + " nearby");
			}
			Villager chosen = target;
			String commander = VillagerBrains.get(ctx.villager()).name();
			return AskService.confirmThen(ctx, commander + " wants " + VillagerBrains.get(chosen).name() + " to: " + order
					+ ". Allow it?", yes -> {
						if (!yes) {
							return "failed: the king said no";
						}
						VillagingRuntime runtime = Villaging.runtime();
						ServerPlayer king = ctx.server().getPlayerList().getPlayer(ctx.kingId());
						if (runtime == null || king == null || chosen.isRemoved()) {
							return "failed: that is no longer possible";
						}
						CommandResult result = runtime.handle(king, chosen.getId(), order);
						return result == CommandResult.ACCEPTED ? "ok: passed the order to " + VillagerBrains.get(chosen).name()
								: "failed: " + VillagingRuntime.rejectionMessage(result, runtime.problem());
					});
		});
	}
}
