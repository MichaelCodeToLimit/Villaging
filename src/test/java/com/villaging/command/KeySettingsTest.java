package com.villaging.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class KeySettingsTest {
	@Test
	void acceptsAKeyForCloudAndGemini() {
		assertEquals(Optional.empty(), KeySettings.validate("cloud", "sk-abc123"));
		assertEquals(Optional.empty(), KeySettings.validate("gemini", "AIzaSyExample_-key"));
	}

	@Test
	void ollamaNeedsNoKey() {
		assertEquals(Optional.empty(), KeySettings.validate("ollama", ""));
	}

	@Test
	void rejectsABlankKeyForCloudProviders() {
		assertTrue(KeySettings.validate("cloud", "   ").isPresent());
		assertTrue(KeySettings.validate("gemini", "").isPresent());
	}

	@Test
	void rejectsUnknownProvidersAndKeysThatCouldBreakAHeader() {
		assertTrue(KeySettings.validate("banana", "k").isPresent());
		assertTrue(KeySettings.validate("cloud", "abc\r\nX-Evil: 1").isPresent());
		assertTrue(KeySettings.validate("cloud", "has space").isPresent());
	}

	@Test
	void messagesNeverContainTheKey() {
		assertTrue(!KeySettings.validate("cloud", "secret value\n").orElseThrow().contains("secret"));
	}
}
