package com.villaging.king;

/** What right-clicking a villager or golem does. Pure, so the rules are easy to test. */
public final class ClickRule {
	public enum Action {
		/** Open the chat box. */
		OPEN_CHAT,
		/** The crown makes it smart (or refuses a non-king). */
		CROWN,
		/** Leave the click to vanilla (trading, healing a golem...). */
		VANILLA
	}

	private ClickRule() {
	}

	/**
	 * @param mayCommand the player is the king, or the world has no king yet
	 * @param smart      the creature is already smart
	 * @param villager   the creature is a villager (not a golem)
	 * @param sneaking   the player is sneaking
	 * @param crown      the player holds the crown
	 */
	public static Action decide(boolean mayCommand, boolean smart, boolean villager, boolean sneaking, boolean crown) {
		if (mayCommand) {
			if (smart) {
				// A smart creature answers a plain click; sneaking gives back vanilla trading or healing.
				return sneaking ? Action.VANILLA : Action.OPEN_CHAT;
			}
			if (sneaking && villager) {
				return Action.OPEN_CHAT;
			}
		}
		return crown ? Action.CROWN : Action.VANILLA;
	}
}
