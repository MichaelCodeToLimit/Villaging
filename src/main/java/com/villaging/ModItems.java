package com.villaging;

import com.villaging.king.CrownItem;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final ResourceKey<Item> CROWN_KEY = ResourceKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "crown"));

	public static final Item CROWN = Registry.register(BuiltInRegistries.ITEM, CROWN_KEY,
			new CrownItem(new Item.Properties().setId(CROWN_KEY).stacksTo(1)));

	private ModItems() {
	}

	public static void init() {
	}
}
