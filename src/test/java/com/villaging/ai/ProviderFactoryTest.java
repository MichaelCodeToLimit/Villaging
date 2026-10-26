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
				d.geminiModel(), d.geminiUrl(), d.maxSteps(), d.callsPerMinute(), d.actionRange());
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
}
