package com.villaging.villager;

import com.villaging.config.VillagingConfig;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Walk close to a block and use it with an empty hand: doors, levers, buttons, beds. */
public final class UseBlockAction implements VillagerAction {
	private static final double REACH = 4.5;
	private static final int TIMEOUT_TICKS = 600;

	private final BlockPos pos;
	private final UUID kingId;
	private final VillagingConfig config;
	private int ticks;

	public UseBlockAction(BlockPos pos, UUID kingId, VillagingConfig config) {
		this.pos = pos;
		this.kingId = kingId;
		this.config = config;
	}

	@Override
	public Status tick(PathfinderMob villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return Status.failed("not in a server world");
		}
		if (ticks++ > TIMEOUT_TICKS) {
			return Status.failed("took too long to get there");
		}
		if (!level.isLoaded(pos)) {
			return Status.failed("that block is not loaded");
		}
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return Status.failed("there is no block at " + pos.toShortString());
		}
		String protection = Protection.interactProblem(level, kingId, pos, config.respectProtection());
		if (protection != null) {
			return Status.failed(protection);
		}
		Status walking = Approach.toward(villager, pos, REACH, ticks);
		if (walking != null) {
			return walking;
		}
		villager.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		VillagerHands hands = HandsManager.get(villager);
		hands.syncFrom();
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		InteractionResult result = state.useWithoutItem(level, hands.player(), hit);
		hands.syncTo();
		villager.swing(InteractionHand.MAIN_HAND);
		String name = state.getBlock().getName().getString();
		return result.consumesAction() ? Status.done("used " + name + " at " + pos.toShortString())
				: Status.failed("nothing happened when I used " + name);
	}
}
