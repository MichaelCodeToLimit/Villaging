package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolSchemaTest {
	private static JsonObject args(String json) {
		return com.google.gson.JsonParser.parseString(json).getAsJsonObject();
	}

	@Test
	void intInRejectsMissingNonNumberFractionAndOutOfRange() {
		assertTrue(ToolSchema.intIn(args("{}"), "count", 1, 10).orElseThrow().contains("count"));
		assertTrue(ToolSchema.intIn(args("{\"count\":\"3\"}"), "count", 1, 10).orElseThrow().contains("count"));
		assertTrue(ToolSchema.intIn(args("{\"count\":2.5}"), "count", 1, 10).orElseThrow().contains("whole"));
		assertTrue(ToolSchema.intIn(args("{\"count\":0}"), "count", 1, 10).orElseThrow().contains("1"));
		assertTrue(ToolSchema.intIn(args("{\"count\":11}"), "count", 1, 10).orElseThrow().contains("10"));
		assertEquals(Optional.empty(), ToolSchema.intIn(args("{\"count\":10}"), "count", 1, 10));
	}

	@Test
	void stringNonBlankChecksPresenceAndLength() {
		assertTrue(ToolSchema.stringNonBlank(args("{}"), "item", 20).isPresent());
		assertTrue(ToolSchema.stringNonBlank(args("{\"item\":\"  \"}"), "item", 20).isPresent());
		assertTrue(ToolSchema.stringNonBlank(args("{\"item\":\"" + "a".repeat(21) + "\"}"), "item", 20).isPresent());
		assertEquals(Optional.empty(), ToolSchema.stringNonBlank(args("{\"item\":\"minecraft:stick\"}"), "item", 20));
	}

	@Test
	void oneOfListsAllowedValues() {
		String error = ToolSchema.oneOf(args("{\"category\":\"cake\"}"), "category", Set.of("fire")).orElseThrow();
		assertTrue(error.contains("fire"));
		assertEquals(Optional.empty(), ToolSchema.oneOf(args("{\"category\":\"fire\"}"), "category", Set.of("fire")));
	}

	@Test
	void coordinatesRejectNaNAndFarPositions() {
		assertTrue(ToolSchema.coordinates(args("{\"x\":1,\"y\":2}"), false, 10, 0, 0, 0).isPresent());
		assertTrue(ToolSchema.coordinates(args("{\"x\":1,\"y\":2,\"z\":50}"), false, 10, 0, 0, 0).orElseThrow()
				.contains("too far"));
		assertTrue(ToolSchema.coordinates(args("{\"x\":1.5,\"y\":2,\"z\":3}"), true, 10, 0, 0, 0).orElseThrow()
				.contains("whole"));
		assertEquals(Optional.empty(), ToolSchema.coordinates(args("{\"x\":1,\"y\":2,\"z\":3}"), true, 10, 0, 0, 0));
	}

	@Test
	void enumPropAndArrayPropAppearInTheSchema() {
		JsonObject schema = ToolSchema.object(
				ToolSchema.enumProp("category", "Which.", "fire", "lava"),
				ToolSchema.arrayOfStrings("options", "Choices.")).required("category").build();
		assertEquals(2, schema.getAsJsonObject("properties").getAsJsonObject("category").getAsJsonArray("enum").size());
		assertEquals("array", schema.getAsJsonObject("properties").getAsJsonObject("options").get("type").getAsString());
		assertEquals("category", schema.getAsJsonArray("required").get(0).getAsString());
	}
}
