package com.villaging.king;

import static com.villaging.king.ClickRule.Action.CROWN;
import static com.villaging.king.ClickRule.Action.OPEN_CHAT;
import static com.villaging.king.ClickRule.Action.VANILLA;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ClickRuleTest {
	// decide(mayCommand, smart, villager, sneaking, crown)

	@Test
	void plainRightClickTalksToASmartCreature() {
		assertEquals(OPEN_CHAT, ClickRule.decide(true, true, true, false, false));
		assertEquals(OPEN_CHAT, ClickRule.decide(true, true, false, false, false));
		assertEquals(OPEN_CHAT, ClickRule.decide(true, true, true, false, true));
	}

	@Test
	void sneakRightClickOnASmartCreatureKeepsVanillaBehaviour() {
		assertEquals(VANILLA, ClickRule.decide(true, true, true, true, false));
		assertEquals(VANILLA, ClickRule.decide(true, true, false, true, false));
	}

	@Test
	void anOrdinaryVillagerNeedsSneakToTalkSoTradingStillWorks() {
		assertEquals(VANILLA, ClickRule.decide(true, false, true, false, false));
		assertEquals(OPEN_CHAT, ClickRule.decide(true, false, true, true, false));
		assertEquals(OPEN_CHAT, ClickRule.decide(true, false, true, true, true));
	}

	@Test
	void theCrownMakesAnOrdinaryCreatureSmart() {
		assertEquals(CROWN, ClickRule.decide(true, false, true, false, true));
		assertEquals(CROWN, ClickRule.decide(true, false, false, false, true));
		assertEquals(CROWN, ClickRule.decide(true, false, false, true, true));
	}

	@Test
	void anOrdinaryGolemIsLeftAloneWithoutTheCrown() {
		assertEquals(VANILLA, ClickRule.decide(true, false, false, true, false));
		assertEquals(VANILLA, ClickRule.decide(true, false, false, false, false));
	}

	@Test
	void someoneWhoCannotCommandNeverTalksButTheCrownStillRefusesThem() {
		assertEquals(VANILLA, ClickRule.decide(false, true, true, false, false));
		assertEquals(VANILLA, ClickRule.decide(false, true, true, true, false));
		assertEquals(CROWN, ClickRule.decide(false, false, true, false, true));
	}
}
