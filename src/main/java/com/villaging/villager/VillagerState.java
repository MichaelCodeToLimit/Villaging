package com.villaging.villager;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;

/** Describes a villager's own situation for the AI prompt. Main thread only. */
public final class VillagerState {
	private static final double NEARBY_RADIUS = 8.0;

	private VillagerState() {
	}

	/** What the helper should know about its team: empty when it has none. */
	private static String teamLine(PathfinderMob villager) {
		java.util.Optional<com.villaging.team.Team> team = com.villaging.team.Teams.teamOf(villager);
		if (team.isEmpty() || !(villager.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return "";
		}
		java.util.List<PathfinderMob> members = com.villaging.team.Teams.loadedMembers(level, team.get());
		if (team.get().leader().equals(villager.getUUID())) {
			java.util.List<String> names = members.stream().filter(m -> m != villager).map(m -> VillagerBrains.get(m).name()).toList();
			return "\nYou lead team " + team.get().name() + ", with members: " + (names.isEmpty() ? "none nearby" : String.join(", ", names))
					+ ". Give the team its jobs with assign (one member, any job) and assign_build (a planned build for everyone), "
					+ "check them with team_status, and tell the king one short summary when they are done.";
		}
		String leader = members.stream().filter(m -> m.getUUID().equals(team.get().leader())).findFirst()
				.map(m -> VillagerBrains.get(m).name()).orElse("your leader");
		return "\nYou are in team " + team.get().name() + ", led by " + leader
				+ ". Do the jobs your leader or the king gives you, and say so when you are done.";
	}

	public static String describe(PathfinderMob villager) {
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
		sb.append(teamLine(villager));
		return sb.toString();
	}
}
