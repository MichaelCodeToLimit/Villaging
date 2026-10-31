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

	@Test
	void abilitySettingsHaveSafeDefaults() {
		VillagingConfig d = VillagingConfig.defaults();
		assertTrue(d.respectProtection());
		assertFalse(d.allowBreakBedrock());
		assertEquals(500, d.maxScriptSteps());
		assertEquals(300, d.maxScriptSeconds());
		assertEquals(3, d.maxScriptDepth());
	}

	@Test
	void abilitySettingsAreReadFromFile() throws Exception {
		Path file = dir.resolve("villaging.json");
		Files.writeString(file, "{\"respectProtection\":false,\"allowBreakBedrock\":true,\"maxScriptSteps\":40}");
		VillagingConfig config = VillagingConfig.load(file);
		assertFalse(config.respectProtection());
		assertTrue(config.allowBreakBedrock());
		assertEquals(40, config.maxScriptSteps());
		assertEquals(300, config.maxScriptSeconds());
	}

	@Test
	void withKeySetsProviderAndKeyAndSurvivesSaveAndLoad() throws Exception {
		Path file = dir.resolve("villaging.json");
		VillagingConfig saved = VillagingConfig.defaults().withKey("gemini", "secret-key");
		assertEquals("gemini", saved.provider());
		assertEquals("secret-key", saved.apiKey());
		assertTrue(saved.isUsable());
		saved.save(file);
		assertEquals(saved, VillagingConfig.load(file));
	}

	@Test
	void unusableConfigMentionsTheInGameKeyBox() {
		assertTrue(VillagingConfig.defaults().problem().contains("/villaging key"));
	}

	@Test
	void nonPositiveScriptLimitsFallBackToDefaults() throws Exception {
		Path file = dir.resolve("villaging.json");
		Files.writeString(file, "{\"maxScriptSteps\":0,\"maxScriptSeconds\":-5,\"maxScriptDepth\":0}");
		VillagingConfig config = VillagingConfig.load(file);
		assertEquals(500, config.maxScriptSteps());
		assertEquals(300, config.maxScriptSeconds());
		assertEquals(3, config.maxScriptDepth());
	}

	private static VillagingConfig withFallback(String provider, String apiKey, String fallback) throws Exception {
		Path file = java.nio.file.Files.createTempFile("villaging", ".json");
		Files.writeString(file, "{\"provider\":\"" + provider + "\",\"apiKey\":\"" + apiKey + "\",\"fallbackProvider\":\"" + fallback + "\"}");
		return VillagingConfig.load(file);
	}

	@Test
	void theBackupAiIsOffByDefault() {
		assertEquals("", VillagingConfig.defaults().fallbackProvider());
		assertEquals("", VillagingConfig.defaults().fallbackProblem());
	}

	@Test
	void theBackupAiIsReadFromTheFileAndSurvivesSavingAKey() throws Exception {
		VillagingConfig config = withFallback("gemini", "k", "ollama");
		assertEquals("ollama", config.fallbackProvider());
		assertEquals("ollama", config.withKey("cloud", "other").fallbackProvider());
		assertEquals("", config.fallbackProblem());
	}

	@Test
	void aBackupThatIsTheSameAsTheMainAiIsRejected() throws Exception {
		assertTrue(withFallback("ollama", "", "ollama").fallbackProblem().contains("different"));
	}

	@Test
	void anUnknownBackupIsRejected() throws Exception {
		assertTrue(withFallback("ollama", "", "banana").fallbackProblem().contains("fallbackProvider"));
	}

	@Test
	void aKeyedBackupNeedsAKey() throws Exception {
		assertTrue(withFallback("ollama", "", "gemini").fallbackProblem().contains("apiKey"));
		assertEquals("", withFallback("ollama", "k", "gemini").fallbackProblem());
	}

	@Test
	void theBackupConfigUsesTheBackupProviderAndHasNoBackupOfItsOwn() throws Exception {
		VillagingConfig backup = withFallback("gemini", "k", "ollama").backupConfig();
		assertEquals("ollama", backup.provider());
		assertEquals("", backup.fallbackProvider());
	}
}
