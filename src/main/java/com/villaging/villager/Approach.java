package com.villaging.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Shared walking logic for actions that must stand within reach of something. */
final class Approach {
	static final double SPEED = 0.6;
	static final double REACH = 4.0;
	/** A villager that is mid-air cannot path yet; give it a moment before calling a spot unreachable. */
	static final int START_GRACE_TICKS = 40;

	private Approach() {
	}

	/**
	 * Walks toward {@code pos}. Returns null once within {@code reach} of its centre, {@link VillagerAction.Status#RUNNING}
	 * while walking, or a failure when there is no way there. {@code ticks} is how long the action has run.
	 */
	static VillagerAction.Status toward(Villager villager, BlockPos pos, double reach, int ticks) {
		if (villager.getEyePosition().distanceTo(Vec3.atCenterOf(pos)) <= reach) {
			villager.getNavigation().stop();
			return null;
		}
		PathNavigation nav = villager.getNavigation();
		if (nav.isDone() || ticks % 20 == 1) {
			Path path = nav.createPath(pos, 1);
			if ((path == null || !nav.moveTo(path, SPEED)) && ticks > START_GRACE_TICKS) {
				return VillagerAction.Status.failed("I can't find a way to reach that");
			}
		}
		return VillagerAction.Status.RUNNING;
	}
}
