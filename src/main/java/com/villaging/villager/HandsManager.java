package com.villaging.villager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.PathfinderMob;

/** One {@link VillagerHands} per active smart villager, created on demand and discarded with the villager. */
public final class HandsManager {
	private static final Map<UUID, VillagerHands> HANDS = new ConcurrentHashMap<>();

	private HandsManager() {
	}

	public static VillagerHands get(PathfinderMob villager) {
		return HANDS.computeIfAbsent(villager.getUUID(), id -> new VillagerHands(villager));
	}

	public static void release(PathfinderMob villager) {
		HANDS.remove(villager.getUUID());
	}

	public static void clear() {
		HANDS.clear();
	}

	public static int size() {
		return HANDS.size();
	}
}
