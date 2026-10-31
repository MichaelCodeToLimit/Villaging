package com.villaging.blueprint;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ready-made designs the AI can adjust: cottage, tower, wall, bridge and castle. Each is written as a shape plan and
 * compiled by {@link ShapePlan}, so it passes the same checks as an AI design. Pure.
 */
public final class Templates {
	/** Width and depth are the footprint (the wall's width is its length); height is the wall height (the bridge's deck height). */
	public record Params(int width, int depth, int height, String style, String roof) {
	}

	/** Smallest and largest value each dimension may take; values outside are moved to the nearest bound. */
	public record Limits(int minWidth, int maxWidth, int minDepth, int maxDepth, int minHeight, int maxHeight) {
	}

	private record Style(String wall, String floor, String stairs, String ridge, String door, String glass, String fence) {
	}

	private static final Map<String, Style> STYLES = new LinkedHashMap<>();
	private static final Map<String, Limits> LIMITS = new LinkedHashMap<>();
	private static final Map<String, Params> DEFAULTS = new LinkedHashMap<>();
	private static final List<String> ROOFS = List.of("gable", "flat", "pyramid");

	static {
		STYLES.put("stone", new Style("minecraft:stone_bricks", "minecraft:cobblestone", "minecraft:stone_brick_stairs",
				"minecraft:stone_bricks", "minecraft:oak_door", "minecraft:glass_pane", "minecraft:stone_brick_wall"));
		STYLES.put("wood", new Style("minecraft:oak_planks", "minecraft:spruce_planks", "minecraft:oak_stairs",
				"minecraft:stripped_oak_log", "minecraft:oak_door", "minecraft:glass_pane", "minecraft:oak_fence"));
		STYLES.put("brick", new Style("minecraft:bricks", "minecraft:stone_bricks", "minecraft:brick_stairs",
				"minecraft:bricks", "minecraft:oak_door", "minecraft:glass_pane", "minecraft:brick_wall"));
		STYLES.put("sandstone", new Style("minecraft:sandstone", "minecraft:smooth_sandstone", "minecraft:sandstone_stairs",
				"minecraft:cut_sandstone", "minecraft:birch_door", "minecraft:glass_pane", "minecraft:sandstone_wall"));
		STYLES.put("dark", new Style("minecraft:dark_oak_planks", "minecraft:dark_oak_planks", "minecraft:dark_oak_stairs",
				"minecraft:dark_oak_log", "minecraft:dark_oak_door", "minecraft:glass_pane", "minecraft:dark_oak_fence"));

		LIMITS.put("cottage", new Limits(5, 16, 5, 16, 3, 8));
		LIMITS.put("tower", new Limits(4, 12, 4, 12, 6, 40));
		LIMITS.put("wall", new Limits(4, 48, 2, 5, 3, 12));
		LIMITS.put("bridge", new Limits(5, 48, 3, 7, 3, 12));
		LIMITS.put("castle", new Limits(24, 48, 24, 48, 8, 20));

		DEFAULTS.put("cottage", new Params(7, 7, 4, "stone", "gable"));
		DEFAULTS.put("tower", new Params(5, 5, 12, "stone", "flat"));
		DEFAULTS.put("wall", new Params(12, 3, 5, "stone", "flat"));
		DEFAULTS.put("bridge", new Params(12, 3, 4, "wood", "flat"));
		DEFAULTS.put("castle", new Params(32, 32, 10, "stone", "flat"));
	}

	private Templates() {
	}

	public static List<String> ids() {
		return List.copyOf(LIMITS.keySet());
	}

	public static Limits limits(String id) {
		return LIMITS.get(id);
	}

	public static Params defaults(String id) {
		return DEFAULTS.get(id);
	}

	public static List<String> styles() {
		return List.copyOf(STYLES.keySet());
	}

	public static List<String> roofs() {
		return ROOFS;
	}

	/** The parameter ranges of a template in plain words, e.g. for the AI. */
	public static String bounds(String id) {
		Limits l = LIMITS.get(id);
		String w = id.equals("wall") || id.equals("bridge") ? "length (width)" : "width";
		return w + " " + l.minWidth() + ".." + l.maxWidth() + ", depth " + l.minDepth() + ".." + l.maxDepth() + ", height "
				+ l.minHeight() + ".." + l.maxHeight();
	}

	public static Blueprint build(String id, String name, Params params, int maxCells) throws BlueprintException {
		Limits l = LIMITS.get(id == null ? "" : id);
		if (l == null) {
			throw new BlueprintException("There is no template \"" + id + "\"; the templates are: " + String.join(", ", ids()) + ".");
		}
		String styleName = params.style() == null ? DEFAULTS.get(id).style() : params.style().toLowerCase(Locale.ROOT);
		Style style = STYLES.get(styleName);
		if (style == null) {
			throw new BlueprintException("There is no style \"" + params.style() + "\"; the styles are: " + String.join(", ", styles()) + ".");
		}
		String roof = params.roof() == null ? "gable" : params.roof().toLowerCase(Locale.ROOT);
		if (!ROOFS.contains(roof)) {
			roof = "gable";
		}
		int w = clamp(params.width(), l.minWidth(), l.maxWidth());
		int d = clamp(params.depth(), l.minDepth(), l.maxDepth());
		int h = clamp(params.height(), l.minHeight(), l.maxHeight());
		Plan plan = switch (id) {
			case "cottage" -> cottage(style, w, d, h, roof);
			case "tower" -> tower(style, w, d, h);
			case "wall" -> wall(style, w, d, h);
			case "bridge" -> bridge(style, w, d, h);
			default -> castle(style, w, d, h);
		};
		return ShapePlan.compile(name, plan.toJson(), maxCells);
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	// --- plans ---

	/** Collects shapes and the palette (W wall, F floor, R roof stairs, B ridge/roof block, D door, G glass, N fence, L torch, H ladder, T trapdoor). */
	private static final class Plan {
		private final int sx;
		private final int sy;
		private final int sz;
		private final JsonObject palette = new JsonObject();
		private final JsonArray shapes = new JsonArray();

		Plan(Style s, int sx, int sy, int sz) {
			this.sx = sx;
			this.sy = sy;
			this.sz = sz;
			palette.addProperty("W", s.wall());
			palette.addProperty("F", s.floor());
			palette.addProperty("R", s.stairs());
			palette.addProperty("B", s.ridge());
			palette.addProperty("D", s.door());
			palette.addProperty("G", s.glass());
			palette.addProperty("N", s.fence());
			palette.addProperty("L", "minecraft:torch");
			palette.addProperty("H", "minecraft:ladder");
			palette.addProperty("T", "minecraft:oak_trapdoor");
		}

		/** Adds a shape from alternating names and values (Integer, Boolean or String). */
		Plan add(String type, String block, Object... fields) {
			JsonObject o = new JsonObject();
			o.addProperty("type", type);
			if (block != null) {
				o.addProperty("block", block);
			}
			for (int i = 0; i < fields.length; i += 2) {
				String key = (String) fields[i];
				Object v = fields[i + 1];
				if (v instanceof Integer n) {
					o.addProperty(key, n);
				} else if (v instanceof Boolean b) {
					o.addProperty(key, b);
				} else {
					o.addProperty(key, String.valueOf(v));
				}
			}
			shapes.add(o);
			return this;
		}

		Plan box(String block, int x, int y, int z, int w, int h, int d, Object... more) {
			Object[] f = new Object[] {"x", x, "y", y, "z", z, "w", w, "h", h, "d", d};
			return add("box", block, concat(f, more));
		}

		JsonObject toJson() {
			JsonObject plan = new JsonObject();
			JsonArray size = new JsonArray();
			size.add(sx);
			size.add(sy);
			size.add(sz);
			plan.add("size", size);
			plan.add("palette", palette);
			plan.add("shapes", shapes);
			return plan;
		}

		private static Object[] concat(Object[] a, Object[] b) {
			Object[] out = new Object[a.length + b.length];
			System.arraycopy(a, 0, out, 0, a.length);
			System.arraycopy(b, 0, out, a.length, b.length);
			return out;
		}
	}

	private static int rise(String roof, int w, int d) {
		return roof.equals("flat") ? 1 : (Math.min(w, d) + 1) / 2;
	}

	/** Foundation layer y=0, walls y=1..h, roof from y=h+1. The door is in the south-facing front (z=0) wall. */
	private static Plan cottage(Style s, int w, int d, int h, String roof) {
		Plan p = new Plan(s, w, 1 + h + rise(roof, w, d), d);
		p.add("floor", "F", "x", 0, "y", 0, "z", 0, "w", w, "d", d);
		p.add("walls", "W", "x", 0, "y", 1, "z", 0, "w", w, "h", h, "d", d);
		p.add("window", "G", "x", 0, "y", 2, "z", d / 2, "w", 1, "h", 1, "axis", "z");
		p.add("window", "G", "x", w - 1, "y", 2, "z", d / 2, "w", 1, "h", 1, "axis", "z");
		p.add("window", "G", "x", w / 2, "y", 2, "z", d - 1, "w", 1, "h", 1, "axis", "x");
		if (w >= 9) {
			p.add("window", "G", "x", 2, "y", 2, "z", 0, "w", 1, "h", 1, "axis", "x");
			p.add("window", "G", "x", w - 3, "y", 2, "z", 0, "w", 1, "h", 1, "axis", "x");
		}
		p.add("door", "D", "x", w / 2, "y", 1, "z", 0, "facing", "north");
		switch (roof) {
			case "pyramid" -> p.add("roof_pyramid", "R", "x", 0, "y", h + 1, "z", 0, "w", w, "d", d, "ridge_block", "B");
			case "flat" -> p.add("roof_flat", "B", "x", 0, "y", h + 1, "z", 0, "w", w, "d", d);
			default -> p.add("roof_gable", "R", "x", 0, "y", h + 1, "z", 0, "w", w, "d", d, "ridge", w >= d ? "x" : "z",
					"ridge_block", "B", "fill", "W");
		}
		return p.box("L", 1, 1, 1, 1, 1, 1);
	}

	/** A tall square-ish tower with a ladder, a flat roof with a trapdoor above the ladder, and a parapet. */
	private static Plan tower(Style s, int w, int d, int h) {
		Plan p = new Plan(s, w, h + 3, d);
		p.add("floor", "F", "x", 0, "y", 0, "z", 0, "w", w, "d", d);
		p.add("walls", "W", "x", 0, "y", 1, "z", 0, "w", w, "h", h, "d", d);
		p.add("window", "G", "x", w - 1, "y", 3, "z", d / 2, "w", 1, "h", 1, "axis", "z");
		p.add("window", "G", "x", w - 1, "y", h - 2, "z", d / 2, "w", 1, "h", 1, "axis", "z");
		p.add("window", "G", "x", w / 2, "y", 3, "z", d - 1, "w", 1, "h", 1, "axis", "x");
		p.add("window", "G", "x", w / 2, "y", h - 2, "z", d - 1, "w", 1, "h", 1, "axis", "x");
		p.add("door", "D", "x", w / 2, "y", 1, "z", 0, "facing", "north");
		p.add("roof_flat", "B", "x", 0, "y", h + 1, "z", 0, "w", w, "d", d);
		p.add("walls", "W", "x", 0, "y", h + 2, "z", 0, "w", w, "h", 1, "d", d);
		p.box("H", 1, 1, d / 2, 1, h, 1, "state", "facing=east");
		p.box("T", 1, h + 1, d / 2, 1, 1, 1, "state", "facing=east,half=top,open=false");
		return p.box("L", w - 2, 1, d - 2, 1, 1, 1);
	}

	/** A solid wall with a walkway on top, merlons on the outer edge (z=0) and two torches. */
	private static Plan wall(Style s, int w, int d, int h) {
		Plan p = new Plan(s, w, h + 1, d);
		p.box("W", 0, 0, 0, w, h, d);
		for (int x = 0; x < w; x += 2) {
			p.box("W", x, h, 0, 1, 1, 1);
		}
		p.box("L", 1, h, d - 1, 1, 1, 1);
		return p.box("L", w - 2, h, d - 1, 1, 1, 1);
	}

	/** A deck at height {@code h - 1} with railings on both edges and pillars to the ground. */
	private static Plan bridge(Style s, int w, int d, int h) {
		Plan p = new Plan(s, w, h + 1, d);
		p.add("floor", "F", "x", 0, "y", h - 1, "z", 0, "w", w, "d", d);
		p.add("line", "N", "x1", 0, "y1", h, "z1", 0, "x2", w - 1, "y2", h, "z2", 0);
		p.add("line", "N", "x1", 0, "y1", h, "z1", d - 1, "x2", w - 1, "y2", h, "z2", d - 1);
		for (int x = 0; x < w; x += 8) {
			pillar(p, x, h, d);
		}
		pillar(p, w - 1, h, d);
		return p;
	}

	private static void pillar(Plan p, int x, int h, int d) {
		p.box("W", x, 0, 0, 1, h - 1, 1);
		p.box("W", x, 0, d - 1, 1, h - 1, 1);
	}

	/** A curtain wall with a three-wide gate in the south wall, four corner towers and a keep with a door. */
	private static Plan castle(Style s, int w, int d, int h) {
		Plan p = new Plan(s, w, h + 4, d);
		p.add("floor", "F", "x", 0, "y", 0, "z", 0, "w", w, "d", d);
		int gate = w / 2 - 1;                     // first gate column, the gate is three wide
		p.box("W", 0, 1, 0, w, h, 1);
		p.box("W", 0, 1, 1, 1, h, d - 2);
		p.box("W", w - 1, 1, 1, 1, h, d - 2);
		p.box("W", 0, 1, d - 1, gate, h, 1);
		p.box("W", gate + 3, 1, d - 1, w - gate - 3, h, 1);
		p.add("walls", "W", "x", 0, "y", h + 1, "z", 0, "w", w, "h", 1, "d", d);
		int[][] corners = {{0, 0}, {w - 5, 0}, {0, d - 5}, {w - 5, d - 5}};
		for (int[] c : corners) {
			p.add("hollow_box", "W", "x", c[0], "y", 1, "z", c[1], "w", 5, "h", h + 3, "d", 5);
		}
		int kw = clamp(w / 3, 7, 13);
		int kd = clamp(d / 3, 7, 13);
		int kx = (w - kw) / 2;
		int kz = (d - kd) / 2;
		int keepHeight = h + 1;
		p.add("walls", "W", "x", kx, "y", 1, "z", kz, "w", kw, "h", keepHeight, "d", kd);
		p.add("roof_flat", "B", "x", kx, "y", keepHeight + 1, "z", kz, "w", kw, "d", kd);
		p.add("door", "D", "x", kx + kw / 2, "y", 1, "z", kz + kd - 1, "facing", "south");
		p.box("L", kx + 1, 1, kz + 1, 1, 1, 1);
		return p.box("L", gate - 1, 1, d - 2, 1, 1, 1);
	}
}
