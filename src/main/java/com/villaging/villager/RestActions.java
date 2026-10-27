package com.villaging.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Sitting still and sleeping. */
final class RestActions {
	private RestActions() {
	}

	/** Stay put until replaced or stopped. */
	static final class Sit implements VillagerAction {
		@Override
		public boolean isContinuous() {
			return true;
		}

		@Override
		public Status tick(Villager villager) {
			villager.getNavigation().stop();
			return Status.RUNNING;
		}
	}

	/** Walk to a bed and lie down. Finishes as soon as the villager is asleep; {@link StayAsleep} then keeps it there. */
	static final class Sleep implements VillagerAction {
		private static final double REACH = 2.5;
		private static final int TIMEOUT_TICKS = 600;

		private final BlockPos pos;
		private int ticks;

		Sleep(BlockPos pos) {
			this.pos = pos;
		}

		@Override
		public Status tick(Villager villager) {
			if (!(villager.level() instanceof ServerLevel level)) {
				return Status.failed("not in a server world");
			}
			if (ticks++ > TIMEOUT_TICKS) {
				return Status.failed("took too long to reach the bed");
			}
			BlockState state = level.getBlockState(pos);
			if (!(state.getBlock() instanceof BedBlock)) {
				return Status.failed("there is no bed at " + pos.toShortString());
			}
			if (state.getValue(BedBlock.OCCUPIED)) {
				return Status.failed("that bed is already taken");
			}
			if (!level.isDarkOutside() && !level.isThundering()) {
				return Status.failed("it isn't night yet, so I can't sleep");
			}
			Status walking = Approach.toward(villager, pos, REACH, ticks);
			if (walking != null) {
				return walking;
			}
			villager.startSleeping(pos);
			return villager.isSleeping() ? Status.done("fell asleep in the bed at " + pos.toShortString())
					: Status.failed("I couldn't lie down in that bed");
		}
	}

	/** Keeps a sleeping villager in bed until it is light again. */
	static final class StayAsleep implements VillagerAction {
		private final BlockPos pos;

		StayAsleep(BlockPos pos) {
			this.pos = pos;
		}

		@Override
		public boolean isContinuous() {
			return true;
		}

		@Override
		public Status tick(Villager villager) {
			if (!(villager.level() instanceof ServerLevel level)) {
				return Status.RUNNING;
			}
			if (!villager.isSleeping()) {
				return Status.done("");
			}
			if (!level.isDarkOutside() && !level.isThundering()) {
				villager.stopSleeping();
				return Status.done("woke up");
			}
			return Status.RUNNING;
		}

		@Override
		public void cancel(Villager villager) {
			if (villager.isSleeping()) {
				villager.stopSleeping();
			}
		}
	}
}
