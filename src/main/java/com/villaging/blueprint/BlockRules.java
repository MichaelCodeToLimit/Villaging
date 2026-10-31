package com.villaging.blueprint;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Pure rules about block state strings: parsing, which blocks a helper may never place, and what a cell costs in items. */
public final class BlockRules {
	/** A block id (always with a namespace) and its state properties, e.g. {@code minecraft:oak_door} with {@code half=upper}. */
	public record State(String id, Map<String, String> properties) {
		/** The state as text again, properties in the order they were given. */
		public String text() {
			if (properties.isEmpty()) {
				return id;
			}
			StringBuilder sb = new StringBuilder(id).append('[');
			boolean first = true;
			for (Map.Entry<String, String> e : properties.entrySet()) {
				if (!first) {
					sb.append(',');
				}
				first = false;
				sb.append(e.getKey()).append('=').append(e.getValue());
			}
			return sb.append(']').toString();
		}
	}

	/** The item that places a block and how many of it one cell takes. */
	public record ItemCost(String item, int count) {
	}

	private static final Pattern ID = Pattern.compile("[a-z0-9_.\\-]+(:[a-z0-9_.\\-/]+)?");
	private static final Pattern KEY = Pattern.compile("[a-z0-9_]+");
	private static final Pattern VALUE = Pattern.compile("[a-z0-9_]+");

	private static final Map<String, String> BANNED = new LinkedHashMap<>();

	static {
		String[][] reasons = {
				{"tnt", "explosives are not allowed in blueprints"},
				{"lava", "lava is not allowed in blueprints"},
				{"fire", "fire is not allowed in blueprints"},
				{"soul_fire", "fire is not allowed in blueprints"},
				{"bedrock", "bedrock cannot be placed"},
				{"barrier", "barriers cannot be placed"},
				{"light", "light blocks cannot be placed"},
				{"command_block", "command blocks cannot be placed"},
				{"chain_command_block", "command blocks cannot be placed"},
				{"repeating_command_block", "command blocks cannot be placed"},
				{"structure_block", "structure blocks cannot be placed"},
				{"structure_void", "structure voids cannot be placed"},
				{"jigsaw", "jigsaw blocks cannot be placed"},
				{"spawner", "spawners cannot be placed"},
				{"trial_spawner", "spawners cannot be placed"},
				{"vault", "vaults cannot be placed"},
				{"end_portal", "portals cannot be placed"},
				{"end_portal_frame", "portal frames cannot be placed"},
				{"end_gateway", "portals cannot be placed"},
				{"nether_portal", "portals cannot be placed"},
				{"respawn_anchor", "explosives are not allowed in blueprints"},
				{"water", "fluids cannot be placed"},
				{"bubble_column", "fluids cannot be placed"},
				{"piston_head", "piston parts cannot be placed on their own"},
				{"moving_piston", "piston parts cannot be placed on their own"},
				{"air", "air is not a block to place"},
				{"cave_air", "air is not a block to place"},
				{"void_air", "air is not a block to place"},
		};
		for (String[] r : reasons) {
			BANNED.put(r[0], r[1]);
		}
	}

	private BlockRules() {
	}

	/**
	 * Parses {@code oak_stairs[facing=north,half=bottom]} (namespace optional).
	 *
	 * @throws IllegalArgumentException with the offending text when it is not a block state
	 */
	public static State parse(String blockState) {
		if (blockState == null || blockState.isBlank()) {
			throw new IllegalArgumentException("A block is empty; use an id like minecraft:stone.");
		}
		String text = blockState.strip();
		String id = text;
		Map<String, String> properties = new LinkedHashMap<>();
		int open = text.indexOf('[');
		if (open >= 0) {
			if (!text.endsWith("]")) {
				throw new IllegalArgumentException("The block \"" + text + "\" is missing a closing ].");
			}
			id = text.substring(0, open);
			String inside = text.substring(open + 1, text.length() - 1);
			if (!inside.isBlank()) {
				for (String pair : inside.split(",")) {
					int eq = pair.indexOf('=');
					String key = eq < 0 ? "" : pair.substring(0, eq).strip();
					String value = eq < 0 ? "" : pair.substring(eq + 1).strip();
					if (!KEY.matcher(key).matches() || !VALUE.matcher(value).matches()) {
						throw new IllegalArgumentException("The block \"" + text + "\" has a bad property \"" + pair.strip()
								+ "\"; use name=value.");
					}
					properties.put(key, value);
				}
			}
		}
		if (!ID.matcher(id).matches()) {
			throw new IllegalArgumentException("\"" + text + "\" is not a block id; use an id like minecraft:stone.");
		}
		return new State(id.contains(":") ? id : "minecraft:" + id, properties);
	}

	/** Null when a helper may place the block, otherwise a short reason it may not. */
	public static String denyReason(String blockId) {
		String name = blockId.startsWith("minecraft:") ? blockId.substring("minecraft:".length()) : blockId;
		String reason = BANNED.get(name);
		return reason == null ? null : name + ": " + reason;
	}

	/** The item that places this block and how many of it this one cell needs (0 for the second half of a door or bed). */
	public static ItemCost cost(State state) {
		String name = state.id().startsWith("minecraft:") ? state.id().substring("minecraft:".length()) : null;
		String namespace = name == null ? state.id().substring(0, state.id().indexOf(':') + 1) : "minecraft:";
		if (name == null) {
			name = state.id().substring(namespace.length());
		}
		Map<String, String> p = state.properties();
		if ("upper".equals(p.get("half")) && isTwoCellBlock(name) || "head".equals(p.get("part"))) {
			return new ItemCost(namespace + name, 0);
		}
		int count = "double".equals(p.get("type")) && name.endsWith("_slab") ? 2 : 1;
		return new ItemCost(namespace + itemName(name), count);
	}

	/** Doors and tall plants have an upper half that comes with the lower one. */
	private static boolean isTwoCellBlock(String name) {
		return name.endsWith("_door") || name.equals("tall_grass") || name.equals("large_fern") || name.equals("sunflower")
				|| name.equals("lilac") || name.equals("rose_bush") || name.equals("peony") || name.equals("tall_seagrass");
	}

	private static String itemName(String blockName) {
		switch (blockName) {
			case "wall_torch":
				return "torch";
			case "soul_wall_torch":
				return "soul_torch";
			case "redstone_wall_torch":
				return "redstone_torch";
			default:
				break;
		}
		for (String kind : new String[] {"_wall_sign", "_wall_hanging_sign", "_wall_banner"}) {
			if (blockName.endsWith(kind)) {
				return blockName.substring(0, blockName.length() - kind.length()) + kind.replace("_wall", "");
			}
		}
		return blockName;
	}
}
