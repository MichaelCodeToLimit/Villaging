package com.villaging.king;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class KingDataTest {
	private final UUID alice = UUID.randomUUID();
	private final UUID bob = UUID.randomUUID();

	@Test
	void firstClaimSucceeds() {
		KingData claimed = KingData.EMPTY.claim(alice).orElseThrow();
		assertTrue(claimed.isKing(alice));
	}

	@Test
	void secondPlayerCannotClaim() {
		KingData claimed = KingData.EMPTY.claim(alice).orElseThrow();
		assertTrue(claimed.claim(bob).isEmpty());
	}

	@Test
	void kingClaimingAgainIsHarmless() {
		KingData claimed = KingData.EMPTY.claim(alice).orElseThrow();
		assertEquals(claimed, claimed.claim(alice).orElseThrow());
	}

	@Test
	void isKingOnlyForKing() {
		KingData claimed = KingData.EMPTY.claim(alice).orElseThrow();
		assertFalse(claimed.isKing(bob));
		assertFalse(KingData.EMPTY.isKing(alice));
	}

	@Test
	void abdicateClearsKing() {
		KingData claimed = KingData.EMPTY.claim(alice).orElseThrow();
		assertEquals(KingData.EMPTY, claimed.abdicate());
		assertTrue(claimed.abdicate().claim(bob).isPresent());
	}

	@Test
	void titleDefaultsToKingAndCanBeChanged() {
		KingData claimed = KingData.EMPTY.claim(alice).orElseThrow();
		assertEquals("King", claimed.title());
		KingData renamed = claimed.withTitle("Boss");
		assertEquals("Boss", renamed.title());
		assertTrue(renamed.isKing(alice));
		assertEquals("Boss", renamed.claim(alice).orElseThrow().title());
	}

	@Test
	void titleSurvivesTheCodecAndOldDataGetsTheDefault() {
		KingData data = KingData.EMPTY.claim(alice).orElseThrow().withTitle("Sire");
		JsonElement json = KingData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
		assertEquals(data, KingData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
		JsonElement old = com.google.gson.JsonParser.parseString("{\"king\":\"" + alice + "\"}");
		assertEquals("King", KingData.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow().title());
	}

	@Test
	void kingDataSurvivesCodecRoundTrip() {
		KingData claimed = KingData.EMPTY.claim(alice).orElseThrow();
		JsonElement json = KingData.CODEC.encodeStart(JsonOps.INSTANCE, claimed).getOrThrow();
		assertEquals(claimed, KingData.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
		JsonElement empty = KingData.CODEC.encodeStart(JsonOps.INSTANCE, KingData.EMPTY).getOrThrow();
		assertEquals(KingData.EMPTY, KingData.CODEC.parse(JsonOps.INSTANCE, empty).getOrThrow());
	}
}
