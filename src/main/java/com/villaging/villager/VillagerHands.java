package com.villaging.villager;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The villager's hands: a hidden fake player that is never in the world or the player list. Actions run through
 * vanilla player code with it. Its 36 inventory slots mirror the villager's own {@link VillagerInventory}.
 */
public final class VillagerHands {
	private final PathfinderMob villager;
	private final VillagerPlayer player;

	VillagerHands(PathfinderMob villager) {
		this.villager = villager;
		this.player = new VillagerPlayer((ServerLevel) villager.level(), new GameProfile(villager.getUUID(), "Villager"));
	}

	public VillagerPlayer player() {
		return player;
	}

	/** Moves the fake player to the villager's position and facing and copies the villager's items into it. */
	public void syncFrom() {
		player.setPos(villager.getX(), villager.getY(), villager.getZ());
		player.setYRot(villager.getYRot());
		player.setXRot(villager.getXRot());
		player.setYHeadRot(villager.getYHeadRot());
		VillagerInventory source = VillagerInventories.get(villager);
		for (int i = 0; i < VillagerInventory.SIZE; i++) {
			player.getInventory().setItem(i, source.getItem(i).copy());
		}
	}

	/** Copies the fake player's items back into the villager's inventory after an action changed them. */
	public void syncTo() {
		VillagerInventory target = VillagerInventories.get(villager);
		for (int i = 0; i < VillagerInventory.SIZE; i++) {
			target.setItem(i, player.getInventory().getItem(i).copy());
		}
	}

	/** Selects a slot holding {@code item} as the main hand (the villager shows it too). False when it has none. */
	public boolean hold(Item item) {
		for (int i = 0; i < VillagerInventory.SIZE; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				player.getInventory().setSelectedSlot(i);
				// A copper golem's main hand is the item it is carrying to a chest; do not swap its load for a tool.
				if (!(villager instanceof net.minecraft.world.entity.animal.golem.CopperGolem)) {
					villager.setItemSlot(EquipmentSlot.MAINHAND, stack.copyWithCount(1));
				}
				return true;
			}
		}
		return false;
	}

	public ItemStack heldItem() {
		return player.getItemInHand(InteractionHand.MAIN_HAND);
	}
}
