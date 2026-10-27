package com.villaging.villager;

import com.villaging.config.VillagingConfig;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Farming actions: till soil with a hoe, and harvest ripe crops (replanting when seeds are at hand). */
final class FarmActions {
	/** The item that replants each crop. */
	private static final Map<Block, Item> SEEDS = Map.of(Blocks.WHEAT, Items.WHEAT_SEEDS, Blocks.CARROTS, Items.CARROT,
			Blocks.POTATOES, Items.POTATO, Blocks.BEETROOTS, Items.BEETROOT_SEEDS);

	private FarmActions() {
	}

	/** Walk to a block of dirt or grass and till it with a hoe from the inventory. */
	static final class Till implements VillagerAction {
		private final BlockPos pos;
		private final UUID kingId;
		private final VillagingConfig config;
		private int ticks;

		Till(BlockPos pos, UUID kingId, VillagingConfig config) {
			this.pos = pos;
			this.kingId = kingId;
			this.config = config;
		}

		@Override
		public Status tick(Villager villager) {
			if (!(villager.level() instanceof ServerLevel level)) {
				return Status.failed("not in a server world");
			}
			if (ticks++ > 600) {
				return Status.failed("took too long to get there");
			}
			String protection = Protection.interactProblem(level, kingId, pos, config.respectProtection());
			if (protection != null) {
				return Status.failed(protection);
			}
			VillagerInventory inventory = VillagerInventories.get(villager);
			Item hoe = null;
			for (int i = 0; i < inventory.getContainerSize() && hoe == null; i++) {
				if (inventory.getItem(i).is(ItemTags.HOES)) {
					hoe = inventory.getItem(i).getItem();
				}
			}
			if (hoe == null) {
				return Status.failed("I need a hoe to till soil");
			}
			Status walking = Approach.toward(villager, pos, 4.0, ticks);
			if (walking != null) {
				return walking;
			}
			VillagerHands hands = HandsManager.get(villager);
			hands.syncFrom();
			hands.hold(hoe);
			ItemStack stack = hands.player().getItemInHand(InteractionHand.MAIN_HAND);
			BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
			InteractionResult result = hands.player().gameMode.useItemOn(hands.player(), level, stack, InteractionHand.MAIN_HAND, hit);
			hands.syncTo();
			villager.swing(InteractionHand.MAIN_HAND);
			return level.getBlockState(pos).is(Blocks.FARMLAND) ? Status.done("tilled the soil at " + pos.toShortString())
					: Status.failed("I couldn't till that (" + level.getBlockState(pos).getBlock().getName().getString() + ")");
		}
	}

	/** Harvest a fully grown crop by breaking it, then replant it if the villager has the seeds. */
	static final class Harvest implements VillagerAction {
		private final BlockPos pos;
		private final UUID kingId;
		private final VillagingConfig config;
		private MineAction mining;
		private PlaceBlockAction replanting;
		private Item seed;
		private String cropName = "";

		Harvest(BlockPos pos, UUID kingId, VillagingConfig config) {
			this.pos = pos;
			this.kingId = kingId;
			this.config = config;
		}

		@Override
		public Status tick(Villager villager) {
			if (!(villager.level() instanceof ServerLevel level)) {
				return Status.failed("not in a server world");
			}
			if (mining == null && replanting == null) {
				BlockState state = level.getBlockState(pos);
				if (!(state.getBlock() instanceof CropBlock crop)) {
					return Status.failed("there is no crop at " + pos.toShortString());
				}
				if (!crop.isMaxAge(state)) {
					return Status.failed("that crop is not ripe yet");
				}
				cropName = state.getBlock().getName().getString();
				seed = SEEDS.get(state.getBlock());
				mining = new MineAction(pos, kingId, config);
			}
			if (mining != null) {
				Status status = mining.tick(villager);
				if (status.state() == State.RUNNING) {
					return status;
				}
				mining = null;
				if (status.state() == State.FAILED) {
					return status;
				}
				if (seed != null && VillagerInventories.get(villager).count(seed) > 0) {
					replanting = new PlaceBlockAction(seed, pos, kingId, config);
					return Status.RUNNING;
				}
				return Status.done("harvested " + cropName);
			}
			Status status = replanting.tick(villager);
			if (status.state() == State.RUNNING) {
				return status;
			}
			return status.state() == State.DONE ? Status.done("harvested " + cropName + " and replanted it")
					: Status.done("harvested " + cropName + " (could not replant: " + status.message() + ")");
		}

		@Override
		public void cancel(Villager villager) {
			if (mining != null) {
				mining.cancel(villager);
			}
			villager.getNavigation().stop();
		}
	}
}
