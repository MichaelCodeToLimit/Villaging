package com.villaging.villager;

import net.minecraft.core.BlockPos;

/** Handlers for the sit and sleep tools. */
final class MoveHandlers {
	private MoveHandlers() {
	}

	static void register() {
		ToolHandlers.register("sit", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(), new RestActions.Sit()));
		ToolHandlers.register("sleep", (ctx, args) -> {
			BlockPos bed = WorldHandlers.pos(args, "x", "y", "z");
			// Once asleep the tool reports success, and a second action keeps the villager in bed until morning.
			return ActionManager.INSTANCE.run(ctx.villager(), new RestActions.Sleep(bed)).thenApply(result -> {
				if (result.startsWith("ok") && !ctx.villager().isRemoved()) {
					ActionManager.INSTANCE.run(ctx.villager(), new RestActions.StayAsleep(bed));
				}
				return result;
			});
		});
	}
}
