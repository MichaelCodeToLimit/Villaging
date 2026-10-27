package com.villaging.villager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VillagerBrainTest {
	@Test
	void defaultIsNotSmart() {
		assertFalse(VillagerBrain.DEFAULT.smart());
		assertTrue(VillagerBrain.DEFAULT.memory().isEmpty());
	}

	@Test
	void makeSmartSetsFlagAndName() {
		VillagerBrain brain = VillagerBrain.DEFAULT.makeSmart("Alma");
		assertTrue(brain.smart());
		assertEquals("Alma", brain.name());
	}

	@Test
	void memoryKeepsLastTwentyLines() {
		VillagerBrain brain = VillagerBrain.DEFAULT.makeSmart("Bob");
		for (int i = 1; i <= 25; i++) {
			brain = brain.withMemory("line " + i);
		}
		assertEquals(20, brain.memory().size());
		assertEquals("line 6", brain.memory().get(0));
		assertEquals("line 25", brain.memory().get(19));
	}

	@Test
	void codecRoundTrips() {
		List<String> memory = new ArrayList<>(List.of("king: hi", "me: hello"));
		VillagerBrain original = new VillagerBrain(true, "Alma", memory);
		JsonElement json = VillagerBrain.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
		VillagerBrain decoded = VillagerBrain.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(original, decoded);
	}

	@Test
	void codecDecodesEmptyObjectAsDefault() {
		JsonElement json = com.google.gson.JsonParser.parseString("{}");
		assertEquals(VillagerBrain.DEFAULT, VillagerBrain.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
	}

	@Test
	void askOffCanBeSwitchedPerCategory() {
		VillagerBrain brain = VillagerBrain.DEFAULT.makeSmart("Alma").withAskOff("fire", true);
		assertTrue(brain.askOff().contains("fire"));
		assertFalse(brain.withAskOff("fire", false).askOff().contains("fire"));
	}

	@Test
	void askOffAndCommanderSurviveTheCodec() {
		VillagerBrain original = VillagerBrain.DEFAULT.makeSmart("Alma").withAskOff("lava", true).withCommander(true);
		JsonElement json = VillagerBrain.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
		VillagerBrain decoded = VillagerBrain.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertEquals(original, decoded);
		assertTrue(decoded.commander());
		assertTrue(decoded.askOff().contains("lava"));
	}

	@Test
	void renameAndKingTitleSurviveTheCodec() {
		VillagerBrain original = VillagerBrain.DEFAULT.makeSmart("Alma").withName("Garrick").withKingTitle("Boss");
		assertEquals("Garrick", original.name());
		assertEquals("Boss", original.kingTitle());
		JsonElement json = VillagerBrain.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
		assertEquals(original, VillagerBrain.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
	}

	@Test
	void kingTitleDefaultsToEmptyMeaningTheWorldTitle() {
		assertEquals("", VillagerBrain.DEFAULT.kingTitle());
	}

	@Test
	void oldDataWithoutNewFieldsDecodesToDefaults() {
		JsonElement json = com.google.gson.JsonParser.parseString("{\"smart\":true,\"name\":\"Bob\"}");
		VillagerBrain decoded = VillagerBrain.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
		assertTrue(decoded.askOff().isEmpty());
		assertFalse(decoded.commander());
	}
}
