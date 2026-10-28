package com.villaging.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villaging.Villaging;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** Remembers, once per computer, that the player has seen the first-time guide. */
final class GuideSeen {
	private static volatile Boolean cache;

	private GuideSeen() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("villaging-client.json");
	}

	/** Read from disk once, then remembered (this is asked every game tick while no screen is open). */
	static boolean seen() {
		Boolean known = cache;
		if (known != null) {
			return known;
		}
		boolean result = false;
		try {
			Path file = file();
			if (Files.exists(file)) {
				JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				result = json.has("guideSeen") && json.get("guideSeen").getAsBoolean();
			}
		} catch (Exception e) {
			result = false;
		}
		cache = result;
		return result;
	}

	static void markSeen() {
		cache = true;
		try {
			JsonObject json = new JsonObject();
			json.addProperty("guideSeen", true);
			Files.writeString(file(), json.toString());
		} catch (Exception e) {
			Villaging.LOGGER.warn("Could not remember that the guide was shown", e);
		}
	}
}
