package com.villaging.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TeamJobsTest {
	@Test
	void startsAllWhenThereIsRoom() {
		assertEquals(List.of(0, 1, 2), TeamJobs.startOrder(3, 5));
	}

	@Test
	void startsOnlyTheFreeOnesAndKeepsOrder() {
		assertEquals(List.of(0, 1), TeamJobs.startOrder(4, 2));
	}

	@Test
	void zeroOrNegativeFreeStartsNone() {
		assertEquals(List.of(), TeamJobs.startOrder(3, 0));
		assertEquals(List.of(), TeamJobs.startOrder(3, -2));
	}

	@Test
	void partNamesAreValidBlueprintNamesEvenForLongTeamNames() {
		String prefix = TeamJobs.partPrefix("a".repeat(32));
		assertTrue(com.villaging.blueprint.Blueprint.isValidName(prefix + "20"), prefix);
		assertEquals("t" + "builders" + "_", TeamJobs.partPrefix("builders"));
	}

	@Test
	void taskTextIsPassedOnWithTheLeadersName() {
		assertEquals("(Your team leader Bob passes on this order from the king) fetch wood",
				TeamJobs.order("Bob", "  fetch wood "));
	}
}
