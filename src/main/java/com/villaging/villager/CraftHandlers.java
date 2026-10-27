package com.villaging.villager;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.item.Item;

/** Handlers for crafting, smelting and brewing. */
final class CraftHandlers {
	private CraftHandlers() {
	}

	private static CompletableFuture<String> noSuchItem(String id) {
		return CompletableFuture.completedFuture("failed: there is no item called " + id);
	}

	static void register() {
		ToolHandlers.register("craft", (ctx, args) -> {
			Item item = ItemHandlers.item(args.get("item").getAsString());
			return item == null ? noSuchItem(args.get("item").getAsString())
					: ActionManager.INSTANCE.run(ctx.villager(), new CraftAction(item, args.get("count").getAsInt()));
		});
		ToolHandlers.register("smelt", (ctx, args) -> {
			Item item = ItemHandlers.item(args.get("item").getAsString());
			return item == null ? noSuchItem(args.get("item").getAsString())
					: ActionManager.INSTANCE.run(ctx.villager(), new SmeltAction(item, args.get("count").getAsInt()));
		});
		ToolHandlers.register("brew", (ctx, args) -> {
			Item item = ItemHandlers.item(args.get("item").getAsString());
			return item == null ? noSuchItem(args.get("item").getAsString())
					: ActionManager.INSTANCE.run(ctx.villager(), new BrewAction(item));
		});
	}
}
