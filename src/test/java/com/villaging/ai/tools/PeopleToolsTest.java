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

class PeopleToolsTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static Optional<String> validate(String name, String json) {
		JsonObject args = JsonParser.parseString(json).getAsJsonObject();
		return Tools.validate(new ToolCall("1", name, args), 64, 0, 0, 0);
	}

	@Test
	void renameTakesANewNameAndAnOptionalTarget() {
		assertEquals(Optional.empty(), validate("rename", "{\"new_name\":\"Garrick\"}"));
		assertEquals(Optional.empty(), validate("rename", "{\"new_name\":\"Garrick\",\"villager_name\":\"Bob\"}"));
		assertTrue(validate("rename", "{}").isPresent());
		assertTrue(validate("rename", "{\"new_name\":\"  \"}").isPresent());
		assertTrue(validate("rename", "{\"new_name\":\"" + "x".repeat(25) + "\"}").isPresent());
		assertTrue(validate("rename", "{\"new_name\":\"“hi”\"}").isPresent());
		assertTrue(validate("rename", "{\"new_name\":\"a\\nb\"}").isPresent());
	}

	@Test
	void kingTitleTakesATitleAndAScope() {
		assertEquals(Optional.empty(), validate("set_king_title", "{\"title\":\"Boss\",\"scope\":\"everyone\"}"));
		assertEquals(Optional.empty(), validate("set_king_title", "{\"title\":\"Sire\",\"scope\":\"me\"}"));
		assertTrue(validate("set_king_title", "{\"title\":\"Boss\"}").isPresent());
		assertTrue(validate("set_king_title", "{\"title\":\"Boss\",\"scope\":\"nobody\"}").isPresent());
		assertTrue(validate("set_king_title", "{\"title\":\"\",\"scope\":\"me\"}").isPresent());
	}

	@Test
	void recruitCanBeLimitedToOneKindOfCreature() {
		for (String kind : new String[] {"any", "villager", "iron_golem", "copper_golem"}) {
			assertEquals(Optional.empty(), validate("recruit", "{\"kind\":\"" + kind + "\"}"), kind);
		}
		assertTrue(validate("recruit", "{\"kind\":\"creeper\"}").isPresent());
		assertEquals(Optional.empty(), validate("recruit", "{\"count\":2,\"kind\":\"iron_golem\"}"));
	}

	@Test
	void recruitTakesACountFromOneToFive() {
		assertEquals(Optional.empty(), validate("recruit", "{}"));
		assertEquals(Optional.empty(), validate("recruit", "{\"count\":5}"));
		assertTrue(validate("recruit", "{\"count\":6}").isPresent());
		assertTrue(validate("recruit", "{\"count\":0}").isPresent());
		assertTrue(validate("recruit", "{\"count\":1.5}").isPresent());
	}
}
