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

	@Test
	void askNeedsAQuestionAndAtMostFourShortOptions() {
		assertEquals(Optional.empty(), validate("ask", "{\"question\":\"Which chest?\",\"options\":[\"Left\",\"Right\"]}"));
		assertEquals(Optional.empty(), validate("ask", "{\"question\":\"Sure?\"}"));
		assertTrue(validate("ask", "{\"question\":\"  \"}").isPresent());
		assertTrue(validate("ask", "{}").isPresent());
		assertTrue(validate("ask", "{\"question\":\"Q\",\"options\":[\"1\",\"2\",\"3\",\"4\",\"5\"]}").isPresent());
		assertTrue(validate("ask", "{\"question\":\"Q\",\"options\":[\"" + "x".repeat(61) + "\"]}").isPresent());
		assertTrue(validate("ask", "{\"question\":\"Q\",\"options\":[\"\"]}").isPresent());
		assertTrue(validate("ask", "{\"question\":\"Q\",\"options\":\"Left\"}").isPresent());
		assertTrue(validate("ask", "{\"question\":\"" + "q".repeat(301) + "\"}").isPresent());
	}
}
