package com.villaging.villager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NamePickerTest {
	private static final List<String> POOL = List.of("Bob", "Alma", "Odo");

	@Test
	void picksFromThePoolUsingTheRandomIndex() {
		assertEquals("Alma", NamePicker.pick(POOL, Set.of(), bound -> 1));
	}

	@Test
	void skipsNamesAlreadyTaken() {
		assertEquals("Odo", NamePicker.pick(POOL, Set.of("Bob", "Alma"), bound -> 0));
	}

	@Test
	void takenNamesAreComparedIgnoringCase() {
		assertFalse(NamePicker.pick(POOL, Set.of("bob"), bound -> 0).equalsIgnoreCase("bob"));
	}

	@Test
	void numbersAVillagerWhenEveryNameIsTaken() {
		assertEquals("Villager 2", NamePicker.pick(POOL, Set.of("Bob", "Alma", "Odo", "Villager 1"), bound -> 0));
	}
}
