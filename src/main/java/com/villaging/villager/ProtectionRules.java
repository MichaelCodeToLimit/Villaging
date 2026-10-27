package com.villaging.villager;

/** The pure yes/no rules behind {@link Protection}. */
public final class ProtectionRules {
	private ProtectionRules() {
	}

	/** Unbreakable blocks (bedrock, barriers) can only be broken the way a creative-mode player breaks them. */
	public static boolean canBreakUnbreakable(boolean allowBreakBedrock) {
		return allowBreakBedrock;
	}

	/** Villagers never hurt players, or pets and other owned entities, whatever they are told. */
	public static boolean isForbiddenTarget(boolean isPlayer, boolean isTamedOrOwned) {
		return isPlayer || isTamedOrOwned;
	}
}
