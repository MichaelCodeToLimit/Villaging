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

class WorldToolsTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static Optional<String> validate(String name, String json) {
		JsonObject args = JsonParser.parseString(json).getAsJsonObject();
		return Tools.validate(new ToolCall("1", name, args), 64, 0, 0, 0);
	}

	@Test
	void placeBlockNeedsANamespacedItemAndNearbyWholeCoordinates() {
		assertEquals(Optional.empty(), validate("place_block", "{\"item\":\"minecraft:oak_planks\",\"x\":1,\"y\":2,\"z\":3}"));
		assertTrue(validate("place_block", "{\"item\":\"oak_planks\",\"x\":1,\"y\":2,\"z\":3}").isPresent());
		assertTrue(validate("place_block", "{\"item\":\"Minecraft:Stone\",\"x\":1,\"y\":2,\"z\":3}").isPresent());
		assertTrue(validate("place_block", "{\"item\":\"minecraft:stone\",\"x\":1.5,\"y\":2,\"z\":3}").isPresent());
		assertTrue(validate("place_block", "{\"item\":\"minecraft:stone\",\"x\":1,\"y\":2}").isPresent());
		assertTrue(validate("place_block", "{\"x\":1,\"y\":2,\"z\":3}").isPresent());
		assertTrue(validate("place_block", "{\"item\":\"minecraft:stone\",\"x\":900,\"y\":2,\"z\":3}").orElseThrow().contains("too far"));
	}

	@Test
	void useBlockNeedsNearbyWholeCoordinates() {
		assertEquals(Optional.empty(), validate("use_block", "{\"x\":1,\"y\":2,\"z\":3}"));
		assertTrue(validate("use_block", "{\"x\":1,\"y\":2}").isPresent());
		assertTrue(validate("use_block", "{\"x\":1,\"y\":2,\"z\":3.2}").isPresent());
	}

	@Test
	void breakAreaBoxesAreCappedAtSixteenPerSide() {
		assertEquals(Optional.empty(),
				validate("break_area", "{\"x1\":0,\"y1\":0,\"z1\":0,\"x2\":15,\"y2\":15,\"z2\":15}"));
		assertTrue(validate("break_area", "{\"x1\":0,\"y1\":0,\"z1\":0,\"x2\":16,\"y2\":0,\"z2\":0}").orElseThrow().contains("16"));
		assertTrue(validate("break_area", "{\"x1\":0,\"y1\":20,\"z1\":0,\"x2\":0,\"y2\":3,\"z2\":0}").orElseThrow().contains("16"));
		assertTrue(validate("break_area", "{\"x1\":0,\"y1\":0,\"z1\":0,\"x2\":1,\"y2\":1}").isPresent());
		assertTrue(validate("break_area", "{\"x1\":0,\"y1\":0,\"z1\":0,\"x2\":1.5,\"y2\":1,\"z2\":1}").isPresent());
	}

	@Test
	void breakAreaCornersMustBeWithinRange() {
		assertTrue(validate("break_area", "{\"x1\":60,\"y1\":0,\"z1\":0,\"x2\":70,\"y2\":0,\"z2\":0}").orElseThrow().contains("too far"));
		assertTrue(validate("break_area", "{\"x1\":300,\"y1\":0,\"z1\":0,\"x2\":301,\"y2\":1,\"z2\":1}").orElseThrow().contains("too far"));
	}
}
