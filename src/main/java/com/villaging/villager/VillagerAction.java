package com.villaging.villager;

import net.minecraft.world.entity.PathfinderMob;

/** A multi-tick job a villager performs on the server main thread. */
public interface VillagerAction {
	enum State { RUNNING, DONE, FAILED }

	record Status(State state, String message) {
		public static final Status RUNNING = new Status(State.RUNNING, "");

		public static Status done(String message) {
			return new Status(State.DONE, message);
		}

		public static Status failed(String message) {
			return new Status(State.FAILED, message);
		}
	}

	/** Continuous actions (such as following) report success at once and keep ticking until replaced. */
	default boolean isContinuous() {
		return false;
	}

	Status tick(PathfinderMob villager);

	/** Called when the action is replaced, stopped or abandoned; must leave no half-finished state behind. */
	default void cancel(PathfinderMob villager) {
		villager.getNavigation().stop();
	}
}
