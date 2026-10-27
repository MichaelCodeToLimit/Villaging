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

class ContainerToolsTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static Optional<String> validate(String name, String json) {
		JsonObject args = JsonParser.parseString(json).getAsJsonObject();
		return Tools.validate(new ToolCall("1", name, args), 64, 0, 0, 0);
	}

	@Test
	void openContainerNeedsNearbyWholeCoordinates() {
		assertEquals(Optional.empty(), validate("open_container", "{\"x\":1,\"y\":2,\"z\":3}"));
		assertTrue(validate("open_container", "{\"x\":1,\"y\":2}").isPresent());
		assertTrue(validate("open_container", "{\"x\":1,\"y\":2,\"z\":99999}").orElseThrow().contains("too far"));
	}

	@Test
	void putInAndTakeOutNeedCoordinatesAnItemAndACount() {
		for (String tool : new String[] {"put_in", "take_out"}) {
			assertEquals(Optional.empty(), validate(tool, "{\"x\":1,\"y\":2,\"z\":3,\"item\":\"minecraft:coal\",\"count\":8}"), tool);
			assertTrue(validate(tool, "{\"x\":1,\"y\":2,\"z\":3,\"item\":\"minecraft:coal\",\"count\":0}").isPresent(), tool);
			assertTrue(validate(tool, "{\"x\":1,\"y\":2,\"z\":3,\"item\":\"minecraft:coal\",\"count\":2305}").isPresent(), tool);
			assertTrue(validate(tool, "{\"x\":1,\"y\":2,\"z\":3,\"item\":\"coal\",\"count\":1}").isPresent(), tool);
			assertTrue(validate(tool, "{\"x\":1,\"y\":2,\"item\":\"minecraft:coal\",\"count\":1}").isPresent(), tool);
			assertTrue(validate(tool, "{\"x\":1,\"y\":2,\"z\":3,\"count\":1}").isPresent(), tool);
		}
	}
}
