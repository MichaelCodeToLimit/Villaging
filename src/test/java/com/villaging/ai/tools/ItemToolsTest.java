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

class ItemToolsTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static Optional<String> validate(String name, String json) {
		JsonObject args = JsonParser.parseString(json).getAsJsonObject();
		return Tools.validate(new ToolCall("1", name, args), 64, 0, 0, 0);
	}

	@Test
	void countedToolsNeedAnItemAndACountFromOneTo2304() {
		for (String tool : new String[] {"drop", "give_to_king", "take_from_king"}) {
			assertEquals(Optional.empty(), validate(tool, "{\"item\":\"minecraft:iron_ingot\",\"count\":5}"), tool);
			assertEquals(Optional.empty(), validate(tool, "{\"item\":\"minecraft:iron_ingot\",\"count\":2304}"), tool);
			assertTrue(validate(tool, "{\"item\":\"minecraft:iron_ingot\",\"count\":0}").isPresent(), tool);
			assertTrue(validate(tool, "{\"item\":\"minecraft:iron_ingot\",\"count\":2305}").isPresent(), tool);
			assertTrue(validate(tool, "{\"item\":\"minecraft:iron_ingot\",\"count\":1.5}").isPresent(), tool);
			assertTrue(validate(tool, "{\"item\":\"minecraft:iron_ingot\"}").isPresent(), tool);
			assertTrue(validate(tool, "{\"item\":\"\",\"count\":1}").isPresent(), tool);
			assertTrue(validate(tool, "{\"count\":1}").isPresent(), tool);
		}
	}

	@Test
	void equipAndUseItemNeedAnItem() {
		assertEquals(Optional.empty(), validate("equip", "{\"item\":\"minecraft:iron_sword\"}"));
		assertTrue(validate("equip", "{}").isPresent());
		assertEquals(Optional.empty(), validate("use_item", "{\"item\":\"minecraft:bucket\"}"));
		assertTrue(validate("use_item", "{\"item\":\"bucket\"}").isPresent());
	}

	@Test
	void pickUpAndEatTakeAnOptionalItem() {
		assertEquals(Optional.empty(), validate("pick_up", "{}"));
		assertEquals(Optional.empty(), validate("pick_up", "{\"item\":\"minecraft:oak_log\"}"));
		assertTrue(validate("pick_up", "{\"item\":\"nonsense\"}").isPresent());
		assertEquals(Optional.empty(), validate("eat", "{}"));
		assertEquals(Optional.empty(), validate("eat", "{\"item\":\"minecraft:apple\"}"));
		assertTrue(validate("eat", "{\"item\":42}").isPresent());
	}
}
