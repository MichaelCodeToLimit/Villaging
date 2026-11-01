package com.villaging.config;

/** The team options in the {@code "teams"} part of config/villaging.json. */
public record TeamSettings(int maxSize) {
	public static final int MIN_SIZE = 2;
	public static final int MAX_SIZE = 20;
	public static final int DEFAULT_SIZE = 5;

	public static TeamSettings defaults() {
		return new TeamSettings(DEFAULT_SIZE);
	}

	/** A missing or non-positive size falls back to the default; any other is held between 2 and 20. */
	static TeamSettings from(Raw raw) {
		if (raw == null || raw.maxSize == null || raw.maxSize <= 0) {
			return defaults();
		}
		return new TeamSettings(Math.max(MIN_SIZE, Math.min(MAX_SIZE, raw.maxSize)));
	}

	/** The file's raw values, any of which may be missing. */
	static final class Raw {
		Integer maxSize;
	}
}
