package com.villaging.build;

import com.villaging.Villaging;
import com.villaging.command.VillagingRuntime;
import com.villaging.config.BuildSettings;
import java.nio.file.Path;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Where a world keeps its blueprints, and the current build limits. */
public final class BlueprintFiles {
	private BlueprintFiles() {
	}

	/** {@code <world>/villaging/blueprints}. */
	public static Path dir(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve("villaging").resolve("blueprints");
	}

	public static BuildSettings settings() {
		VillagingRuntime runtime = Villaging.runtime();
		return runtime == null ? BuildSettings.defaults() : runtime.config().build();
	}
}
