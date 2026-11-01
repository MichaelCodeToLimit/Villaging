package com.villaging.team;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TeamDataTest {
	@Test
	void codecRoundTrips() throws TeamException {
		Team team = Team.create("builders", UUID.randomUUID(), List.of(UUID.randomUUID(), UUID.randomUUID()), 5);
		var json = TeamData.CODEC.encodeStart(JsonOps.INSTANCE, team).result().orElseThrow();
		assertEquals(team, TeamData.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow());
	}
}
