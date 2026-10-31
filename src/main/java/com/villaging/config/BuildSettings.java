package com.villaging.config;

/** The building options in the {@code "build"} part of config/villaging.json. */
public record BuildSettings(int maxBlueprintBlocks, int maxSavedBlueprints, int buildTicksPerBlock, int buildAskThreshold,
		int maxActiveBuilds) {
	public static BuildSettings defaults() {
		return new BuildSettings(20_000, 50, 6, 64, 3);
	}

	/** Non-positive or missing values fall back to the defaults. */
	static BuildSettings from(Raw raw) {
		BuildSettings d = defaults();
		if (raw == null) {
			return d;
		}
		return new BuildSettings(positive(raw.maxBlueprintBlocks, d.maxBlueprintBlocks), positive(raw.maxSavedBlueprints, d.maxSavedBlueprints),
				positive(raw.buildTicksPerBlock, d.buildTicksPerBlock), positive(raw.buildAskThreshold, d.buildAskThreshold),
				positive(raw.maxActiveBuilds, d.maxActiveBuilds));
	}

	private static int positive(Integer value, int fallback) {
		return value != null && value > 0 ? value : fallback;
	}

	/** The file's raw values, any of which may be missing. */
	static final class Raw {
		Integer maxBlueprintBlocks;
		Integer maxSavedBlueprints;
		Integer buildTicksPerBlock;
		Integer buildAskThreshold;
		Integer maxActiveBuilds;
	}
}
