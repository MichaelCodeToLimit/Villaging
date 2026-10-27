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

class AskToolsTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static Optional<String> validate(String name, String json) {
		JsonObject args = JsonParser.parseString(json).getAsJsonObject();
		return Tools.validate(new ToolCall("1", name, args), 64, 0, 0, 0);
	}

	@Test
	void setAskPreferenceTakesACategoryAndABoolean() {
		assertEquals(Optional.empty(), validate("set_ask_preference", "{\"category\":\"fire\",\"ask\":false}"));
		assertTrue(validate("set_ask_preference", "{\"category\":\"cake\",\"ask\":false}").isPresent());
		assertTrue(validate("set_ask_preference", "{\"category\":\"fire\"}").isPresent());
		assertTrue(validate("set_ask_preference", "{\"category\":\"fire\",\"ask\":\"no\"}").isPresent());
	}

	@Test
	void registeringTwiceLeavesOneEntryPerTool() {
		AbilityTools.registerAll();
		long count = Tools.all().stream().map(ToolDef::name).filter("set_ask_preference"::equals).count();
		assertEquals(1, count);
	}
}
