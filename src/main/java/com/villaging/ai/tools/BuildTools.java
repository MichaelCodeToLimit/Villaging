package com.villaging.ai.tools;

import static com.villaging.ai.ToolSchema.enumProp;
import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import com.villaging.blueprint.Blueprint;
import com.villaging.blueprint.Templates;
import java.util.Optional;
import java.util.Set;

/** Tool definitions for designing, planning and running builds. The handlers live in the Minecraft layer. */
public final class BuildTools {
	/** Longest shape plan text a model may send. */
	public static final int MAX_PLAN_CHARS = 20_000;
	private static final Set<String> ROTATIONS = Set.of("0", "90", "180", "270");
	private static final String[] STOCKPILE = {"stockpile_x", "stockpile_y", "stockpile_z"};

	private BuildTools() {
	}

	private static Optional<String> name(JsonObject args) {
		Optional<String> text = ToolSchema.stringNonBlank(args, "name", 32);
		if (text.isPresent()) {
			return text;
		}
		return Blueprint.isValidName(args.get("name").getAsString()) ? Optional.empty()
				: Optional.of("\"name\" must be 1 to 32 lowercase letters, digits or underscores, like stone_cottage.");
	}

	private static Optional<String> design(JsonObject args, int range, double ox, double oy, double oz) {
		Optional<String> bad = name(args);
		if (bad.isPresent()) {
			return bad;
		}
		bad = ToolSchema.stringNonBlank(args, "plan", MAX_PLAN_CHARS);
		if (bad.isPresent()) {
			return bad;
		}
		try {
			JsonElement parsed = JsonParser.parseString(args.get("plan").getAsString());
			if (!parsed.isJsonObject()) {
				return Optional.of("\"plan\" must be JSON text for an object with \"size\", \"palette\" and \"shapes\".");
			}
		} catch (RuntimeException e) {
			return Optional.of("\"plan\" is not valid JSON text; send an object with \"size\", \"palette\" and \"shapes\".");
		}
		return Optional.empty();
	}

	private static Optional<String> template(JsonObject args, int range, double ox, double oy, double oz) {
		Optional<String> bad = name(args);
		if (bad.isPresent()) {
			return bad;
		}
		bad = ToolSchema.oneOf(args, "template", Set.copyOf(Templates.ids()));
		if (bad.isPresent()) {
			return bad;
		}
		for (String size : new String[] {"width", "depth", "height"}) {
			if (args.has(size)) {
				bad = ToolSchema.intIn(args, size, 1, 64);
				if (bad.isPresent()) {
					return bad;
				}
			}
		}
		if (args.has("style")) {
			bad = ToolSchema.oneOf(args, "style", Set.copyOf(Templates.styles()));
			if (bad.isPresent()) {
				return bad;
			}
		}
		return args.has("roof") ? ToolSchema.oneOf(args, "roof", Set.copyOf(Templates.roofs())) : Optional.empty();
	}

	private static Optional<String> show(JsonObject args, int range, double ox, double oy, double oz) {
		Optional<String> bad = name(args);
		return bad.isPresent() ? bad : ToolSchema.intIn(args, "layer", 0, Blueprint.MAX_HEIGHT - 1);
	}

	private static Optional<String> planBuild(JsonObject args, int range, double ox, double oy, double oz) {
		Optional<String> bad = name(args);
		if (bad.isPresent()) {
			return bad;
		}
		bad = ToolSchema.coordinates(args, true, range, ox, oy, oz);
		if (bad.isPresent()) {
			return bad;
		}
		if (args.has("rotation")) {
			JsonElement r = args.get("rotation");
			if (!r.isJsonPrimitive() || !ROTATIONS.contains(r.getAsString())) {
				return Optional.of("\"rotation\" must be one of [0, 180, 270, 90] degrees.");
			}
		}
		int given = 0;
		for (String key : STOCKPILE) {
			if (args.has(key)) {
				given++;
			}
		}
		if (given == 0) {
			return Optional.empty();
		}
		if (given != 3) {
			return Optional.of("Give the stockpile chest as all three of stockpile_x, stockpile_y and stockpile_z, or none.");
		}
		JsonObject chest = new JsonObject();
		chest.add("x", args.get("stockpile_x"));
		chest.add("y", args.get("stockpile_y"));
		chest.add("z", args.get("stockpile_z"));
		return ToolSchema.coordinates(chest, true, range, ox, oy, oz);
	}

	public static void register() {
		Tools.register(new ToolDef("design_blueprint",
				"Save a new blueprint from a shape plan you write. \"plan\" is JSON text: {\"size\":[w,h,d],\"palette\":{\"S\":\"minecraft:stone_bricks\"},"
						+ "\"shapes\":[{\"type\":\"box\",\"x\":0,\"y\":0,\"z\":0,\"w\":5,\"h\":3,\"d\":5,\"block\":\"S\"}]}. Shape types: box, hollow_box, "
						+ "floor, walls, roof_gable, roof_flat, roof_pyramid, cylinder, line, door, window. At most 48 wide or deep, 64 high, 60 shapes. "
						+ "Returns the size, block count and the materials it needs.",
				object(prop("name", "string", "A new blueprint name: lowercase letters, digits, underscores."),
						prop("plan", "string", "The shape plan as JSON text.")).required("name", "plan").build()),
				BuildTools::design);
		Tools.register(new ToolDef("use_template",
				"Save a blueprint from a ready-made design: " + String.join(", ", Templates.ids())
						+ ". Width, depth and height are optional (they are kept inside each design's limits); style is "
						+ String.join(", ", Templates.styles()) + "; roof (cottage only) is " + String.join(", ", Templates.roofs()) + ".",
				object(prop("name", "string", "A new blueprint name: lowercase letters, digits, underscores."),
						enumProp("template", "Which design.", Templates.ids().toArray(new String[0])),
						prop("width", "integer", "Width (the length of a wall or bridge)."), prop("depth", "integer", "Depth."),
						prop("height", "integer", "Height of the walls."),
						enumProp("style", "Building materials.", Templates.styles().toArray(new String[0])),
						enumProp("roof", "Roof shape (cottage).", Templates.roofs().toArray(new String[0])))
						.required("name", "template").build()),
				BuildTools::template);
		Tools.register(new ToolDef("list_blueprints", "List the saved blueprints with their sizes.", object().build()),
				(args, range, ox, oy, oz) -> Optional.empty());
		Tools.register(new ToolDef("show_blueprint",
				"Look at one layer of a saved blueprint as text (. is empty), to check a design. Layer 0 is the bottom.",
				object(prop("name", "string", "Blueprint name."), prop("layer", "integer", "Layer number from 0 (bottom).")).required("name", "layer").build()),
				BuildTools::show);
		Tools.register(new ToolDef("plan_build",
				"Get ready to build a blueprint at a spot: checks the site and counts the materials against what you carry and a chest "
						+ "(name the chest with stockpile_x/y/z, or the nearest chest within 8 blocks is used). Starts nothing; it tells you what is missing. "
						+ "x, y, z is the lowest corner of the build (where the blueprint's corner goes).",
				object(prop("name", "string", "Blueprint name."), prop("x", "integer", "Corner X."), prop("y", "integer", "Corner Y (the floor level)."),
						prop("z", "integer", "Corner Z."), enumProp("rotation", "Turn the build clockwise, in degrees.", "0", "90", "180", "270"),
						prop("stockpile_x", "integer", "Chest X (optional)."), prop("stockpile_y", "integer", "Chest Y (optional)."),
						prop("stockpile_z", "integer", "Chest Z (optional).")).required("name", "x", "y", "z").build()),
				BuildTools::planBuild);
		Tools.register(new ToolDef("start_build",
				"Start (or carry on with) the build you planned with plan_build. The king is asked first for big builds. You keep building by yourself "
						+ "and will say when you are done, or what you need if materials run out.",
				object().build()), (args, range, ox, oy, oz) -> Optional.empty());
		Tools.register(new ToolDef("build_status", "Say how far the current build is and what is missing.", object().build()),
				(args, range, ox, oy, oz) -> Optional.empty());
		Tools.register(new ToolDef("stop_build", "Pause the build. start_build carries on from where it stopped.", object().build()),
				(args, range, ox, oy, oz) -> Optional.empty());
		Tools.register(new ToolDef("cancel_build", "End the build for good and forget it. Blocks already placed stay.", object().build()),
				(args, range, ox, oy, oz) -> Optional.empty());
	}
}
