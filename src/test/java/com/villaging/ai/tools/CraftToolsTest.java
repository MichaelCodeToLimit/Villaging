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

class CraftToolsTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static Optional<String> validate(String name, String json) {
		JsonObject args = JsonParser.parseString(json).getAsJsonObject();
		return Tools.validate(new ToolCall("1", name, args), 64, 0, 0, 0);
	}

	@Test
	void craftAndSmeltTakeAnItemAndACountFromOneTo64() {
		for (String tool : new String[] {"craft", "smelt"}) {
			assertEquals(Optional.empty(), validate(tool, "{\"item\":\"minecraft:stick\",\"count\":4}"), tool);
			assertEquals(Optional.empty(), validate(tool, "{\"item\":\"minecraft:stick\",\"count\":64}"), tool);
			assertTrue(validate(tool, "{\"item\":\"minecraft:stick\",\"count\":0}").isPresent(), tool);
			assertTrue(validate(tool, "{\"item\":\"minecraft:stick\",\"count\":65}").isPresent(), tool);
			assertTrue(validate(tool, "{\"item\":\"minecraft:stick\",\"count\":2.5}").isPresent(), tool);
			assertTrue(validate(tool, "{\"item\":\"stick\",\"count\":1}").isPresent(), tool);
			assertTrue(validate(tool, "{\"count\":1}").isPresent(), tool);
		}
	}

	@Test
	void brewTakesTheIngredientItem() {
		assertEquals(Optional.empty(), validate("brew", "{\"item\":\"minecraft:nether_wart\"}"));
		assertTrue(validate("brew", "{}").isPresent());
		assertTrue(validate("brew", "{\"item\":\"nether_wart\"}").isPresent());
	}

	@Test
	void enchantAndAnvilAreNotOfferedYet() {
		assertTrue(validate("enchant", "{\"item\":\"minecraft:iron_sword\",\"level\":1}").orElseThrow().contains("Unknown tool"));
		assertTrue(validate("anvil", "{\"item\":\"minecraft:iron_sword\"}").orElseThrow().contains("Unknown tool"));
	}
}
