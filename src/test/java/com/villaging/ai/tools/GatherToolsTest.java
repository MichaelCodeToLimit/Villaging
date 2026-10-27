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

class GatherToolsTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static Optional<String> validate(String name, String json) {
		JsonObject args = JsonParser.parseString(json).getAsJsonObject();
		return Tools.validate(new ToolCall("1", name, args), 64, 0, 0, 0);
	}

	@Test
	void blockToolsNeedNearbyWholeCoordinates() {
		for (String tool : new String[] {"chop_tree", "till", "harvest"}) {
			assertEquals(Optional.empty(), validate(tool, "{\"x\":1,\"y\":2,\"z\":3}"), tool);
			assertTrue(validate(tool, "{\"x\":1,\"y\":2}").isPresent(), tool);
			assertTrue(validate(tool, "{\"x\":1.5,\"y\":2,\"z\":3}").isPresent(), tool);
			assertTrue(validate(tool, "{\"x\":1,\"y\":2,\"z\":9999}").orElseThrow().contains("too far"), tool);
		}
	}

	@Test
	void plantNeedsASeedItemAndNearbyCoordinates() {
		assertEquals(Optional.empty(), validate("plant", "{\"x\":1,\"y\":2,\"z\":3,\"item\":\"minecraft:wheat_seeds\"}"));
		assertTrue(validate("plant", "{\"x\":1,\"y\":2,\"z\":3}").isPresent());
		assertTrue(validate("plant", "{\"x\":1,\"y\":2,\"z\":3,\"item\":\"seeds\"}").isPresent());
	}

	@Test
	void fishingTimeIsFiveToOneTwentySeconds() {
		assertEquals(Optional.empty(), validate("fish", "{\"seconds\":5}"));
		assertEquals(Optional.empty(), validate("fish", "{\"seconds\":120}"));
		assertTrue(validate("fish", "{\"seconds\":4}").isPresent());
		assertTrue(validate("fish", "{\"seconds\":121}").isPresent());
		assertTrue(validate("fish", "{}").isPresent());
	}

	@Test
	void animalToolsTakeAnOptionalWholeEntityId() {
		for (String tool : new String[] {"shear", "milk", "breed", "tame"}) {
			assertEquals(Optional.empty(), validate(tool, "{}"), tool);
			assertEquals(Optional.empty(), validate(tool, "{\"entity_id\":42}"), tool);
			assertTrue(validate(tool, "{\"entity_id\":4.5}").isPresent(), tool);
			assertTrue(validate(tool, "{\"entity_id\":\"sheep\"}").isPresent(), tool);
		}
	}
}
