package com.villaging.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Decides which tool calls are risky enough that the king must say yes first. Pure logic. */
public final class RiskPolicy {
	public static final int MASS_VOLUME = 64;

	public enum Category {
		LAVA, FIRE, EXPLOSIVE, MASS_DESTRUCTION;

		/** The lowercase name used in tool arguments and in the villager's saved ask preferences. */
		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private static final Set<String> ITEM_TOOLS = Set.of("place_block", "use_item", "use_block");

	private RiskPolicy() {
	}

	public static Optional<Category> classify(String toolName, JsonObject args, String heldItemId) {
		if (toolName.equals("break_area")) {
			return volume(args) > MASS_VOLUME ? Optional.of(Category.MASS_DESTRUCTION) : Optional.empty();
		}
		if (!ITEM_TOOLS.contains(toolName)) {
			return Optional.empty();
		}
		String item = text(args, "item");
		if (item == null) {
			item = heldItemId;
		}
		if (item == null) {
			return Optional.empty();
		}
		return switch (item.replace("minecraft:", "")) {
			case "lava_bucket" -> Optional.of(Category.LAVA);
			case "flint_and_steel", "fire_charge" -> Optional.of(Category.FIRE);
			case "tnt", "tnt_minecart", "end_crystal", "respawn_anchor" -> Optional.of(Category.EXPLOSIVE);
			default -> Optional.empty();
		};
	}

	/** True unless the call is not risky or the king switched asking off for its category. */
	public static boolean mustAsk(Optional<Category> category, Set<String> askOff) {
		return category.isPresent() && !askOff.contains(category.get().key());
	}

	private static String text(JsonObject args, String key) {
		JsonElement e = args.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static long volume(JsonObject args) {
		long volume = 1;
		for (String axis : new String[] {"x", "y", "z"}) {
			JsonElement a = args.get(axis + "1");
			JsonElement b = args.get(axis + "2");
			if (a == null || b == null || !a.isJsonPrimitive() || !b.isJsonPrimitive()) {
				return 0;
			}
			volume *= Math.abs(Math.round(a.getAsDouble()) - Math.round(b.getAsDouble())) + 1;
		}
		return volume;
	}

	/** Blocks a felled tree is assumed to cost when counting destruction. */
	public static final int TREE_VOLUME = 64;

	/** How many blocks a call can break, for the running total that triggers the mass-destruction question. */
	public static long destructionVolume(String toolName, JsonObject args) {
		return switch (toolName) {
			case "mine_block" -> 1L;
			case "break_area" -> volume(args);
			case "chop_tree" -> TREE_VOLUME;
			default -> 0L;
		};
	}

	/** Using these blocks explodes: a bed outside the Overworld, or a respawn anchor. */
	public static Optional<Category> classifyBlockUse(String blockPath, boolean inOverworld) {
		if (blockPath.equals("respawn_anchor") || (blockPath.endsWith("_bed") && !inOverworld)) {
			return Optional.of(Category.EXPLOSIVE);
		}
		return Optional.empty();
	}
}
