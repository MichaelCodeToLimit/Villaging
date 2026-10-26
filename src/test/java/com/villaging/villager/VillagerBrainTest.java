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
}
