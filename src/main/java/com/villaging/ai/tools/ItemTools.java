package com.villaging.ai.tools;

import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.google.gson.JsonObject;
import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import java.util.Optional;

/** Tools about the items a villager carries, hands over, takes and uses. */
public final class ItemTools {
	public static final int MAX_COUNT = 2304;

	private ItemTools() {
	}

	private static Optional<String> itemAndCount(JsonObject args) {
		Optional<String> item = WorldTools.itemId(args, "item");
		return item.isPresent() ? item : ToolSchema.intIn(args, "count", 1, MAX_COUNT);
	}

	private static Optional<String> optionalItem(JsonObject args) {
		return args.has("item") ? WorldTools.itemId(args, "item") : Optional.empty();
	}

	public static void register() {
		Tools.register(new ToolDef("pick_up",
				"Walk to dropped items nearby (within 12 blocks) and pick them up. Optionally only one kind.",
				object(prop("item", "string", "Item id such as minecraft:oak_log; leave out for anything.")).build()),
				(args, range, ox, oy, oz) -> optionalItem(args));
		Tools.register(new ToolDef("drop", "Drop some of an item you carry on the ground.",
				object(prop("item", "string", "Item id."), prop("count", "integer", "How many, 1 to " + MAX_COUNT + "."))
						.required("item", "count").build()),
				(args, range, ox, oy, oz) -> itemAndCount(args));
		Tools.register(new ToolDef("give_to_king", "Walk to the king and hand over some of an item you carry.",
				object(prop("item", "string", "Item id."), prop("count", "integer", "How many, 1 to " + MAX_COUNT + "."))
						.required("item", "count").build()),
				(args, range, ox, oy, oz) -> itemAndCount(args));
		Tools.register(new ToolDef("take_from_king",
				"Take items from the king's inventory. Use only when the king told you to take them, and only what was named. "
						+ "The king is asked to confirm.",
				object(prop("item", "string", "Item id."), prop("count", "integer", "How many, 1 to " + MAX_COUNT + "."))
						.required("item", "count").build()),
				(args, range, ox, oy, oz) -> itemAndCount(args));
		Tools.register(new ToolDef("equip", "Wear armor or hold a tool or weapon from your inventory.",
				object(prop("item", "string", "Item id.")).required("item").build()),
				(args, range, ox, oy, oz) -> WorldTools.itemId(args, "item"));
		Tools.register(new ToolDef("use_item", "Use an item from your inventory like a player right-clicking with it.",
				object(prop("item", "string", "Item id.")).required("item").build()),
				(args, range, ox, oy, oz) -> WorldTools.itemId(args, "item"));
		Tools.register(new ToolDef("eat", "Eat a food item from your inventory to heal.",
				object(prop("item", "string", "Item id; leave out to eat whatever food you have.")).build()),
				(args, range, ox, oy, oz) -> optionalItem(args));
	}
}
