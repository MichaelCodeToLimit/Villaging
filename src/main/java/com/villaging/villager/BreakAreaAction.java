package com.villaging.villager;

import com.villaging.config.VillagingConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;

/** Break every block in a box, one at a time, nearest first; stops at the first block it cannot break. */
public final class BreakAreaAction implements VillagerAction {
	private final BlockPos min;
	private final BlockPos max;
	private final UUID kingId;
	private final VillagingConfig config;
	private Queue<BlockPos> queue;
	private MineAction current;
	private int broken;

	public BreakAreaAction(BlockPos a, BlockPos b, UUID kingId, VillagingConfig config) {
		this.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
		this.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
		this.kingId = kingId;
		this.config = config;
	}

	@Override
	public Status tick(PathfinderMob villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return Status.failed("not in a server world");
		}
		if (queue == null) {
			List<BlockPos> targets = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(min, max)) {
				targets.add(p.immutable());
			}
			// Top-down keeps blocks from falling onto the work; nearest first within a layer.
			BlockPos from = villager.blockPosition();
			targets.sort(Comparator.comparingInt((BlockPos p) -> -p.getY()).thenComparingDouble(p -> p.distSqr(from)));
			queue = new ArrayDeque<>(targets);
		}
		while (current == null) {
			BlockPos next = queue.poll();
			if (next == null) {
				return Status.done("broke " + broken + " block" + (broken == 1 ? "" : "s"));
			}
			if (level.isLoaded(next) && !level.getBlockState(next).isAir()) {
				current = new MineAction(next, kingId, config);
			}
		}
		Status status = current.tick(villager);
		if (status.state() == State.RUNNING) {
			return status;
		}
		current = null;
		if (status.state() == State.FAILED) {
			return Status.failed("broke " + broken + " block" + (broken == 1 ? "" : "s") + ", then: " + status.message());
		}
		broken++;
		return Status.RUNNING;
	}

	@Override
	public void cancel(PathfinderMob villager) {
		if (current != null) {
			current.cancel(villager);
		}
		villager.getNavigation().stop();
	}
}
