package com.villaging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SmokeTest {
	@Test
	void modIdIsVillaging() {
		assertEquals("villaging", Villaging.MOD_ID);
	}
}
