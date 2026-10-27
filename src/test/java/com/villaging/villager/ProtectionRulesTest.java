package com.villaging.villager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProtectionRulesTest {
	@Test
	void unbreakableBlocksNeedTheBedrockSetting() {
		assertFalse(ProtectionRules.canBreakUnbreakable(false));
		assertTrue(ProtectionRules.canBreakUnbreakable(true));
	}

	@Test
	void playersAreNeverValidTargets() {
		assertTrue(ProtectionRules.isForbiddenTarget(true, false));
	}

	@Test
	void tamedOrOwnedEntitiesAreNeverValidTargets() {
		assertTrue(ProtectionRules.isForbiddenTarget(false, true));
	}

	@Test
	void plainHostileMobsAreAllowedTargets() {
		assertFalse(ProtectionRules.isForbiddenTarget(false, false));
	}
}
