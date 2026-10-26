package com.villaging.king;

import java.util.Locale;

/** Describes what the king is doing right now, so the villager can resolve "here" and "that block". */
public final class KingContext {
	/** The block the king is looking at. */
	public record LookedAt(String blockName, int x, int y, int z) {
	}

	private KingContext() {
	}

	public static String describe(String playerName, double x, double y, double z, String facing, String heldItem,
			LookedAt lookedAt) {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format(Locale.ROOT, "The king, %s, is at (%.1f, %.1f, %.1f), facing %s, holding %s. ",
				playerName, x, y, z, facing, heldItem));
		if (lookedAt == null) {
			sb.append("The king is not looking at any block.");
		} else {
			sb.append(String.format(Locale.ROOT, "The king is looking at %s at (%d, %d, %d); \"that block\" or \"this\" means it.",
					lookedAt.blockName(), lookedAt.x(), lookedAt.y(), lookedAt.z()));
		}
		return sb.toString();
	}
}
