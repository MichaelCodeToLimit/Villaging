package com.villaging.ai.tools;

import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.google.gson.JsonObject;
import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import java.util.Optional;

/** Tools for gathering: trees, farming, fishing and working with animals. */
public final class GatherTools {
	public static final int MIN_FISH_SECONDS = 5;
	public static final int MAX_FISH_SECONDS = 120;

	private GatherTools() {
	}

	private static ToolDef block(String name, String description) {
		return new ToolDef(name, description, object(prop("x", "integer", "Block X."), prop("y", "integer", "Block Y."),
				prop("z", "integer", "Block Z.")).required("x", "y", "z").build());
	}

	private static ToolDef animal(String name, String description) {
		return new ToolDef(name, description, object(prop("entity_id", "integer",
				"The animal's id from look_around; leave out to use the nearest suitable one.")).build());
	}

	private static Optional<String> optionalEntityId(JsonObject args) {
		return args.has("entity_id") ? ToolSchema.intIn(args, "entity_id", 0, Integer.MAX_VALUE) : Optional.empty();
	}

	public static void register() {
		ArgCheck nearbyBlock = (args, range, ox, oy, oz) -> ToolSchema.coordinates(args, true, range, ox, oy, oz);
		Tools.register(block("chop_tree", "Fell a tree: break the connected logs starting from the log at this position "
				+ "(up to 64 logs, from the bottom up, as high as you can reach)."), nearbyBlock::check);
		Tools.register(block("till", "Till dirt or grass into farmland with a hoe from your inventory."), nearbyBlock::check);
		Tools.register(block("harvest", "Harvest a fully grown crop and replant it if you have the seeds."), nearbyBlock::check);
		Tools.register(new ToolDef("plant", "Plant seeds or a crop item at a position (usually on farmland).",
				object(prop("item", "string", "Seed or crop item id, e.g. minecraft:wheat_seeds."), prop("x", "integer", "Target X."),
						prop("y", "integer", "Target Y (the air block above the farmland)."), prop("z", "integer", "Target Z."))
						.required("item", "x", "y", "z").build()),
				(args, range, ox, oy, oz) -> {
					Optional<String> item = WorldTools.itemId(args, "item");
					return item.isPresent() ? item : ToolSchema.coordinates(args, true, range, ox, oy, oz);
				});
		Tools.register(new ToolDef("fish",
				"Fish with a fishing rod from your inventory while standing near water. About one bite every 20 seconds.",
				object(prop("seconds", "integer", "How long to fish, " + MIN_FISH_SECONDS + " to " + MAX_FISH_SECONDS + ".")).required("seconds").build()),
				(args, range, ox, oy, oz) -> ToolSchema.intIn(args, "seconds", MIN_FISH_SECONDS, MAX_FISH_SECONDS));
		Tools.register(animal("shear", "Shear a sheep with shears from your inventory."), (args, range, ox, oy, oz) -> optionalEntityId(args));
		Tools.register(animal("milk", "Milk a cow with a bucket from your inventory."), (args, range, ox, oy, oz) -> optionalEntityId(args));
		Tools.register(animal("breed", "Feed an animal and a partner of the same kind with food from your inventory so they breed."),
				(args, range, ox, oy, oz) -> optionalEntityId(args));
		Tools.register(animal("tame", "Try to tame a wild animal such as a wolf or cat with food from your inventory."),
				(args, range, ox, oy, oz) -> optionalEntityId(args));
	}

	@FunctionalInterface
	private interface ArgCheck {
		Optional<String> check(JsonObject args, int range, double ox, double oy, double oz);
	}
}
