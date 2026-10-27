package com.villaging.villager;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A smart villager's own 36-slot inventory. Armor and hand items stay in the villager's equipment slots. */
public final class VillagerInventory extends SimpleContainer {
	public static final int SIZE = 36;

	public static final Codec<VillagerInventory> CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(
			stacks -> {
				VillagerInventory inv = new VillagerInventory();
				for (int i = 0; i < Math.min(stacks.size(), SIZE); i++) {
					inv.setItem(i, stacks.get(i));
				}
				return inv;
			},
			inv -> {
				List<ItemStack> stacks = new ArrayList<>(SIZE);
				for (int i = 0; i < SIZE; i++) {
					stacks.add(inv.getItem(i));
				}
				return stacks;
			});

	public VillagerInventory() {
		super(SIZE);
	}

	public int count(Item item) {
		int total = 0;
		for (int i = 0; i < getContainerSize(); i++) {
			ItemStack stack = getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	/** Removes exactly {@code amount} of {@code item}, or nothing and false when fewer are held. */
	public boolean remove(Item item, int amount) {
		if (amount < 0 || count(item) < amount) {
			return false;
		}
		int left = amount;
		for (int i = 0; i < getContainerSize() && left > 0; i++) {
			ItemStack stack = getItem(i);
			if (stack.is(item)) {
				int take = Math.min(left, stack.getCount());
				stack.shrink(take);
				left -= take;
				if (stack.isEmpty()) {
					setItem(i, ItemStack.EMPTY);
				}
			}
		}
		setChanged();
		return true;
	}
}
