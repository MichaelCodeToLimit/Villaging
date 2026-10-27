package com.villaging.villager;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/** Describes a villager's own situation for the AI prompt. Main thread only. */
public final class VillagerState {
	private static final double NEARBY_RADIUS = 8.0;

	private VillagerState() {
	}

	public static String describe(Villager villager) {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format(Locale.ROOT, "You are at (%.1f, %.1f, %.1f). Health %.0f/%.0f.\n", villager.getX(),
				villager.getY(), villager.getZ(), villager.getHealth(), villager.getMaxHealth()));

		Map<String, Integer> items = new TreeMap<>();
		SimpleContainer inventory = VillagerInventories.get(villager);
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				items.merge(stack.getHoverName().getString(), stack.getCount(), Integer::sum);
			}
		}
		sb.append("Inventory: ").append(items.isEmpty() ? "empty" : items.toString()).append(".\n");

		Map<String, Integer> nearby = new TreeMap<>();
		for (LivingEntity e : villager.level().getEntitiesOfClass(LivingEntity.class,
				villager.getBoundingBox().inflate(NEARBY_RADIUS))) {
			if (e != villager) {
				nearby.merge(e.getType().getDescription().getString(), 1, Integer::sum);
			}
		}
		sb.append("Nearby (within 8 blocks): ").append(nearby.isEmpty() ? "nothing" : nearby.toString()).append('.');
		return sb.toString();
	}
}
