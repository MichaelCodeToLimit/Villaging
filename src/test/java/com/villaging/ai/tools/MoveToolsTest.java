package com.villaging.ai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villaging.ai.ToolCall;
import com.villaging.ai.Tools;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MoveToolsTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static Optional<String> validate(String name, String json) {
		JsonObject args = JsonParser.parseString(json).getAsJsonObject();
		return Tools.validate(new ToolCall("1", name, args), 64, 0, 0, 0);
	}

	@Test
	void sitTakesNoArguments() {
		assertEquals(Optional.empty(), validate("sit", "{}"));
	}

	@Test
	void sleepNeedsNearbyWholeBedCoordinates() {
		assertEquals(Optional.empty(), validate("sleep", "{\"x\":1,\"y\":2,\"z\":3}"));
		assertTrue(validate("sleep", "{\"x\":1,\"y\":2}").isPresent());
		assertTrue(validate("sleep", "{\"x\":1.5,\"y\":2,\"z\":3}").isPresent());
		assertTrue(validate("sleep", "{\"x\":1,\"y\":2,\"z\":9999}").orElseThrow().contains("too far"));
	}
}
