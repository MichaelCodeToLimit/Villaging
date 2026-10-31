package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.villaging.config.VillagingConfig;
import org.junit.jupiter.api.Test;

class ProviderFactoryTest {
	private static VillagingConfig with(String provider, String apiKey) {
		VillagingConfig d = VillagingConfig.defaults();
		return new VillagingConfig(provider, apiKey, d.model(), d.cloudUrl(), d.ollamaUrl(), d.ollamaModel(),
				d.geminiModel(), d.geminiUrl(), d.maxSteps(), d.callsPerMinute(), d.actionRange(), d.respectProtection(), d.allowBreakBedrock(),
				d.maxScriptSteps(), d.maxScriptSeconds(), d.maxScriptDepth());
	}

	@Test
	void factoryPicksGeminiWhenConfigured() {
		assertInstanceOf(GeminiProvider.class, ProviderFactory.create(with("gemini", "key")));
	}

	@Test
	void geminiWithoutKeyIsRejectedWithTheFixHint() {
		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> ProviderFactory.create(with("gemini", "")));
		assertTrue(e.getMessage().contains("apiKey"));
	}

	@Test
	void factoryPicksCloudWhenConfigured() {
		assertInstanceOf(CloudProvider.class, ProviderFactory.create(with("cloud", "key")));
	}

	@Test
	void factoryPicksOllamaWhenConfigured() {
		assertInstanceOf(OllamaProvider.class, ProviderFactory.create(with("ollama", "")));
	}

	@Test
	void factoryRejectsUnusableConfigWithTheFixHint() {
		IllegalStateException e = assertThrows(IllegalStateException.class,
				() -> ProviderFactory.create(with("cloud", "")));
		assertTrue(e.getMessage().contains("apiKey"));
	}

	private static VillagingConfig withBackup(String provider, String apiKey, String backup) {
		VillagingConfig base = with(provider, apiKey);
		return new VillagingConfig(base.provider(), base.apiKey(), base.model(), base.cloudUrl(), base.ollamaUrl(),
				base.ollamaModel(), base.geminiModel(), base.geminiUrl(), base.maxSteps(), base.callsPerMinute(),
				base.actionRange(), base.respectProtection(), base.allowBreakBedrock(), base.maxScriptSteps(),
				base.maxScriptSeconds(), base.maxScriptDepth(), backup);
	}

	@Test
	void aConfiguredBackupWrapsTheMainAi() {
		assertInstanceOf(FallbackProvider.class, ProviderFactory.create(withBackup("gemini", "key", "ollama")));
	}

	@Test
	void aBrokenBackupIsIgnoredInsteadOfBreakingTheMainAi() {
		assertInstanceOf(GeminiProvider.class, ProviderFactory.create(withBackup("gemini", "key", "gemini")));
		assertInstanceOf(GeminiProvider.class, ProviderFactory.create(withBackup("gemini", "key", "banana")));
	}

	@Test
	void noBackupMeansNoWrapper() {
		assertInstanceOf(GeminiProvider.class, ProviderFactory.create(withBackup("gemini", "key", "")));
	}
}
