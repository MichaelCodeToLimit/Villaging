package com.villaging.ai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villaging.ai.ToolCall;
import com.villaging.ai.ToolDef;
import com.villaging.ai.Tools;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LookToolsTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static Optional<String> validate(String name, String json) {
		JsonObject args = JsonParser.parseString(json).getAsJsonObject();
		return Tools.validate(new ToolCall("1", name, args), 64, 0, 0, 0);
	}

	@Test
	void lookAroundRadiusIsOptionalButBounded() {
		assertEquals(Optional.empty(), validate("look_around", "{}"));
		assertEquals(Optional.empty(), validate("look_around", "{\"radius\":1}"));
		assertEquals(Optional.empty(), validate("look_around", "{\"radius\":16}"));
		assertTrue(validate("look_around", "{\"radius\":0}").isPresent());
		assertTrue(validate("look_around", "{\"radius\":17}").isPresent());
		assertTrue(validate("look_around", "{\"radius\":2.5}").isPresent());
		assertTrue(validate("look_around", "{\"radius\":\"big\"}").isPresent());
	}

	@Test
	void lookAtNeedsNearbyWholeCoordinates() {
		assertEquals(Optional.empty(), validate("look_at", "{\"x\":1,\"y\":2,\"z\":3}"));
		assertTrue(validate("look_at", "{\"x\":1,\"y\":2}").isPresent());
		assertTrue(validate("look_at", "{\"x\":1.5,\"y\":2,\"z\":3}").isPresent());
		assertTrue(validate("look_at", "{\"x\":500,\"y\":2,\"z\":3}").orElseThrow().contains("too far"));
	}

	@Test
	void checkInventoryTakesNoArguments() {
		assertEquals(Optional.empty(), validate("check_inventory", "{}"));
	}

	@Test
	void registeringTwiceLeavesOneEntryPerTool() {
		AbilityTools.registerAll();
		for (String name : new String[] {"look_around", "look_at", "check_inventory"}) {
			assertEquals(1, Tools.all().stream().map(ToolDef::name).filter(name::equals).count(), name);
		}
	}
}
