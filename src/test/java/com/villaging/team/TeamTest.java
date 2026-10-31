package com.villaging.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamTest {
	private final UUID bob = UUID.randomUUID();
	private final UUID alice = UUID.randomUUID();
	private final UUID rex = UUID.randomUUID();
	private final UUID max = UUID.randomUUID();

	@Test
	void createPutsLeaderFirstAndDropsDuplicates() throws TeamException {
		Team t = Team.create("builders", bob, List.of(alice, bob, alice, rex), 5);
		assertEquals(List.of(bob, alice, rex), t.members());
		assertEquals(bob, t.leader());
	}

	@Test
	void rejectsBadNames() {
		for (String bad : new String[] {"Bad Name", "", "a".repeat(33), "UPPER"}) {
			assertThrows(TeamException.class, () -> Team.create(bad, bob, List.of(alice), 5), bad);
		}
		assertTrue(Team.isValidName("stone_crew_2"));
	}

	@Test
	void rejectsMoreThanMaxSize() {
		TeamException e = assertThrows(TeamException.class, () -> Team.create("crew", bob, List.of(alice, rex, max), 3));
		assertTrue(e.getMessage().contains("3"), e.getMessage());
	}

	@Test
	void withRefusesDuplicateAndOverflow() throws TeamException {
		Team t = Team.create("crew", bob, List.of(alice), 3);
		assertThrows(TeamException.class, () -> t.with(alice, 3));
		Team full = t.with(rex, 3);
		assertEquals(3, full.members().size());
		assertThrows(TeamException.class, () -> full.with(max, 3));
	}

	@Test
	void withoutRefusesLeader() throws TeamException {
		Team t = Team.create("crew", bob, List.of(alice), 5);
		assertThrows(TeamException.class, () -> t.without(bob));
		assertEquals(List.of(bob), t.without(alice).members());
		assertThrows(TeamException.class, () -> t.without(rex), "not a member");
	}

	@Test
	void canWorkNeedsTwo() throws TeamException {
		assertFalse(Team.create("solo", bob, List.of(), 5).canWork());
		assertTrue(Team.create("duo", bob, List.of(alice), 5).canWork());
	}

	@Test
	void leaderIsAlwaysMember() throws TeamException {
		assertTrue(Team.create("crew", bob, List.of(alice), 5).has(bob));
	}
}
