package com.villaging.villager;

import com.villaging.config.VillagingConfig;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/** Walk next to a block and break it by hand. */
public final class MineAction implements VillagerAction {
	private static final double SPEED = 0.6;
	private static final double REACH = 4.0;
	private static final float MAX_HARDNESS = 6.0f;
	private static final int TIMEOUT_TICKS = 1200;
	/** A villager that is mid-air cannot path yet; give it a moment before calling the block unreachable. */
	private static final int START_GRACE_TICKS = 40;

	private final BlockPos pos;
	private final UUID kingId;
	private int ticks;
	private int mined;
	private int needed = -1;
	private final VillagingConfig config;
	private boolean protectionChecked;

	/** @param kingId the king the villager works for; protection is checked as if the king broke the block */
	public MineAction(BlockPos pos, UUID kingId) {
		this(pos, kingId, VillagingConfig.defaults());
	}

	public MineAction(BlockPos pos, UUID kingId, VillagingConfig config) {
		this.config = config;
		this.pos = pos;
		this.kingId = kingId;
	}

	@Override
	public Status tick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return Status.failed("not in a server world");
		}
		if (ticks++ > TIMEOUT_TICKS) {
			return Status.failed("took too long to mine that");
		}
		if (!level.isLoaded(pos)) {
			return Status.failed("that block is not loaded");
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return Status.failed("there is no block at " + pos.toShortString());
		}
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0 && !ProtectionRules.canBreakUnbreakable(config.allowBreakBedrock())) {
			return Status.failed("that block cannot be broken");
		}
		if (hardness > MAX_HARDNESS) {
			return Status.failed("that block is too hard to mine by hand");
		}
		BlockEntity entity = level.getBlockEntity(pos);
		if (entity instanceof Container container && !container.isEmpty()) {
			return Status.failed("container has items");
		}

		if (!protectionChecked) {
			String protection = protectionProblem(level, state, entity);
			if (protection != null) {
				return Status.failed(protection);
			}
			protectionChecked = true;
		}

		Vec3 center = Vec3.atCenterOf(pos);
		if (villager.getEyePosition().distanceTo(center) > REACH) {
			PathNavigation nav = villager.getNavigation();
			if (nav.isDone() || ticks % 20 == 1) {
				Path path = nav.createPath(pos, 1);
				if ((path == null || !nav.moveTo(path, SPEED)) && ticks > START_GRACE_TICKS) {
					return Status.failed("I can't find a way to reach that block");
				}
			}
			return Status.RUNNING;
		}

		villager.getNavigation().stop();
		villager.getLookControl().setLookAt(center.x, center.y, center.z);
		if (needed < 0) {
			needed = hardness < 0 ? 20 : Math.max(10, (int) (hardness * 30));
		}
		mined++;
		if (mined % 5 == 0) {
			villager.swing(InteractionHand.MAIN_HAND);
		}
		level.destroyBlockProgress(villager.getId(), pos, Math.min(9, mined * 10 / needed));
		if (mined >= needed) {
			level.destroyBlockProgress(villager.getId(), pos, -1);
			String changed = protectionProblem(level, state, entity);
			if (changed != null) {
				return Status.failed(changed);
			}
			String name = state.getBlock().getName().getString();
			level.destroyBlock(pos, true, villager, 512);
			return Status.done("mined " + name);
		}
		return Status.RUNNING;
	}

	@Override
	public void cancel(Villager villager) {
		villager.getNavigation().stop();
		if (villager.level() instanceof ServerLevel level) {
			level.destroyBlockProgress(villager.getId(), pos, -1);
		}
	}

	/** Null when the block may be broken; otherwise why not. Applies the same rules as if the king broke it. */
	private String protectionProblem(ServerLevel level, BlockState state, BlockEntity entity) {
		return Protection.blockProblem(level, kingId, pos, state, entity, config.respectProtection());
	}
}
