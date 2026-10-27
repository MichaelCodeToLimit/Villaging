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

class CommandToolsTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static Optional<String> validate(String json) {
		JsonObject args = JsonParser.parseString(json).getAsJsonObject();
		return Tools.validate(new ToolCall("1", "relay_order", args), 64, 0, 0, 0);
	}

	@Test
	void relayOrderNeedsANameAndAnOrder() {
		assertEquals(Optional.empty(), validate("{\"villager_name\":\"Bram\",\"order\":\"chop some wood\"}"));
		assertTrue(validate("{\"order\":\"chop some wood\"}").isPresent());
		assertTrue(validate("{\"villager_name\":\"Bram\"}").isPresent());
		assertTrue(validate("{\"villager_name\":\"  \",\"order\":\"x\"}").isPresent());
		assertTrue(validate("{\"villager_name\":\"Bram\",\"order\":\"\"}").isPresent());
	}

	@Test
	void ordersAreLimitedToFiveHundredCharacters() {
		assertEquals(Optional.empty(), validate("{\"villager_name\":\"Bram\",\"order\":\"" + "o".repeat(500) + "\"}"));
		assertTrue(validate("{\"villager_name\":\"Bram\",\"order\":\"" + "o".repeat(501) + "\"}").isPresent());
	}
}
