package com.villaging.villager;

import com.villaging.config.VillagingConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.PathfinderMob;

/** Fell a tree: break the connected logs from the chosen one, lowest first, as far as the villager can reach. */
public final class ChopTreeAction implements VillagerAction {
	static final int MAX_LOGS = 64;

	private final BlockPos start;
	private final UUID kingId;
	private final VillagingConfig config;
	private Queue<BlockPos> queue;
	private MineAction current;
	private int chopped;
	private int total;

	public ChopTreeAction(BlockPos start, UUID kingId, VillagingConfig config) {
		this.start = start;
		this.kingId = kingId;
		this.config = config;
	}

	@Override
	public Status tick(PathfinderMob villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return Status.failed("not in a server world");
		}
		if (queue == null) {
			if (!level.getBlockState(start).is(BlockTags.LOGS)) {
				return Status.failed("there is no log at " + start.toShortString());
			}
			List<BlockPos> logs = connectedLogs(level);
			total = logs.size();
			logs.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingDouble(p -> p.distSqr(start)));
			queue = new ArrayDeque<>(logs);
		}
		while (current == null) {
			BlockPos next = queue.poll();
			if (next == null) {
				return Status.done("chopped " + chopped + " log" + (chopped == 1 ? "" : "s"));
			}
			if (level.getBlockState(next).is(BlockTags.LOGS)) {
				current = new MineAction(next, kingId, config);
			}
		}
		Status status = current.tick(villager);
		if (status.state() == State.RUNNING) {
			return status;
		}
		current = null;
		if (status.state() == State.FAILED) {
			return chopped > 0 ? Status.done("chopped " + chopped + " of " + total + " logs, then: " + status.message())
					: Status.failed(status.message());
		}
		chopped++;
		return Status.RUNNING;
	}

	private List<BlockPos> connectedLogs(ServerLevel level) {
		Set<BlockPos> seen = new HashSet<>();
		List<BlockPos> found = new ArrayList<>();
		ArrayDeque<BlockPos> open = new ArrayDeque<>();
		open.add(start);
		seen.add(start);
		while (!open.isEmpty() && found.size() < MAX_LOGS) {
			BlockPos p = open.poll();
			found.add(p);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						BlockPos n = p.offset(dx, dy, dz);
						if (seen.add(n) && level.isLoaded(n) && level.getBlockState(n).is(BlockTags.LOGS)) {
							open.add(n);
						}
					}
				}
			}
		}
		return found;
	}

	@Override
	public void cancel(PathfinderMob villager) {
		if (current != null) {
			current.cancel(villager);
		}
		villager.getNavigation().stop();
	}
}
