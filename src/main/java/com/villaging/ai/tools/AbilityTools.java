package com.villaging.ai.tools;

/** Registers every ability group's tool definitions. Safe to call more than once. */
public final class AbilityTools {
	private AbilityTools() {
	}

	public static void registerAll() {
		AskTools.register();
	}
}
