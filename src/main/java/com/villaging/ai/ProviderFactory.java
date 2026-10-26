package com.villaging.ai;

import com.villaging.config.VillagingConfig;

public final class ProviderFactory {
	private ProviderFactory() {
	}

	/** @throws IllegalStateException with the config's fix hint when the config is not usable. */
	public static AiProvider create(VillagingConfig config) {
		if (!config.isUsable()) {
			throw new IllegalStateException(config.problem());
		}
		return switch (config.provider()) {
			case "ollama" -> new OllamaProvider(config.ollamaUrl(), config.ollamaModel());
			case "gemini" -> new GeminiProvider(config.geminiUrl(), config.apiKey(), config.geminiModel());
			default -> new CloudProvider(config.cloudUrl(), config.apiKey(), config.model());
		};
	}
}
