package com.villaging.villager;

import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Finds crafting stations (tables, furnaces, brewing stands) near a villager. */
final class Stations {
	static final int RADIUS = 16;

	private Stations() {
	}

	/** The nearest loaded block within {@code radius} of {@code center} whose state matches, or null. */
	static BlockPos find(ServerLevel level, BlockPos center, int radius, Predicate<BlockState> matches) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			if (!level.isLoaded(p) || !matches.test(level.getBlockState(p))) {
				continue;
			}
			double d = p.distSqr(center);
			if (d < bestDistance) {
				bestDistance = d;
				best = p.immutable();
			}
		}
		return best;
	}
}
