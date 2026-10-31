package com.villaging.villager;

import com.villaging.config.VillagingConfig;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Walk close to a spot and place a block from the villager's inventory there, against a neighbouring block. */
public final class PlaceBlockAction implements VillagerAction {
	private static final double REACH = 4.5;
	private static final int TIMEOUT_TICKS = 600;

	private final Item item;
	private final BlockPos pos;
	private final UUID kingId;
	private final VillagingConfig config;
	private int ticks;

	public PlaceBlockAction(Item item, BlockPos pos, UUID kingId, VillagingConfig config) {
		this.item = item;
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
			return Status.failed("took too long to place that");
		}
		if (!level.isLoaded(pos)) {
			return Status.failed("that spot is not loaded");
		}
		BlockState here = level.getBlockState(pos);
		if (!here.canBeReplaced()) {
			return Status.failed("that spot is already taken by " + here.getBlock().getName().getString());
		}
		String protection = Protection.interactProblem(level, kingId, pos, config.respectProtection());
		if (protection != null) {
			return Status.failed(protection);
		}
		if (VillagerInventories.get(villager).count(item) <= 0) {
			return Status.failed("I don't have " + item.getName(new ItemStack(item)).getString());
		}
		Status walking = Approach.toward(villager, pos, REACH, ticks);
		if (walking != null) {
			return walking;
		}
		villager.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);

		BlockHitResult hit = supportFace(level);
		if (hit == null) {
			return Status.failed("there is nothing next to that spot to build against");
		}
		VillagerHands hands = HandsManager.get(villager);
		hands.syncFrom();
		if (!hands.hold(item)) {
			return Status.failed("I don't have that item");
		}
		ItemStack stack = hands.player().getItemInHand(InteractionHand.MAIN_HAND);
		String name = stack.getHoverName().getString();
		// Sneaking, like a player placing against a door or chest, so the neighbour is built against and not used (opened).
		hands.player().setShiftKeyDown(true);
		InteractionResult result;
		try {
			result = hands.player().gameMode.useItemOn(hands.player(), level, stack, InteractionHand.MAIN_HAND, hit);
		} finally {
			hands.player().setShiftKeyDown(false);
		}
		hands.syncTo();
		villager.swing(InteractionHand.MAIN_HAND);
		if (result.consumesAction() && !level.getBlockState(pos).canBeReplaced()) {
			return Status.done("placed " + name + " at " + pos.toShortString());
		}
		String blockers = blockers(level, villager);
		return Status.failed("I couldn't place " + name + " there ("
				+ (blockers.isEmpty() ? "it may be protected or the spot cannot hold it" : blockers + " is in the way") + ")");
	}

	/** A click on the face of a solid neighbour that points at {@code pos}, or null when there is none. */
	private BlockHitResult supportFace(ServerLevel level) {
		for (Direction d : Direction.values()) {
			BlockPos neighbour = pos.relative(d);
			BlockState state = level.getBlockState(neighbour);
			if (!state.isAir() && !state.canBeReplaced()) {
				Vec3 on = Vec3.atCenterOf(neighbour).add(d.getOpposite().getStepX() * 0.5, d.getOpposite().getStepY() * 0.5,
						d.getOpposite().getStepZ() * 0.5);
				return new BlockHitResult(on, d.getOpposite(), neighbour, false);
			}
		}
		return null;
	}

	/** The creatures standing where the block would go, such as "a villager and a zombie"; empty when none. */
	private String blockers(ServerLevel level, PathfinderMob villager) {
		java.util.List<String> names = new java.util.ArrayList<>();
		for (net.minecraft.world.entity.Entity e : level.getEntities((net.minecraft.world.entity.Entity) null,
				new net.minecraft.world.phys.AABB(pos), entity -> !entity.isRemoved() && entity != villager)) {
			names.add(e.getType().getDescription().getString().toLowerCase(java.util.Locale.ROOT) + (e.isAlive() ? "" : " (dying)"));
		}
		return String.join(" and ", names);
	}
}
