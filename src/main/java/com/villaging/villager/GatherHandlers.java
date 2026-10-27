package com.villaging.villager;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.world.item.Item;

/** Handlers for the gathering tools. */
final class GatherHandlers {
	private GatherHandlers() {
	}

	private static Integer entityId(JsonObject args) {
		return args.has("entity_id") ? args.get("entity_id").getAsInt() : null;
	}

	static void register() {
		ToolHandlers.register("chop_tree", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(),
				new ChopTreeAction(WorldHandlers.pos(args, "x", "y", "z"), ctx.kingId(), ctx.config())));
		ToolHandlers.register("till", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(),
				new FarmActions.Till(WorldHandlers.pos(args, "x", "y", "z"), ctx.kingId(), ctx.config())));
		ToolHandlers.register("harvest", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(),
				new FarmActions.Harvest(WorldHandlers.pos(args, "x", "y", "z"), ctx.kingId(), ctx.config())));
		ToolHandlers.register("plant", (ctx, args) -> {
			Item item = ItemHandlers.item(args.get("item").getAsString());
			if (item == null) {
				return CompletableFuture.completedFuture("failed: there is no item called " + args.get("item").getAsString());
			}
			return ActionManager.INSTANCE.run(ctx.villager(), new PlaceBlockAction(item, WorldHandlers.pos(args, "x", "y", "z"),
					ctx.kingId(), ctx.config()));
		});
		ToolHandlers.register("fish", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(),
				new FishAction(args.get("seconds").getAsInt())));
		for (InteractEntityAction.Kind kind : InteractEntityAction.Kind.values()) {
			ToolHandlers.register(kind.name().toLowerCase(), (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(),
					new InteractEntityAction(kind, entityId(args))));
		}
	}
}
