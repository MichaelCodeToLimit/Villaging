package com.villaging.villager;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/** Turns an ordinary villager into a named smart villager. Main thread. */
public final class Smartify {
	public static final double NAME_SCAN_RANGE = 64;

	private Smartify() {
	}

	/** Names of the smart villagers around {@code center}, so a new one gets a name nobody nearby has. */
	public static Set<String> nearbyNames(Villager center) {
		Set<String> names = new HashSet<>();
		if (center.level() instanceof ServerLevel level) {
			for (Villager other : level.getEntitiesOfClass(Villager.class, center.getBoundingBox().inflate(NAME_SCAN_RANGE))) {
				VillagerBrain brain = VillagerBrains.get(other);
				if (brain.smart()) {
					names.add(brain.name());
				}
			}
		}
		return names;
	}

	/** Makes the villager smart under a fresh name and returns that name. */
	public static String makeSmart(Villager villager, Set<String> taken) {
		String name = NamePicker.pick(NamePicker.NAMES, taken, villager.getRandom()::nextInt);
		VillagerBrains.set(villager, VillagerBrains.get(villager).makeSmart(name));
		villager.setCustomName(Component.literal(name));
		villager.setCustomNameVisible(true);
		return name;
	}

	/** Gives an already-smart villager a new name (and shows it). */
	public static void rename(Villager villager, String name) {
		VillagerBrains.set(villager, VillagerBrains.get(villager).withName(name));
		villager.setCustomName(Component.literal(name));
		villager.setCustomNameVisible(true);
	}
}
