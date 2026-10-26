package com.villaging.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VillagingConfigTest {
	@TempDir
	Path dir;

	@Test
	void loadCreatesDefaultsWhenMissing() throws Exception {
		Path file = dir.resolve("villaging.json");
		VillagingConfig config = VillagingConfig.load(file);
		assertEquals(VillagingConfig.defaults(), config);
		assertTrue(Files.exists(file));
	}

	@Test
	void loadReadsValues() throws Exception {
		Path file = dir.resolve("villaging.json");
		Files.writeString(file, "{\"provider\":\"ollama\",\"ollamaModel\":\"qwen\",\"maxSteps\":7,\"apiKey\":\"k\"}");
		VillagingConfig config = VillagingConfig.load(file);
		assertEquals("ollama", config.provider());
		assertEquals("qwen", config.ollamaModel());
		assertEquals(7, config.maxSteps());
		assertEquals("k", config.apiKey());
		assertEquals(10, config.callsPerMinute());
	}

	@Test
	void cloudWithoutKeyIsNotUsable() {
		VillagingConfig config = VillagingConfig.defaults();
		assertFalse(config.isUsable());
		assertTrue(config.problem().contains("apiKey"));
	}

	@Test
	void ollamaWithBaseUrlIsUsable() throws Exception {
		Path file = dir.resolve("villaging.json");
		Files.writeString(file, "{\"provider\":\"ollama\"}");
		VillagingConfig config = VillagingConfig.load(file);
		assertTrue(config.isUsable());
		assertEquals("", config.problem());
	}

	@Test
	void malformedJsonFallsBackToDefaults() throws Exception {
		Path file = dir.resolve("villaging.json");
		Files.writeString(file, "{ this is not json");
		assertEquals(VillagingConfig.defaults(), VillagingConfig.load(file));
	}

	@Test
	void geminiDefaultsAndKeyRequirement() throws Exception {
		Path file = dir.resolve("villaging.json");
		Files.writeString(file, "{\"provider\":\"gemini\"}");
		VillagingConfig config = VillagingConfig.load(file);
		assertEquals("gemini-3.5-flash", config.geminiModel());
		assertEquals("https://generativelanguage.googleapis.com", config.geminiUrl());
		assertFalse(config.isUsable());
		assertTrue(config.problem().contains("apiKey"));
		Files.writeString(file, "{\"provider\":\"gemini\",\"apiKey\":\"k\"}");
		assertTrue(VillagingConfig.load(file).isUsable());
	}

	@Test
	void unknownProviderIsNotUsable() throws Exception {
		Path file = dir.resolve("villaging.json");
		Files.writeString(file, "{\"provider\":\"banana\"}");
		VillagingConfig config = VillagingConfig.load(file);
		assertFalse(config.isUsable());
		assertTrue(config.problem().contains("provider"));
	}
}
