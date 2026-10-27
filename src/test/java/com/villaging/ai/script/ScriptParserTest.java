package com.villaging.ai.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villaging.ai.tools.AbilityTools;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ScriptParserTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	private static String error(String text, int maxDepth) {
		return assertThrows(ScriptException.class, () -> ScriptParser.parse(json(text), maxDepth)).getMessage();
	}

	@Test
	void aPlainListOfToolCallsParses() {
		Script script = ScriptParser.parse(json("{\"steps\":[{\"tool\":\"go_to\",\"args\":{\"x\":1,\"y\":2,\"z\":3}},{\"tool\":\"stop\"}]}"), 3);
		assertEquals(2, script.steps().size());
		assertTrue(script.steps().get(0) instanceof Step.Call);
		assertEquals("go_to", ((Step.Call) script.steps().get(0)).tool());
	}

	@Test
	void repeatUntilAndIfParseWithTheirBodies() {
		Script script = ScriptParser.parse(json("{\"steps\":["
				+ "{\"repeat\":3,\"steps\":[{\"tool\":\"stop\"}]},"
				+ "{\"until\":{\"have\":{\"item\":\"minecraft:oak_log\",\"count\":10}},\"steps\":[{\"tool\":\"stop\"}]},"
				+ "{\"if\":{\"night\":true},\"then\":[{\"tool\":\"stop\"}],\"else\":[{\"tool\":\"stop\"}]}]}"), 3);
		assertTrue(script.steps().get(0) instanceof Step.Repeat);
		assertTrue(script.steps().get(1) instanceof Step.Until);
		assertTrue(script.steps().get(2) instanceof Step.If);
	}

	@Test
	void anUnknownToolIsRejectedWithItsName() {
		assertTrue(error("{\"steps\":[{\"tool\":\"fly\"}]}", 3).contains("fly"));
	}

	@Test
	void scriptsCannotStartScripts() {
		assertTrue(error("{\"steps\":[{\"tool\":\"run_script\",\"args\":{}}]}", 3).contains("run_script"));
		assertTrue(error("{\"steps\":[{\"tool\":\"stop_script\"}]}", 3).contains("stop_script"));
	}

	@Test
	void anUnknownKeyIsRejected() {
		assertTrue(error("{\"steps\":[{\"tool\":\"stop\",\"eval\":\"1+1\"}]}", 3).contains("eval"));
		assertTrue(error("{\"steps\":[],\"extra\":1}", 3).contains("extra"));
	}

	@Test
	void repeatMustBeBetweenOneAndOneHundred() {
		assertThrows(ScriptException.class, () -> ScriptParser.parse(json("{\"steps\":[{\"repeat\":0,\"steps\":[{\"tool\":\"stop\"}]}]}"), 3));
		assertThrows(ScriptException.class, () -> ScriptParser.parse(json("{\"steps\":[{\"repeat\":101,\"steps\":[{\"tool\":\"stop\"}]}]}"), 3));
		ScriptParser.parse(json("{\"steps\":[{\"repeat\":100,\"steps\":[{\"tool\":\"stop\"}]}]}"), 3);
	}

	@Test
	void nestingDeeperThanTheLimitIsRejected() {
		String deep = "{\"steps\":[{\"repeat\":2,\"steps\":[{\"repeat\":2,\"steps\":[{\"repeat\":2,\"steps\":[{\"tool\":\"stop\"}]}]}]}]}";
		ScriptParser.parse(json(deep), 3);
		assertTrue(error(deep, 2).contains("deep"));
	}

	@Test
	void moreThanTwoHundredStepsInTheSourceIsRejected() {
		StringBuilder steps = new StringBuilder();
		for (int i = 0; i < 201; i++) {
			steps.append(i == 0 ? "" : ",").append("{\"tool\":\"stop\"}");
		}
		assertTrue(error("{\"steps\":[" + steps + "]}", 3).contains("200"));
	}

	@Test
	void conditionsAreCheckedToo() {
		assertTrue(error("{\"steps\":[{\"if\":{\"explode\":true},\"then\":[]}]}", 3).contains("explode"));
		assertTrue(error("{\"steps\":[{\"if\":{\"have\":{\"item\":\"bad\"}},\"then\":[]}]}", 3).contains("item"));
		assertTrue(error("{\"steps\":[{\"if\":{\"night\":true,\"have\":{\"item\":\"minecraft:stick\"}},\"then\":[]}]}", 3).contains("one"));
	}

	@Test
	void aScriptNeedsAStepsList() {
		assertTrue(error("{}", 3).contains("steps"));
		assertTrue(error("{\"steps\":5}", 3).contains("steps"));
	}
}
