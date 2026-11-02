package com.villaging.villager;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/** Handlers for the tools that place, use and break blocks. */
final class WorldHandlers {
	private WorldHandlers() {
	}

	static void register() {
		ToolHandlers.register("place_block", (ctx, args) -> {
			Item item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(args.get("item").getAsString())).orElse(null);
			if (item == null) {
				return CompletableFuture.completedFuture("failed: there is no item called " + args.get("item").getAsString());
			}
			return ActionManager.INSTANCE.run(ctx.villager(), new PlaceBlockAction(item, pos(args, "x", "y", "z"),
					ctx.kingId(), ctx.config()));
		});
		ToolHandlers.register("use_block", (ctx, args) -> {
			BlockPos target = pos(args, "x", "y", "z");
			// A bed is slept in by the helper itself; the fake player lying down would not put the helper to sleep.
			if (ctx.villager().level().getBlockState(target).getBlock() instanceof net.minecraft.world.level.block.BedBlock) {
				return MoveHandlers.sleepIn(ctx, target);
			}
			return ActionManager.INSTANCE.run(ctx.villager(), new UseBlockAction(target, ctx.kingId(), ctx.config()));
		});
		ToolHandlers.register("break_area", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(),
				new BreakAreaAction(pos(args, "x1", "y1", "z1"), pos(args, "x2", "y2", "z2"), ctx.kingId(), ctx.config())));
	}

	static BlockPos pos(JsonObject args, String x, String y, String z) {
		return new BlockPos(args.get(x).getAsInt(), args.get(y).getAsInt(), args.get(z).getAsInt());
	}
}
