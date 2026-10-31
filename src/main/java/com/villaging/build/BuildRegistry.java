package com.villaging.build;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Which helpers are building right now (so a world never runs more builds at once than allowed) and which shortage each
 * helper's AI was last woken for (so a pause can never loop). Main thread.
 */
public final class BuildRegistry {
	private static final Set<UUID> ACTIVE = new HashSet<>();
	private static final Map<UUID, String> LAST_WAKE = new HashMap<>();
	private static boolean slotFreed;

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
		if (ACTIVE.remove(helper)) {
			slotFreed = true;
		}
	}

	/** True once after a build place has been freed since the last call (so waiting team builds can try to start). */
	public static boolean consumeSlotFreed() {
		boolean was = slotFreed;
		slotFreed = false;
		return was;
	}

	public static boolean isBuilding(UUID helper) {
		return ACTIVE.contains(helper);
	}

	public static int count() {
		return ACTIVE.size();
	}

	/**
	 * True the first time this helper pauses for {@code shortage}. If the AI answers a wake-up by saying start_build again without
	 * bringing the materials, the same pause comes back and must not wake the AI a second time.
	 */
	public static boolean shouldWake(UUID helper, String shortage) {
		return !shortage.equals(LAST_WAKE.put(helper, shortage));
	}

	/** The build finished or was cancelled; the next shortage is news again. */
	public static void forgetWake(UUID helper) {
		LAST_WAKE.remove(helper);
	}

	public static void clear() {
		ACTIVE.clear();
		LAST_WAKE.clear();
		slotFreed = false;
	}
}
