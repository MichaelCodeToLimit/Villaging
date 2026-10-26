package com.villaging.villager;

import net.minecraft.world.entity.npc.villager.Villager;

/** Walk to a position; succeeds within 2 blocks of it. */
public final class GoToAction implements VillagerAction {
	private static final double SPEED = 0.6;
	private static final int TIMEOUT_TICKS = 600;
	private static final int MAX_REPATHS = 3;

	private final double x;
	private final double y;
	private final double z;
	private int ticks;
	private int repaths;

	public GoToAction(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	@Override
	public Status tick(Villager villager) {
		if (distanceTo(villager) <= 2.0) {
			return Status.done("arrived");
		}
		if (ticks++ == 0) {
			if (!villager.getNavigation().moveTo(x, y, z, SPEED)) {
				return Status.failed("there is no path to that position");
			}
			return Status.RUNNING;
		}
		if (ticks > TIMEOUT_TICKS) {
			return Status.failed("took too long to get there");
		}
		if (villager.getNavigation().isDone()) {
			if (++repaths > MAX_REPATHS) {
				return Status.failed("got stuck on the way");
			}
			if (!villager.getNavigation().moveTo(x, y, z, SPEED)) {
				return Status.failed("lost the path to that position");
			}
		}
		return Status.RUNNING;
	}

	private double distanceTo(Villager v) {
		double dx = v.getX() - x;
		double dy = v.getY() - y;
		double dz = v.getZ() - z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
