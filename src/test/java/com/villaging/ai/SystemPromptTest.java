package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.villaging.king.KingContext;
import com.villaging.villager.VillagerBrain;
import org.junit.jupiter.api.Test;

class SystemPromptTest {
	private static final VillagerBrain BRAIN = VillagerBrain.DEFAULT.makeSmart("Alma");
	private static final String KING = KingContext.describe("Steve", 1.5, 64, -3.5, "north", "diamond pickaxe",
			new KingContext.LookedAt("stone", 10, 63, -4));

	private static String prompt() {
		return SystemPrompt.build(BRAIN, "You are at (0, 64, 0). Inventory: empty.", KING);
	}

	@Test
	void includesNameAndState() {
		String p = prompt();
		assertTrue(p.contains("Alma"));
		assertTrue(p.contains("You are at (0, 64, 0)"));
	}

	@Test
	void mentionsAllRegisteredTools() {
		String p = prompt();
		for (ToolDef tool : Tools.all()) {
			assertTrue(p.contains(tool.name()), "missing " + tool.name());
		}
	}

	@Test
	void teachesTheAbilityRulesAndLimits() {
		com.villaging.ai.tools.AbilityTools.registerAll();
		String p = prompt();
		assertTrue(p.contains("never hurt a player or a pet"), "missing the player and pet rule");
		assertTrue(p.contains("risky"), "missing the risky-act rule");
		assertTrue(p.contains("run_script"), "missing the script advice");
		assertTrue(p.contains("look_around"), "missing the look-first advice");
		assertTrue(p.contains("take_from_king"), "missing the taking rule");
		assertTrue(p.contains("ask"), "missing the question tool");
		assertTrue(p.contains("cannot do that yet"), "must stay honest about limits");
	}

	@Test
	void includesKingContextAndLookedAtBlock() {
		String p = prompt();
		assertTrue(p.contains("Steve"));
		assertTrue(p.contains("stone at (10, 63, -4)"));
		assertTrue(p.contains("diamond pickaxe"));
	}

	@Test
	void instructsFreeFormUnderstandingAndHonestRefusal() {
		String p = prompt();
		assertTrue(p.contains("any phrasing"));
		assertTrue(p.contains("typos"));
		assertTrue(p.contains("one short clarifying question"));
		assertTrue(p.contains("Never claim"));
		assertTrue(p.contains("cannot do that yet"));
		assertTrue(p.contains("that block"));
	}

	@Test
	void kingContextSaysNothingLookedAtWhenNoBlock() {
		String text = KingContext.describe("Steve", 0, 64, 0, "south", "nothing", null);
		assertTrue(text.contains("not looking at any block"));
	}

	@Test
	void kingContextDescribesLookedAtBlockCoordinates() {
		assertTrue(KING.contains("looking at stone at (10, 63, -4)"));
	}
}
