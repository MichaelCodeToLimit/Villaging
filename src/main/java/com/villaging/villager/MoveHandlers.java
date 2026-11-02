package com.villaging.villager;

import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;

/** Handlers for the sit and sleep tools. */
final class MoveHandlers {
	private MoveHandlers() {
	}

	static void register() {
		ToolHandlers.register("sit", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(), new RestActions.Sit()));
		ToolHandlers.register("sleep", (ctx, args) -> sleepIn(ctx, WorldHandlers.pos(args, "x", "y", "z")));
	}

	/** Walks to the bed and lies down; once asleep a second action keeps the villager in bed until morning. */
	static CompletableFuture<String> sleepIn(ToolContext ctx, BlockPos bed) {
		return ActionManager.INSTANCE.run(ctx.villager(), new RestActions.Sleep(bed)).thenApply(result -> {
			if (result.startsWith("ok") && !ctx.villager().isRemoved()) {
				ActionManager.INSTANCE.run(ctx.villager(), new RestActions.StayAsleep(bed));
			}
			return result;
		});
	}
}
