package com.villaging.build;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Which helpers are building right now, so a world never runs more builds at once than allowed. Main thread. */
public final class BuildRegistry {
	private static final Set<UUID> ACTIVE = new HashSet<>();

	private BuildRegistry() {
	}

	/** Registers a build; false (and nothing changes) when {@code max} builds are already running and this helper is not one of them. */
	public static boolean tryStart(UUID helper, int max) {
		if (!ACTIVE.contains(helper) && ACTIVE.size() >= max) {
			return false;
		}
		ACTIVE.add(helper);
		return true;
	}

	public static void finish(UUID helper) {
		ACTIVE.remove(helper);
	}

	public static boolean isBuilding(UUID helper) {
		return ACTIVE.contains(helper);
	}

	public static int count() {
		return ACTIVE.size();
	}

	public static void clear() {
		ACTIVE.clear();
	}
}
