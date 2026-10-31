package com.villaging.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the AI's small list of shapes into a {@link Blueprint}. The AI never lists blocks one by one: it describes boxes,
 * walls, roofs and so on, and this class draws them in order (later shapes overwrite earlier ones). Every problem is
 * reported with the shape number so the AI can fix its plan.
 *
 * <p>Plan format: {@code {"size":[w,h,d],"palette":{"S":"minecraft:stone"},"shapes":[{"type":"box","x":0,"y":0,"z":0,"w":3,"h":2,"d":3,"block":"S"}]}}
 */
public final class ShapePlan {
	public static final int MAX_SHAPES = 60;

	private static final String SPARE_KEYS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private static final String TYPES = "box, hollow_box, floor, walls, roof_gable, roof_flat, roof_pyramid, cylinder, line, door, window";

	private ShapePlan() {
	}

	/** A problem inside one shape; turned into a {@link BlueprintException} naming the shape. */
	private static final class ShapeError extends Exception {
		ShapeError(String message) {
			super(message);
		}
	}

	public static Blueprint compile(String name, JsonObject plan, int maxCells) throws BlueprintException {
		if (plan == null) {
			throw new BlueprintException("The plan is missing or is not valid JSON; send an object with \"size\", \"palette\" and \"shapes\".");
		}
		int[] size = readSize(plan);
		Map<Character, String> palette = readPalette(plan);
		JsonElement shapesElement = plan.get("shapes");
		if (shapesElement == null || !shapesElement.isJsonArray()) {
			throw new BlueprintException("The plan needs a \"shapes\" list.");
		}
		JsonArray shapes = shapesElement.getAsJsonArray();
		if (shapes.size() > MAX_SHAPES) {
			throw new BlueprintException("A plan may have at most " + MAX_SHAPES + " shapes; this one has " + shapes.size() + ".");
		}
		Raster raster = new Raster(size[0], size[1], size[2], palette);
		for (int i = 0; i < shapes.size(); i++) {
			try {
				if (!shapes.get(i).isJsonObject()) {
					throw new ShapeError("must be an object like {\"type\":\"box\",...}");
				}
				raster.draw(shapes.get(i).getAsJsonObject());
			} catch (ShapeError e) {
				throw new BlueprintException("shape " + (i + 1) + ": " + e.getMessage());
			}
		}
		return raster.toBlueprint(name, maxCells);
	}

	private static int[] readSize(JsonObject plan) throws BlueprintException {
		JsonElement e = plan.get("size");
		if (e == null || !e.isJsonArray() || e.getAsJsonArray().size() != 3) {
			throw new BlueprintException("The plan needs a \"size\": [width, height, depth], for example [9,6,7].");
		}
		int[] size = new int[3];
		for (int i = 0; i < 3; i++) {
			JsonElement v = e.getAsJsonArray().get(i);
			if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber() || v.getAsDouble() != Math.rint(v.getAsDouble())) {
				throw new BlueprintException("The \"size\" numbers must be whole numbers.");
			}
			size[i] = (int) v.getAsDouble();
		}
		if (size[0] < 1 || size[1] < 1 || size[2] < 1) {
			throw new BlueprintException("The \"size\" must be at least 1 in every direction.");
		}
		if (size[0] > Blueprint.MAX_SIDE || size[2] > Blueprint.MAX_SIDE) {
			throw new BlueprintException("The plan is too wide: width and depth may be at most " + Blueprint.MAX_SIDE + " blocks.");
		}
		if (size[1] > Blueprint.MAX_HEIGHT) {
			throw new BlueprintException("The plan is too high: the height may be at most " + Blueprint.MAX_HEIGHT + " blocks.");
		}
		return size;
	}

	private static Map<Character, String> readPalette(JsonObject plan) throws BlueprintException {
		JsonElement e = plan.get("palette");
		if (e == null || !e.isJsonObject()) {
			throw new BlueprintException("The plan needs a \"palette\" like {\"S\":\"minecraft:stone\"}.");
		}
		Map<Character, String> palette = new LinkedHashMap<>();
		for (Map.Entry<String, JsonElement> entry : e.getAsJsonObject().entrySet()) {
			String key = entry.getKey();
			if (key.length() != 1 || key.charAt(0) == '.' || Character.isWhitespace(key.charAt(0))) {
				throw new BlueprintException("The palette key \"" + key + "\" must be a single character that is not a dot or a space.");
			}
			if (!entry.getValue().isJsonPrimitive()) {
				throw new BlueprintException("palette entry " + key + " must be a block id like minecraft:stone.");
			}
			BlockRules.State state;
			try {
				state = BlockRules.parse(entry.getValue().getAsString());
			} catch (IllegalArgumentException bad) {
				throw new BlueprintException("palette entry " + key + ": " + bad.getMessage());
			}
			String denied = BlockRules.denyReason(state.id());
			if (denied != null) {
				throw new BlueprintException("palette entry " + key + " uses a block that is not allowed (" + denied + ").");
			}
			palette.put(key.charAt(0), state.text());
		}
		return palette;
	}

	/** The drawing board: one state text (or null for air) per position in the plan's box. */
	private static final class Raster {
		private final int sx;
		private final int sy;
		private final int sz;
		private final Map<Character, String> palette;
		private final String[] grid;

		Raster(int sx, int sy, int sz, Map<Character, String> palette) {
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
			this.palette = palette;
			this.grid = new String[sx * sy * sz];
		}

		private int index(int x, int y, int z) {
			return (y * sz + z) * sx + x;
		}

		private void put(int x, int y, int z, String state) throws ShapeError {
			if (x < 0 || x >= sx || y < 0 || y >= sy || z < 0 || z >= sz) {
				throw new ShapeError("reaches " + x + "," + y + "," + z + ", outside the plan's size " + sx + "," + sy + "," + sz + ".");
			}
			grid[index(x, y, z)] = state;
		}

		private void checkBox(int x, int y, int z, int w, int h, int d) throws ShapeError {
			if (w < 1 || h < 1 || d < 1) {
				throw new ShapeError("w, h and d must be at least 1.");
			}
			if (x < 0 || x + w > sx) {
				throw new ShapeError("x range " + x + ".." + (x + w - 1) + " does not fit the plan's width " + sx + ".");
			}
			if (y < 0 || y + h > sy) {
				throw new ShapeError("y range " + y + ".." + (y + h - 1) + " does not fit the plan's height " + sy + ".");
			}
			if (z < 0 || z + d > sz) {
				throw new ShapeError("z range " + z + ".." + (z + d - 1) + " does not fit the plan's depth " + sz + ".");
			}
		}

		private int num(JsonObject s, String key) throws ShapeError {
			JsonElement e = s.get(key);
			if (e == null) {
				throw new ShapeError("is missing \"" + key + "\".");
			}
			if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber() || e.getAsDouble() != Math.rint(e.getAsDouble())) {
				throw new ShapeError("\"" + key + "\" must be a whole number.");
			}
			return (int) e.getAsDouble();
		}

		private boolean flag(JsonObject s, String key, boolean fallback) {
			JsonElement e = s.get(key);
			return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
		}

		private String text(JsonObject s, String key, String fallback) throws ShapeError {
			JsonElement e = s.get(key);
			if (e == null) {
				return fallback;
			}
			if (!e.isJsonPrimitive()) {
				throw new ShapeError("\"" + key + "\" must be text.");
			}
			return e.getAsString();
		}

		/** The state text for the palette key named by field {@code field}, plus the shape's own {@code state} properties and {@code extra}. */
		private String stateOf(JsonObject s, String field, String... extra) throws ShapeError {
			String keyText = text(s, field, null);
			if (keyText == null) {
				throw new ShapeError("is missing \"" + field + "\" (a palette key).");
			}
			String base = keyText.length() == 1 ? palette.get(keyText.charAt(0)) : null;
			if (base == null) {
				throw new ShapeError("the block \"" + keyText + "\" is not in the palette.");
			}
			Map<String, String> props = new LinkedHashMap<>(BlockRules.parse(base).properties());
			String own = text(s, "state", "");
			addProps(props, own);
			for (String more : extra) {
				addProps(props, more);
			}
			return new BlockRules.State(BlockRules.parse(base).id(), props).text();
		}

		private void addProps(Map<String, String> props, String list) throws ShapeError {
			if (list == null || list.isBlank()) {
				return;
			}
			try {
				props.putAll(BlockRules.parse("x[" + list + "]").properties());
			} catch (IllegalArgumentException bad) {
				throw new ShapeError("the state \"" + list + "\" is not written like facing=north,half=top.");
			}
		}

		void draw(JsonObject s) throws ShapeError {
			String type = text(s, "type", null);
			if (type == null) {
				throw new ShapeError("is missing \"type\"; use one of " + TYPES + ".");
			}
			switch (type) {
				case "box" -> box(s, false, true, true, true);
				case "hollow_box" -> box(s, true, flag(s, "floor", true), flag(s, "roof", true), true);
				case "walls" -> box(s, true, false, false, true);
				case "floor", "roof_flat" -> layer(s);
				case "roof_gable" -> gable(s);
				case "roof_pyramid" -> pyramid(s);
				case "cylinder" -> cylinder(s);
				case "line" -> line(s);
				case "door" -> door(s);
				case "window" -> window(s);
				default -> throw new ShapeError("has an unknown type \"" + type + "\"; use one of " + TYPES + ".");
			}
		}

		private void box(JsonObject s, boolean hollow, boolean floor, boolean roof, boolean unused) throws ShapeError {
			int x = num(s, "x");
			int y = num(s, "y");
			int z = num(s, "z");
			int w = num(s, "w");
			int h = num(s, "h");
			int d = num(s, "d");
			checkBox(x, y, z, w, h, d);
			String state = stateOf(s, "block");
			for (int dy = 0; dy < h; dy++) {
				for (int dz = 0; dz < d; dz++) {
					for (int dx = 0; dx < w; dx++) {
						boolean edge = dx == 0 || dx == w - 1 || dz == 0 || dz == d - 1;
						boolean solid = !hollow || edge || (floor && dy == 0) || (roof && dy == h - 1);
						if (solid) {
							put(x + dx, y + dy, z + dz, state);
						}
					}
				}
			}
		}

		private void layer(JsonObject s) throws ShapeError {
			int x = num(s, "x");
			int y = num(s, "y");
			int z = num(s, "z");
			int w = num(s, "w");
			int d = num(s, "d");
			checkBox(x, y, z, w, 1, d);
			String state = stateOf(s, "block");
			for (int dz = 0; dz < d; dz++) {
				for (int dx = 0; dx < w; dx++) {
					put(x + dx, y, z + dz, state);
				}
			}
		}

		private void gable(JsonObject s) throws ShapeError {
			int x = num(s, "x");
			int y = num(s, "y");
			int z = num(s, "z");
			int w = num(s, "w");
			int d = num(s, "d");
			String axis = text(s, "ridge", "x");
			if (!axis.equals("x") && !axis.equals("z")) {
				throw new ShapeError("\"ridge\" must be \"x\" or \"z\".");
			}
			boolean alongX = axis.equals("x");
			int across = alongX ? d : w;          // the span the slopes cover
			int along = alongX ? w : d;           // the length of the ridge
			int height = (across + 1) / 2;
			checkBox(x, y, z, w, height, d);
			String low = alongX ? "north" : "west";
			String high = alongX ? "south" : "east";
			boolean hasRidge = s.has("ridge_block");
			boolean hasFill = s.has("fill");
			String fill = hasFill ? stateOf(s, "fill") : null;
			String ridge = hasRidge ? stateOf(s, "ridge_block") : null;
			for (int t = 0; t < across; t++) {
				int h = Math.min(t, across - 1 - t);
				boolean middle = across % 2 == 1 && t == (across - 1) / 2;
				String state = middle && ridge != null ? ridge
						: stateOf(s, "block", "facing=" + (t < across - 1 - t || middle ? low : high), "half=bottom");
				for (int a = 0; a < along; a++) {
					put(alongX ? x + a : x + t, y + h, alongX ? z + t : z + a, state);
				}
				if (fill != null) {
					for (int hh = 0; hh < h; hh++) {
						put(alongX ? x : x + t, y + hh, alongX ? z + t : z, fill);
						put(alongX ? x + w - 1 : x + t, y + hh, alongX ? z + t : z + d - 1, fill);
					}
				}
			}
		}

		private void pyramid(JsonObject s) throws ShapeError {
			int x = num(s, "x");
			int y = num(s, "y");
			int z = num(s, "z");
			int w = num(s, "w");
			int d = num(s, "d");
			int rings = (Math.min(w, d) + 1) / 2;
			checkBox(x, y, z, w, rings, d);
			String top = s.has("ridge_block") ? stateOf(s, "ridge_block") : null;
			for (int k = 0; k < rings; k++) {
				int x0 = x + k;
				int x1 = x + w - 1 - k;
				int z0 = z + k;
				int z1 = z + d - 1 - k;
				boolean cap = x1 - x0 + 1 <= 2 || z1 - z0 + 1 <= 2;
				for (int zz = z0; zz <= z1; zz++) {
					for (int xx = x0; xx <= x1; xx++) {
						boolean perimeter = xx == x0 || xx == x1 || zz == z0 || zz == z1;
						if (!cap && !perimeter) {
							continue;
						}
						String facing = zz == z0 ? "north" : zz == z1 ? "south" : xx == x0 ? "west" : "east";
						String state = cap && top != null ? top : stateOf(s, "block", "facing=" + facing, "half=bottom");
						put(xx, y + k, zz, state);
					}
				}
			}
		}

		private void cylinder(JsonObject s) throws ShapeError {
			int x = num(s, "x");
			int y = num(s, "y");
			int z = num(s, "z");
			int r = num(s, "radius");
			int h = num(s, "h");
			if (r < 1) {
				throw new ShapeError("\"radius\" must be at least 1.");
			}
			checkBox(x, y, z, 2 * r + 1, h, 2 * r + 1);
			boolean hollow = flag(s, "hollow", false);
			String state = stateOf(s, "block");
			for (int dy = 0; dy < h; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					for (int dx = -r; dx <= r; dx++) {
						int d2 = dx * dx + dz * dz;
						boolean inside = d2 <= r * r + r;
						boolean core = hollow && d2 <= (r - 1) * (r - 1) + (r - 1);
						if (inside && !core) {
							put(x + r + dx, y + dy, z + r + dz, state);
						}
					}
				}
			}
		}

		private void line(JsonObject s) throws ShapeError {
			int x1 = num(s, "x1");
			int y1 = num(s, "y1");
			int z1 = num(s, "z1");
			int x2 = num(s, "x2");
			int y2 = num(s, "y2");
			int z2 = num(s, "z2");
			String state = stateOf(s, "block");
			int n = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
			for (int i = 0; i <= n; i++) {
				double f = n == 0 ? 0 : (double) i / n;
				put(x1 + (int) Math.round((x2 - x1) * f), y1 + (int) Math.round((y2 - y1) * f), z1 + (int) Math.round((z2 - z1) * f), state);
			}
		}

		private void door(JsonObject s) throws ShapeError {
			int x = num(s, "x");
			int y = num(s, "y");
			int z = num(s, "z");
			checkBox(x, y, z, 1, 2, 1);
			String facing = switch (text(s, "facing", "north")) {
				case "n", "north" -> "north";
				case "e", "east" -> "east";
				case "s", "south" -> "south";
				case "w", "west" -> "west";
				default -> throw new ShapeError("\"facing\" must be north, east, south or west.");
			};
			put(x, y, z, stateOf(s, "block", "facing=" + facing, "half=lower", "hinge=left", "open=false"));
			put(x, y + 1, z, stateOf(s, "block", "facing=" + facing, "half=upper", "hinge=left", "open=false"));
		}

		private void window(JsonObject s) throws ShapeError {
			int x = num(s, "x");
			int y = num(s, "y");
			int z = num(s, "z");
			int w = num(s, "w");
			int h = num(s, "h");
			String axis = text(s, "axis", "x");
			if (!axis.equals("x") && !axis.equals("z")) {
				throw new ShapeError("\"axis\" must be \"x\" or \"z\".");
			}
			boolean alongX = axis.equals("x");
			checkBox(x, y, z, alongX ? w : 1, h, alongX ? 1 : w);
			String state = stateOf(s, "block");
			for (int i = 0; i < w; i++) {
				for (int j = 0; j < h; j++) {
					put(alongX ? x + i : x, y + j, alongX ? z : z + i, state);
				}
			}
		}

		Blueprint toBlueprint(String name, int maxCells) throws BlueprintException {
			Map<String, Character> keys = new HashMap<>();
			Map<Character, String> used = new LinkedHashMap<>();
			for (Map.Entry<Character, String> e : palette.entrySet()) {
				keys.put(e.getValue(), e.getKey());
			}
			List<Cell> cells = new ArrayList<>();
			int spare = 0;
			for (int y = 0; y < sy; y++) {
				for (int z = 0; z < sz; z++) {
					for (int x = 0; x < sx; x++) {
						String state = grid[index(x, y, z)];
						if (state == null) {
							continue;
						}
						Character key = keys.get(state);
						if (key == null) {
							while (spare < SPARE_KEYS.length() && palette.containsKey(SPARE_KEYS.charAt(spare))) {
								spare++;
							}
							if (spare >= SPARE_KEYS.length()) {
								throw new BlueprintException("The plan uses too many different block states; simplify it.");
							}
							key = SPARE_KEYS.charAt(spare++);
							keys.put(state, key);
						}
						used.put(key, state);
						cells.add(new Cell(x, y, z, key));
					}
				}
			}
			return Blueprint.create(name, sx, sy, sz, used, cells, "ai", maxCells);
		}
	}
}
