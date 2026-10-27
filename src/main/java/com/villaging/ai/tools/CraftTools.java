package com.villaging.ai.tools;

import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import java.util.Optional;

/** Tools for crafting and for working furnaces and brewing stands. */
public final class CraftTools {
	public static final int MAX_COUNT = 64;

	private CraftTools() {
	}

	public static void register() {
		Tools.register(new ToolDef("craft",
				"Craft an item from materials in your inventory. Small recipes work anywhere; big ones need a crafting table "
						+ "within 16 blocks. Gives at least the count asked for if you have the materials.",
				object(prop("item", "string", "Item id to make, e.g. minecraft:oak_planks."),
						prop("count", "integer", "How many to make, 1 to " + MAX_COUNT + ".")).required("item", "count").build()),
				(args, range, ox, oy, oz) -> {
					Optional<String> item = WorldTools.itemId(args, "item");
					return item.isPresent() ? item : ToolSchema.intIn(args, "count", 1, MAX_COUNT);
				});
		Tools.register(new ToolDef("smelt",
				"Smelt an item from your inventory in a furnace within 16 blocks, using fuel you carry. Takes real time.",
				object(prop("item", "string", "Item id to smelt, e.g. minecraft:raw_iron."),
						prop("count", "integer", "How many to smelt, 1 to " + MAX_COUNT + ".")).required("item", "count").build()),
				(args, range, ox, oy, oz) -> {
					Optional<String> item = WorldTools.itemId(args, "item");
					return item.isPresent() ? item : ToolSchema.intIn(args, "count", 1, MAX_COUNT);
				});
		Tools.register(new ToolDef("brew",
				"Brew potions in a brewing stand within 16 blocks: adds the ingredient to the potion bottles you carry "
						+ "(up to 3) and blaze powder fuel if needed. Takes real time.",
				object(prop("item", "string", "Ingredient item id, e.g. minecraft:nether_wart.")).required("item").build()),
				(args, range, ox, oy, oz) -> WorldTools.itemId(args, "item"));
	}
}
