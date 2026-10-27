package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SmartKindTest {
	@Test
	void keysAndLabelsReadWell() {
		assertEquals("iron_golem", SmartKind.IRON_GOLEM.key());
		assertEquals("iron golem", SmartKind.IRON_GOLEM.label());
		assertEquals("copper golem", SmartKind.COPPER_GOLEM.label());
		assertEquals("villager", SmartKind.VILLAGER.label());
	}

	@Test
	void findsAKindByItsKey() {
		assertEquals(Optional.of(SmartKind.COPPER_GOLEM), SmartKind.fromKey("copper_golem"));
		assertEquals(Optional.empty(), SmartKind.fromKey("any"));
		assertEquals(Optional.empty(), SmartKind.fromKey("creeper"));
	}

	@Test
	void everyKindHasAPersonalityLine() {
		for (SmartKind kind : SmartKind.values()) {
			assertTrue(kind.personality().length() > 20, kind.key());
		}
	}
}
