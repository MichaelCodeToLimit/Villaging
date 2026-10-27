package com.villaging.ai.tools;

import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.google.gson.JsonObject;
import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import java.util.Optional;

/** Tools for looking in, filling and emptying chests, barrels and other containers. */
public final class ContainerTools {
	private ContainerTools() {
	}

	private static Optional<String> transfer(JsonObject args, int range, double ox, double oy, double oz) {
		Optional<String> where = ToolSchema.coordinates(args, true, range, ox, oy, oz);
		if (where.isPresent()) {
			return where;
		}
		Optional<String> item = WorldTools.itemId(args, "item");
		return item.isPresent() ? item : ToolSchema.intIn(args, "count", 1, ItemTools.MAX_COUNT);
	}

	public static void register() {
		Tools.register(new ToolDef("open_container", "Look inside a chest, barrel or other container and see what it holds.",
				object(prop("x", "integer", "Container X."), prop("y", "integer", "Container Y."),
						prop("z", "integer", "Container Z.")).required("x", "y", "z").build()),
				(args, range, ox, oy, oz) -> ToolSchema.coordinates(args, true, range, ox, oy, oz));
		Tools.register(new ToolDef("put_in", "Put some of an item you carry into a container.",
				object(prop("x", "integer", "Container X."), prop("y", "integer", "Container Y."),
						prop("z", "integer", "Container Z."), prop("item", "string", "Item id."),
						prop("count", "integer", "How many, 1 to " + ItemTools.MAX_COUNT + "."))
						.required("x", "y", "z", "item", "count").build()),
				ContainerTools::transfer);
		Tools.register(new ToolDef("take_out", "Take some of an item out of a container into your inventory.",
				object(prop("x", "integer", "Container X."), prop("y", "integer", "Container Y."),
						prop("z", "integer", "Container Z."), prop("item", "string", "Item id."),
						prop("count", "integer", "How many, 1 to " + ItemTools.MAX_COUNT + "."))
						.required("x", "y", "z", "item", "count").build()),
				ContainerTools::transfer);
	}
}
