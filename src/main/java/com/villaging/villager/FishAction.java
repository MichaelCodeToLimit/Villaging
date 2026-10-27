package com.villaging.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/**
 * Fish with a rod near water. A real hook is not simulated: about one bite every 20 seconds is rolled on the vanilla
 * fishing loot table, and each catch wears the rod by one use.
 */
public final class FishAction implements VillagerAction {
	private static final int TICKS_PER_BITE = 400;
	private static final int WATER_RADIUS = 6;

	private final int seconds;
	private int ticks;
	private int caught;
	private boolean started;

	public FishAction(int seconds) {
		this.seconds = seconds;
	}

	@Override
	public Status tick(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return Status.failed("not in a server world");
		}
		VillagerInventory inventory = VillagerInventories.get(villager);
		if (!started) {
			if (inventory.count(Items.FISHING_ROD) < 1) {
				return Status.failed("I need a fishing rod");
			}
			BlockPos water = Stations.find(level, villager.blockPosition(), WATER_RADIUS, s -> s.is(Blocks.WATER));
			if (water == null) {
				return Status.failed("there is no water within " + WATER_RADIUS + " blocks to fish in");
			}
			villager.getLookControl().setLookAt(water.getX() + 0.5, water.getY() + 0.5, water.getZ() + 0.5);
			started = true;
		}
		ticks++;
		if (ticks % 10 == 0) {
			villager.swing(InteractionHand.MAIN_HAND);
		}
		if (ticks % TICKS_PER_BITE == 0) {
			if (!catchOne(villager, level, inventory)) {
				return Status.done("caught " + caught + " item" + (caught == 1 ? "" : "s") + " (my rod broke)");
			}
		}
		if (ticks >= seconds * 20) {
			return caught == 0 ? Status.done("nothing bit in " + seconds + " seconds")
					: Status.done("caught " + caught + " item" + (caught == 1 ? "" : "s"));
		}
		return Status.RUNNING;
	}

	/** Rolls one catch. False when the rod broke doing it. */
	private boolean catchOne(Villager villager, ServerLevel level, VillagerInventory inventory) {
		ItemStack rod = ItemStack.EMPTY;
		int rodSlot = -1;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(Items.FISHING_ROD)) {
				rod = inventory.getItem(i);
				rodSlot = i;
				break;
			}
		}
		if (rod.isEmpty()) {
			return false;
		}
		LootParams params = new LootParams.Builder(level)
				.withParameter(LootContextParams.ORIGIN, villager.position())
				.withParameter(LootContextParams.TOOL, rod)
				.withParameter(LootContextParams.THIS_ENTITY, villager)
				.create(LootContextParamSets.FISHING);
		for (ItemStack loot : level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING).getRandomItems(params)) {
			caught += loot.getCount();
			ItemStack left = inventory.addItem(loot);
			if (!left.isEmpty()) {
				villager.spawnAtLocation(level, left);
			}
		}
		rod.setDamageValue(rod.getDamageValue() + 1);
		if (rod.getDamageValue() >= rod.getMaxDamage()) {
			inventory.setItem(rodSlot, ItemStack.EMPTY);
			return false;
		}
		return true;
	}
}
