package com.villaging.villager;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.item.Item;

/** Handlers for the container tools. */
final class ContainerHandlers {
	private ContainerHandlers() {
	}

	static void register() {
		ToolHandlers.register("open_container", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(),
				new ContainerAction(ContainerAction.Mode.OPEN, WorldHandlers.pos(args, "x", "y", "z"), null, 0, ctx.kingId(),
						ctx.config())));
		ToolHandlers.register("put_in", (ctx, args) -> transfer(ctx, args, ContainerAction.Mode.PUT));
		ToolHandlers.register("take_out", (ctx, args) -> transfer(ctx, args, ContainerAction.Mode.TAKE));
	}

	private static CompletableFuture<String> transfer(ToolContext ctx, JsonObject args, ContainerAction.Mode mode) {
		Item item = ItemHandlers.item(args.get("item").getAsString());
		if (item == null) {
			return CompletableFuture.completedFuture("failed: there is no item called " + args.get("item").getAsString());
		}
		return ActionManager.INSTANCE.run(ctx.villager(), new ContainerAction(mode, WorldHandlers.pos(args, "x", "y", "z"),
				item, args.get("count").getAsInt(), ctx.kingId(), ctx.config()));
	}
}
