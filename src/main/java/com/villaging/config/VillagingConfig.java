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
		String geminiModel,
		String geminiUrl,
		int maxSteps,
		int callsPerMinute,
		int actionRange,
		boolean respectProtection,
		boolean allowBreakBedrock,
		int maxScriptSteps,
		int maxScriptSeconds,
		int maxScriptDepth,
		String fallbackProvider) {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** Without a backup AI: the older setting list, so existing code that builds a config keeps working. */
	public VillagingConfig(String provider, String apiKey, String model, String cloudUrl, String ollamaUrl,
			String ollamaModel, String geminiModel, String geminiUrl, int maxSteps, int callsPerMinute, int actionRange,
			boolean respectProtection, boolean allowBreakBedrock, int maxScriptSteps, int maxScriptSeconds,
			int maxScriptDepth) {
		this(provider, apiKey, model, cloudUrl, ollamaUrl, ollamaModel, geminiModel, geminiUrl, maxSteps, callsPerMinute,
				actionRange, respectProtection, allowBreakBedrock, maxScriptSteps, maxScriptSeconds, maxScriptDepth, "");
	}

	public static VillagingConfig defaults() {
		return new VillagingConfig("cloud", "", "claude-sonnet-5-5", "https://api.anthropic.com",
				"http://localhost:11434", "llama3.1:8b", "gemini-3.5-flash", "https://generativelanguage.googleapis.com",
				20, 10, 64, true, false, 500, 300, 3, "");
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
					orDefault(raw.geminiModel, defaults.geminiModel),
					orDefault(raw.geminiUrl, defaults.geminiUrl),
					raw.maxSteps != null && raw.maxSteps > 0 ? raw.maxSteps : defaults.maxSteps,
					raw.callsPerMinute != null && raw.callsPerMinute > 0 ? raw.callsPerMinute : defaults.callsPerMinute,
					raw.actionRange != null && raw.actionRange > 0 ? raw.actionRange : defaults.actionRange,
					raw.respectProtection != null ? raw.respectProtection : defaults.respectProtection,
					raw.allowBreakBedrock != null ? raw.allowBreakBedrock : defaults.allowBreakBedrock,
					raw.maxScriptSteps != null && raw.maxScriptSteps > 0 ? raw.maxScriptSteps : defaults.maxScriptSteps,
					raw.maxScriptSeconds != null && raw.maxScriptSeconds > 0 ? raw.maxScriptSeconds : defaults.maxScriptSeconds,
					raw.maxScriptDepth != null && raw.maxScriptDepth > 0 ? raw.maxScriptDepth : defaults.maxScriptDepth,
					raw.fallbackProvider == null ? "" : raw.fallbackProvider.strip());
		} catch (IOException | RuntimeException e) {
			Villaging.LOGGER.error("Could not read {}; using defaults", file, e);
			return defaults;
		}
	}

	/** A copy using the given provider and key (the in-game key box). */
	public VillagingConfig withKey(String newProvider, String newKey) {
		return new VillagingConfig(newProvider, newKey, model, cloudUrl, ollamaUrl, ollamaModel, geminiModel, geminiUrl,
				maxSteps, callsPerMinute, actionRange, respectProtection, allowBreakBedrock, maxScriptSteps,
				maxScriptSeconds, maxScriptDepth, fallbackProvider);
	}

	/** The same settings with the backup AI as the main one and no backup of its own. */
	public VillagingConfig backupConfig() {
		return new VillagingConfig(fallbackProvider, apiKey, model, cloudUrl, ollamaUrl, ollamaModel, geminiModel, geminiUrl,
				maxSteps, callsPerMinute, actionRange, respectProtection, allowBreakBedrock, maxScriptSteps,
				maxScriptSeconds, maxScriptDepth, "");
	}

	/**
	 * Fix hint when {@code fallbackProvider} is set but cannot work, empty when there is no backup AI or it is fine.
	 * A broken backup never stops the main AI; it is simply not used.
	 */
	public String fallbackProblem() {
		if (fallbackProvider.isEmpty()) {
			return "";
		}
		if (fallbackProvider.equals(provider)) {
			return "\"fallbackProvider\" must be different from \"provider\" in config/villaging.json.";
		}
		if (!fallbackProvider.equals("cloud") && !fallbackProvider.equals("gemini") && !fallbackProvider.equals("ollama")) {
			return "Unknown \"fallbackProvider\" \"" + fallbackProvider + "\" in config/villaging.json; use \"cloud\", \"gemini\" or \"ollama\".";
		}
		return backupConfig().problem();
	}

	public void save(Path file) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.writeString(file, GSON.toJson(this));
	}

	public boolean isUsable() {
		return problem().isEmpty();
	}

	/** Human-readable fix hint, empty when the config is usable. */
	public String problem() {
		return switch (provider) {
			case "cloud" -> apiKey.isBlank() ? "Enter your apiKey with /villaging key (or in config/villaging.json) to use the cloud AI." : "";
			case "gemini" -> apiKey.isBlank() ? "Enter your apiKey with /villaging key (or in config/villaging.json) to use Gemini." : "";
			case "ollama" -> ollamaUrl.isBlank() ? "Set \"ollamaUrl\" in config/villaging.json." : "";
			default -> "Unknown \"provider\" \"" + provider + "\" in config/villaging.json; use \"cloud\", \"gemini\" or \"ollama\".";
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
		String geminiModel;
		String geminiUrl;
		Integer maxSteps;
		Integer callsPerMinute;
		Integer actionRange;
		Boolean respectProtection;
		Boolean allowBreakBedrock;
		Integer maxScriptSteps;
		Integer maxScriptSeconds;
		Integer maxScriptDepth;
		String fallbackProvider;
	}
}
