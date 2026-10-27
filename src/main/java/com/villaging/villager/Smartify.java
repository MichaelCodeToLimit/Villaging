package com.villaging.villager;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;

/** Turns an ordinary villager or golem into a named smart one. Main thread. */
public final class Smartify {
	public static final double NAME_SCAN_RANGE = 64;

	private Smartify() {
	}

	/** The smart creatures within {@code range} of {@code center}, including it if it is smart. */
	public static java.util.List<PathfinderMob> smartNear(PathfinderMob center, double range) {
		if (!(center.level() instanceof ServerLevel level)) {
			return java.util.List.of();
		}
		return level.getEntitiesOfClass(PathfinderMob.class, center.getBoundingBox().inflate(range), Kinds::isSmart);
	}

	/** Names of the smart creatures around {@code center}, so a new one gets a name nobody nearby has. */
	public static Set<String> nearbyNames(PathfinderMob center) {
		Set<String> names = new HashSet<>();
		for (PathfinderMob other : smartNear(center, NAME_SCAN_RANGE)) {
			names.add(VillagerBrains.get(other).name());
		}
		return names;
	}

	/** Makes the creature smart under a fresh name and returns that name. */
	public static String makeSmart(PathfinderMob mob, Set<String> taken) {
		String name = NamePicker.pick(NamePicker.NAMES, taken, mob.getRandom()::nextInt);
		VillagerBrains.set(mob, VillagerBrains.get(mob).makeSmart(name));
		mob.setCustomName(Component.literal(name));
		mob.setCustomNameVisible(true);
		return name;
	}

	/** Gives an already-smart creature a new name (and shows it). */
	public static void rename(PathfinderMob mob, String name) {
		VillagerBrains.set(mob, VillagerBrains.get(mob).withName(name));
		mob.setCustomName(Component.literal(name));
		mob.setCustomNameVisible(true);
	}
}
