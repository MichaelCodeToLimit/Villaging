package com.villaging.guide;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.villaging.ai.ToolDef;
import com.villaging.ai.Tools;
import com.villaging.ai.tools.AbilityTools;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GuideContentTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static String everything() {
		StringBuilder sb = new StringBuilder();
		for (GuideContent.Page page : GuideContent.pages()) {
			sb.append(page.title()).append('\n').append(page.body()).append('\n');
		}
		return sb.toString().toLowerCase(Locale.ROOT);
	}

	@Test
	void everyAbilityIsExplainedSoNewOnesCannotBeForgotten() {
		String text = everything();
		for (ToolDef tool : Tools.all()) {
			assertTrue(text.contains(tool.name()), "the guide does not mention " + tool.name());
		}
	}

	@Test
	void coversTheBasicsAHurriedPlayerNeeds() {
		String text = everything();
		for (String needed : new String[] {"/villaging key", "/villaging guide", "right-click", "sneak", "crown", "king",
				"golem", "recruit", "pop-up", "never hurt"}) {
			assertTrue(text.contains(needed), "the guide does not mention " + needed);
		}
	}

	@Test
	void pagesAreShortEnoughToReadOnOneScreen() {
		assertTrue(GuideContent.pages().size() >= 8);
		for (GuideContent.Page page : GuideContent.pages()) {
			assertFalse(page.title().isBlank());
			assertFalse(page.body().isBlank(), page.title());
			assertTrue(page.body().length() <= 650, page.title() + " is " + page.body().length() + " characters");
		}
	}
}
