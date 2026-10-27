package com.villaging.ai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.villaging.ai.ToolCall;
import com.villaging.ai.Tools;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ScriptToolsTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static Optional<String> validate(String name, JsonObject args) {
		return Tools.validate(new ToolCall("1", name, args), 64, 0, 0, 0);
	}

	private static JsonObject withScript(String script) {
		JsonObject args = new JsonObject();
		args.addProperty("script", script);
		return args;
	}

	@Test
	void runScriptTakesTheScriptAsJsonText() {
		assertEquals(Optional.empty(), validate("run_script", withScript("{\"steps\":[{\"tool\":\"stop\"}]}")));
		assertTrue(validate("run_script", withScript("not json")).orElseThrow().contains("JSON"));
		assertTrue(validate("run_script", withScript("{\"steps\":[{\"tool\":\"fly\"}]}")).orElseThrow().contains("fly"));
		assertTrue(validate("run_script", new JsonObject()).isPresent());
		assertTrue(validate("run_script", withScript("x".repeat(20001))).isPresent());
	}

	@Test
	void stopScriptTakesNoArguments() {
		assertEquals(Optional.empty(), validate("stop_script", new JsonObject()));
	}
}
