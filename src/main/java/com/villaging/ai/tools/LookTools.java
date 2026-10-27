package com.villaging.ai.tools;

import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import java.util.Optional;

/** Read-only tools: what is around the villager, what a block is, what it carries. */
public final class LookTools {
	public static final int MAX_RADIUS = 16;

	private LookTools() {
	}

	public static void register() {
		Tools.register(new ToolDef("look_around",
				"Look at your surroundings: notable blocks (ores, wood, containers, stations, crops, water, lava) with "
						+ "coordinates, nearby creatures, dropped items. Do this first when you are unsure what is where.",
				object(prop("radius", "integer", "How far to look, 1 to " + MAX_RADIUS + " blocks (default 8).")).build()),
				(args, range, ox, oy, oz) -> args.has("radius") ? ToolSchema.intIn(args, "radius", 1, MAX_RADIUS)
						: Optional.empty());
		Tools.register(new ToolDef("look_at",
				"Check exactly what block is at a position, and what is inside it if it is a container.",
				object(prop("x", "integer", "Block X."), prop("y", "integer", "Block Y."), prop("z", "integer", "Block Z."))
						.required("x", "y", "z").build()),
				(args, range, ox, oy, oz) -> ToolSchema.coordinates(args, true, range, ox, oy, oz));
		Tools.register(new ToolDef("check_inventory", "List the items you are carrying.", object().build()),
				(args, range, ox, oy, oz) -> Optional.empty());
	}
}
