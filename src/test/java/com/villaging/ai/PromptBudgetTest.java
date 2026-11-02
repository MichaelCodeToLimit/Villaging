package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.villaging.ai.tools.AbilityTools;
import com.villaging.villager.VillagerBrain;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PromptBudgetTest {
	private static final VillagerBrain BRAIN = VillagerBrain.DEFAULT.makeSmart("Alma");
	private static final String MARK = "YOUR SITUATION";

	@BeforeAll
	static void tools() {
		AbilityTools.registerAll();
	}

	private static String prompt(String state, String king) {
		return SystemPrompt.build(BRAIN, SmartKind.VILLAGER, state, king, "King");
	}

	@Test
	void promptPlusToolsStaysUnderBudget() {
		int total = prompt("You are at (0, 64, 0). Inventory: empty.", "Steve at (1, 64, 2).").length();
		for (ToolDef tool : Tools.all()) {
			total += tool.name().length() + tool.description().length() + tool.parametersSchema().toString().length();
		}
		assertTrue(total <= 22_300, "prompt plus tool list is " + total + " characters");
	}

	@Test
	void fixedPartIsIdenticalForDifferentSituations() {
		String a = prompt("state one", "king one");
		String b = prompt("a quite different state", "another king context");
		assertTrue(a.contains(MARK));
		assertEquals(a.substring(0, a.indexOf(MARK)), b.substring(0, b.indexOf(MARK)));
	}

	@Test
	void noPerToolDescriptionLines() {
		String p = prompt("s", "k");
		for (ToolDef tool : Tools.all()) {
			assertFalse(p.contains("- " + tool.name() + ": "), "tool line for " + tool.name());
		}
	}
}
