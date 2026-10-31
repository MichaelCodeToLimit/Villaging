package com.villaging.build;

import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Where a world keeps its blueprints. */
public final class BlueprintFiles {
	private BlueprintFiles() {
	}

	/** {@code <world>/villaging/blueprints}. */
	public static Path dir(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("villaging").resolve("blueprints");
	}
}
