package com.villaging.command;

import com.villaging.Villaging;
import com.villaging.config.VillagingConfig;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/** Saves the API key typed into the in-game box and makes it take effect. The key is never logged or echoed. */
public final class KeySettings {
	public static final Set<String> PROVIDERS = Set.of("cloud", "gemini", "ollama");

	private KeySettings() {
	}

	/** A message for the player when the choice is not acceptable, empty when it is. Never repeats the key. */
	public static Optional<String> validate(String provider, String key) {
		if (!PROVIDERS.contains(provider)) {
			return Optional.of("Unknown AI provider.");
		}
		if (provider.equals("ollama")) {
			return Optional.empty();
		}
		if (key.isBlank()) {
			return Optional.of("Enter an API key first.");
		}
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (Character.isWhitespace(c) || Character.isISOControl(c)) {
				return Optional.of("That key has spaces or line breaks in it; paste only the key itself.");
			}
		}
		return Optional.empty();
	}

	/** Only the world's owner (single player) or an admin may change the key, since it is stored on the server. */
	public static boolean mayChange(MinecraftServer server, ServerPlayer player) {
		return server.isSingleplayerOwner(player.nameAndId()) || player.permissions().hasPermission(Permissions.COMMANDS_ADMIN);
	}

	/** Returns the message to show the player. */
	public static String apply(MinecraftServer server, ServerPlayer player, String provider, String key) {
		if (!mayChange(server, player)) {
			return "Only the world owner or an admin can change the AI key.";
		}
		String trimmed = key == null ? "" : key.trim();
		Optional<String> problem = validate(provider, trimmed);
		if (problem.isPresent()) {
			return problem.get();
		}
		VillagingRuntime current = Villaging.runtime();
		VillagingConfig base = current != null ? current.config() : VillagingConfig.load(Villaging.configFile());
		VillagingConfig updated = base.withKey(provider, provider.equals("ollama") ? base.apiKey() : trimmed);
		try {
			updated.save(Villaging.configFile());
		} catch (IOException e) {
			Villaging.LOGGER.error("Could not save the AI settings", e);
			return "Could not save the settings file.";
		}
		Villaging.replaceRuntime(new VillagingRuntime(server, updated));
		return updated.isUsable() ? "Saved. Your smart villagers are ready." : "Saved, but: " + updated.problem();
	}
}
