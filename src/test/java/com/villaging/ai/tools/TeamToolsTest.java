package com.villaging.ai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villaging.ai.ToolCall;
import com.villaging.ai.Tools;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TeamToolsTest {
	private static final List<String> KING_TOOLS = List.of("create_team", "add_to_team", "remove_from_team", "disband_team", "list_teams");

	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static Optional<String> validate(String name, String json) {
		JsonObject args = JsonParser.parseString(json).getAsJsonObject();
		return Tools.validate(new ToolCall("1", name, args), 64, 0, 0, 0);
	}

	@Test
	void kingToolsAreRegistered() {
		Set<String> names = Tools.all().stream().map(t -> t.name()).collect(Collectors.toSet());
		assertTrue(names.containsAll(KING_TOOLS));
	}

	@Test
	void createTeamNeedsNameLeaderAndMembers() {
		assertTrue(validate("create_team", "{\"name\":\"builders\",\"leader\":\"Bob\",\"members\":[\"Alice\"]}").isEmpty());
		assertTrue(validate("create_team", "{\"name\":\"builders\",\"members\":[\"Alice\"]}").isPresent(), "missing leader");
		assertTrue(validate("create_team", "{\"name\":\"Bad Name\",\"leader\":\"Bob\",\"members\":[\"Alice\"]}").isPresent());
		assertTrue(validate("create_team", "{\"name\":\"builders\",\"leader\":\"Bob\",\"members\":\"Alice\"}").isPresent(), "not an array");
		assertTrue(validate("create_team", "{\"name\":\"builders\",\"leader\":\"Bob\",\"members\":[1]}").isPresent(), "not strings");
	}

	@Test
	void createTeamRefusesAnAbsurdlyLongMemberList() {
		StringBuilder many = new StringBuilder("[");
		for (int i = 0; i < 21; i++) {
			many.append(i == 0 ? "" : ",").append("\"h").append(i).append('"');
		}
		many.append(']');
		assertTrue(validate("create_team", "{\"name\":\"big\",\"leader\":\"Bob\",\"members\":" + many + "}").isPresent());
	}

	@Test
	void leaderToolsAreRegisteredAndValidate() {
		Set<String> names = Tools.all().stream().map(t -> t.name()).collect(Collectors.toSet());
		assertTrue(names.containsAll(List.of("assign", "team_status", "assign_build")));
		assertTrue(validate("assign", "{\"member\":\"Alice\",\"task\":\"fetch 20 logs\"}").isEmpty());
		assertTrue(validate("assign", "{\"member\":\" \",\"task\":\"fetch 20 logs\"}").isPresent());
		assertTrue(validate("assign", "{\"member\":\"Alice\",\"task\":\"" + "x".repeat(501) + "\"}").isPresent());
		assertTrue(validate("assign", "{\"member\":\"Alice\"}").isPresent());
		assertTrue(validate("assign_build", "{\"blueprint\":\"stone_cottage\"}").isEmpty());
		assertTrue(validate("assign_build", "{\"blueprint\":\"Bad Name\"}").isPresent());
		assertEquals(Optional.empty(), validate("team_status", "{}"));
	}

	@Test
	void otherKingToolsValidate() {
		assertTrue(validate("add_to_team", "{\"team\":\"builders\",\"helper\":\"Rex\"}").isEmpty());
		assertTrue(validate("add_to_team", "{\"team\":\"builders\"}").isPresent());
		assertTrue(validate("remove_from_team", "{\"team\":\"builders\",\"helper\":\"Rex\"}").isEmpty());
		assertTrue(validate("disband_team", "{\"team\":\"builders\"}").isEmpty());
		assertTrue(validate("disband_team", "{\"team\":\" \"}").isPresent());
		assertEquals(Optional.empty(), validate("list_teams", "{}"));
	}
}
