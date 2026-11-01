package com.villaging.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TeamCommandsTest {
	@Test
	void parsesLeaderAndMembers() {
		TeamCommands.Parsed p = TeamCommands.parse("Bob, Alice, Rex").orElseThrow();
		assertEquals("Bob", p.leader());
		assertEquals(List.of("Alice", "Rex"), p.members());
	}

	@Test
	void acceptsAndSeparator() {
		TeamCommands.Parsed p = TeamCommands.parse("Bob and Alice and Rex").orElseThrow();
		assertEquals("Bob", p.leader());
		assertEquals(List.of("Alice", "Rex"), p.members());
	}

	@Test
	void trimsAndDropsEmpty() {
		TeamCommands.Parsed p = TeamCommands.parse("  Bob ,, Alice ,  ").orElseThrow();
		assertEquals("Bob", p.leader());
		assertEquals(List.of("Alice"), p.members());
	}

	@Test
	void aLeaderAloneIsFine() {
		assertTrue(TeamCommands.parse("Bob").orElseThrow().members().isEmpty());
	}

	@Test
	void emptyIsRejected() {
		assertEquals(Optional.empty(), TeamCommands.parse("  , , "));
		assertEquals(Optional.empty(), TeamCommands.parse(""));
		assertEquals(Optional.empty(), TeamCommands.parse(null));
	}
}
