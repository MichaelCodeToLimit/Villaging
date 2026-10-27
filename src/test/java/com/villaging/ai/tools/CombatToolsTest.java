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

class CombatToolsTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static Optional<String> validate(String name, String json) {
		JsonObject args = JsonParser.parseString(json).getAsJsonObject();
		return Tools.validate(new ToolCall("1", name, args), 64, 0, 0, 0);
	}

	@Test
	void attackAndShootNeedAWholeEntityId() {
		for (String tool : new String[] {"attack", "shoot"}) {
			assertEquals(Optional.empty(), validate(tool, "{\"entity_id\":42}"), tool);
			assertTrue(validate(tool, "{}").isPresent(), tool);
			assertTrue(validate(tool, "{\"entity_id\":4.5}").isPresent(), tool);
			assertTrue(validate(tool, "{\"entity_id\":-1}").isPresent(), tool);
			assertTrue(validate(tool, "{\"entity_id\":\"zombie\"}").isPresent(), tool);
		}
	}

	@Test
	void guardTakesNoArguments() {
		assertEquals(Optional.empty(), validate("guard", "{}"));
	}

	@Test
	void fleeTimeIsOneToSixtySeconds() {
		assertEquals(Optional.empty(), validate("flee", "{\"seconds\":1}"));
		assertEquals(Optional.empty(), validate("flee", "{\"seconds\":60}"));
		assertTrue(validate("flee", "{\"seconds\":0}").isPresent());
		assertTrue(validate("flee", "{\"seconds\":61}").isPresent());
		assertTrue(validate("flee", "{}").isPresent());
	}
}
