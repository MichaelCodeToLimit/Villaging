package com.villaging.ai.tools;

import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.google.gson.JsonObject;
import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import java.util.Optional;
import java.util.regex.Pattern;

/** Tools that change or use blocks in the world. */
public final class WorldTools {
	public static final int MAX_BOX_SIDE = 16;
	static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

	private WorldTools() {
	}

	/** An item or block id such as {@code minecraft:oak_planks}. */
	static Optional<String> itemId(JsonObject args, String key) {
		Optional<String> text = ToolSchema.stringNonBlank(args, key, 100);
		if (text.isPresent()) {
			return text;
		}
		return ITEM_ID.matcher(args.get(key).getAsString()).matches() ? Optional.empty()
				: Optional.of("\"" + key + "\" must be an id like minecraft:oak_planks.");
	}

	public static void register() {
		Tools.register(new ToolDef("place_block",
				"Place a block from your inventory at a position. It must touch an existing block to be placed against.",
				object(prop("item", "string", "Block item id, e.g. minecraft:oak_planks."), prop("x", "integer", "Target X."),
						prop("y", "integer", "Target Y."), prop("z", "integer", "Target Z.")).required("item", "x", "y", "z").build()),
				(args, range, ox, oy, oz) -> {
					Optional<String> item = itemId(args, "item");
					return item.isPresent() ? item : ToolSchema.coordinates(args, true, range, ox, oy, oz);
				});
		Tools.register(new ToolDef("use_block",
				"Use a block like a player: open a door or chest lid, flip a lever, press a button, sleep in a bed.",
				object(prop("x", "integer", "Block X."), prop("y", "integer", "Block Y."), prop("z", "integer", "Block Z."))
						.required("x", "y", "z").build()),
				(args, range, ox, oy, oz) -> ToolSchema.coordinates(args, true, range, ox, oy, oz));
		Tools.register(new ToolDef("break_area",
				"Break every block in a box between two opposite corners (at most " + MAX_BOX_SIDE + " blocks per side). "
						+ "Large boxes need the king's approval.",
				object(prop("x1", "integer", "First corner X."), prop("y1", "integer", "First corner Y."),
						prop("z1", "integer", "First corner Z."), prop("x2", "integer", "Opposite corner X."),
						prop("y2", "integer", "Opposite corner Y."), prop("z2", "integer", "Opposite corner Z."))
						.required("x1", "y1", "z1", "x2", "y2", "z2").build()),
				(args, range, ox, oy, oz) -> {
					for (String axis : new String[] {"x", "y", "z"}) {
						Optional<String> bad = ToolSchema.intIn(args, axis + "1", Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2);
						if (bad.isEmpty()) {
							bad = ToolSchema.intIn(args, axis + "2", Integer.MIN_VALUE / 2, Integer.MAX_VALUE / 2);
						}
						if (bad.isPresent()) {
							return bad;
						}
						if (Math.abs(args.get(axis + "1").getAsLong() - args.get(axis + "2").getAsLong()) + 1 > MAX_BOX_SIDE) {
							return Optional.of("The box may be at most " + MAX_BOX_SIDE + " blocks along each side.");
						}
					}
					for (int corner = 1; corner <= 2; corner++) {
						JsonObject c = new JsonObject();
						c.add("x", args.get("x" + corner));
						c.add("y", args.get("y" + corner));
						c.add("z", args.get("z" + corner));
						Optional<String> far = ToolSchema.coordinates(c, true, range, ox, oy, oz);
						if (far.isPresent()) {
							return far;
						}
					}
					return Optional.empty();
				});
	}
}
