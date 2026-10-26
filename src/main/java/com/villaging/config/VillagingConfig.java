package com.villaging.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.villaging.Villaging;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public record VillagingConfig(
		String provider,
		String apiKey,
		String model,
		String cloudUrl,
		String ollamaUrl,
		String ollamaModel,
		int maxSteps,
		int callsPerMinute,
		int actionRange) {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public static VillagingConfig defaults() {
		return new VillagingConfig("cloud", "", "claude-sonnet-5-5", "https://api.anthropic.com",
				"http://localhost:11434", "llama3.1:8b", 20, 10, 64);
	}

	public static VillagingConfig load(Path file) {
		VillagingConfig defaults = defaults();
		try {
			if (!Files.exists(file)) {
				Files.createDirectories(file.toAbsolutePath().getParent());
				Files.writeString(file, GSON.toJson(defaults));
				return defaults;
			}
			Raw raw = GSON.fromJson(Files.readString(file), Raw.class);
			if (raw == null) {
				return defaults;
			}
			return new VillagingConfig(
					orDefault(raw.provider, defaults.provider),
					orDefault(raw.apiKey, defaults.apiKey),
					orDefault(raw.model, defaults.model),
					orDefault(raw.cloudUrl, defaults.cloudUrl),
					orDefault(raw.ollamaUrl, defaults.ollamaUrl),
					orDefault(raw.ollamaModel, defaults.ollamaModel),
					raw.maxSteps != null && raw.maxSteps > 0 ? raw.maxSteps : defaults.maxSteps,
					raw.callsPerMinute != null && raw.callsPerMinute > 0 ? raw.callsPerMinute : defaults.callsPerMinute,
					raw.actionRange != null && raw.actionRange > 0 ? raw.actionRange : defaults.actionRange);
		} catch (IOException | RuntimeException e) {
			Villaging.LOGGER.error("Could not read {}; using defaults", file, e);
			return defaults;
		}
	}

	public boolean isUsable() {
		return problem().isEmpty();
	}

	/** Human-readable fix hint, empty when the config is usable. */
	public String problem() {
		return switch (provider) {
			case "cloud" -> apiKey.isBlank() ? "Set \"apiKey\" in config/villaging.json to use the cloud AI." : "";
			case "ollama" -> ollamaUrl.isBlank() ? "Set \"ollamaUrl\" in config/villaging.json." : "";
			default -> "Unknown \"provider\" \"" + provider + "\" in config/villaging.json; use \"cloud\" or \"ollama\".";
		};
	}

	private static String orDefault(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}

	private static final class Raw {
		String provider;
		String apiKey;
		String model;
		String cloudUrl;
		String ollamaUrl;
		String ollamaModel;
		Integer maxSteps;
		Integer callsPerMinute;
		Integer actionRange;
	}
}
