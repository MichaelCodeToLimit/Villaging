package com.villaging.ai;

import com.villaging.Villaging;
import com.villaging.config.VillagingConfig;

public final class ProviderFactory {
	private ProviderFactory() {
	}

	/**
	 * The main AI, wrapped with the backup AI when {@code fallbackProvider} is set and works. A backup that cannot work
	 * is ignored with a warning, so a typo there never switches off the main AI.
	 *
	 * @throws IllegalStateException with the config's fix hint when the main config is not usable.
	 */
	public static AiProvider create(VillagingConfig config) {
		AiProvider main = createSingle(config);
		if (config.fallbackProvider().isEmpty()) {
			return main;
		}
		String problem = config.fallbackProblem();
		if (!problem.isEmpty()) {
			Villaging.LOGGER.warn("The backup AI is not used: {}", problem);
			return main;
		}
		return new FallbackProvider(main, createSingle(config.backupConfig()), System::nanoTime);
	}

	private static AiProvider createSingle(VillagingConfig config) {
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
