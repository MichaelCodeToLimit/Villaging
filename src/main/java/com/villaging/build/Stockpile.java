package com.villaging.build;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/** What a build can draw on: the chest the king pointed at (a double chest counts as one) and what the helper carries. */
public final class Stockpile {
	public static final int SEARCH_RADIUS = 8;

	private Stockpile() {
	}

	/** Item id to count in the container at {@code pos}; empty when there is no container there. */
	public static Map<String, Integer> read(ServerLevel level, BlockPos pos) {
		Container container = level.isLoaded(pos) ? HopperBlockEntity.getContainerAt(level, pos) : null;
		return container == null ? Map.of() : count(container);
	}

	/** Item id to count of what the helper carries. */
	public static Map<String, Integer> carried(PathfinderMob helper) {
		return count(com.villaging.villager.VillagerInventories.get(helper));
	}

	/** The nearest chest or barrel within {@link #SEARCH_RADIUS} blocks of {@code center}. */
	public static Optional<BlockPos> nearestChest(ServerLevel level, BlockPos center) {
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-SEARCH_RADIUS, -SEARCH_RADIUS, -SEARCH_RADIUS),
				center.offset(SEARCH_RADIUS, SEARCH_RADIUS, SEARCH_RADIUS))) {
			if (!level.isLoaded(p)) {
				continue;
			}
			BlockEntity entity = level.getBlockEntity(p);
			if (entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity) {
				double d = p.distSqr(center);
				if (d < bestDistance) {
					bestDistance = d;
					best = p.immutable();
				}
			}
		}
		return Optional.ofNullable(best);
	}

	private static Map<String, Integer> count(Container container) {
		Map<String, Integer> counts = new HashMap<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack stack = container.getItem(i);
			if (!stack.isEmpty()) {
				counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
			}
		}
		return counts;
	}
}
