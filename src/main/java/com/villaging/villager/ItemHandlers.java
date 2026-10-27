package com.villaging.villager;

import com.google.gson.JsonObject;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Handlers for the item tools. */
final class ItemHandlers {
	/** How close the villager must be to the king to take items from them. */
	private static final double TAKE_DISTANCE = 8.0;

	private ItemHandlers() {
	}

	/** The item with this id, or null when there is none. */
	static Item item(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElse(null);
	}

	private static CompletableFuture<String> failed(String reason) {
		return CompletableFuture.completedFuture("failed: " + reason);
	}

	private static String name(Item item) {
		return new ItemStack(item).getHoverName().getString();
	}

	static void register() {
		ToolHandlers.register("pick_up", (ctx, args) -> {
			Item only = args.has("item") ? item(args.get("item").getAsString()) : null;
			if (args.has("item") && only == null) {
				return failed("there is no item called " + args.get("item").getAsString());
			}
			return ActionManager.INSTANCE.run(ctx.villager(), new PickUpAction(only));
		});
		ToolHandlers.register("drop", (ctx, args) -> {
			Item item = item(args.get("item").getAsString());
			int count = args.get("count").getAsInt();
			if (item == null) {
				return failed("there is no item called " + args.get("item").getAsString());
			}
			VillagerInventory inventory = VillagerInventories.get(ctx.villager());
			java.util.List<ItemStack> taken = inventory.take(item, count);
			if (taken == null) {
				return failed("I only have " + inventory.count(item) + " " + name(item));
			}
			ServerLevel level = (ServerLevel) ctx.villager().level();
			for (ItemStack stack : taken) {
				ctx.villager().spawnAtLocation(level, stack);
			}
			return CompletableFuture.completedFuture("ok: dropped " + count + " " + name(item));
		});
		ToolHandlers.register("give_to_king", (ctx, args) -> {
			Item item = item(args.get("item").getAsString());
			if (item == null) {
				return failed("there is no item called " + args.get("item").getAsString());
			}
			return ActionManager.INSTANCE.run(ctx.villager(),
					new GiveToKingAction(item, args.get("count").getAsInt(), ctx.kingId()));
		});
		ToolHandlers.register("take_from_king", ItemHandlers::takeFromKing);
		ToolHandlers.register("equip", (ctx, args) -> {
			Item item = item(args.get("item").getAsString());
			if (item == null) {
				return failed("there is no item called " + args.get("item").getAsString());
			}
			return CompletableFuture.completedFuture(equip(ctx.villager(), item));
		});
		ToolHandlers.register("use_item", (ctx, args) -> {
			Item item = item(args.get("item").getAsString());
			if (item == null) {
				return failed("there is no item called " + args.get("item").getAsString());
			}
			VillagerHands hands = ctx.hands();
			hands.syncFrom();
			if (!hands.hold(item)) {
				return failed("I don't have " + name(item));
			}
			ItemStack stack = hands.player().getItemInHand(InteractionHand.MAIN_HAND);
			InteractionResult result = hands.player().gameMode.useItem(hands.player(), ctx.villager().level(), stack,
					InteractionHand.MAIN_HAND);
			hands.syncTo();
			ctx.villager().swing(InteractionHand.MAIN_HAND);
			return CompletableFuture.completedFuture(result.consumesAction() ? "ok: used " + name(item)
					: "failed: nothing happened when I used " + name(item));
		});
		ToolHandlers.register("eat", (ctx, args) -> {
			Item wanted = args.has("item") ? item(args.get("item").getAsString()) : null;
			if (args.has("item") && wanted == null) {
				return failed("there is no item called " + args.get("item").getAsString());
			}
			return CompletableFuture.completedFuture(eat(ctx.villager(), wanted));
		});
	}

	/** Moves one item of {@code item} into the villager's matching equipment slot, returning what was worn before. */
	static String equip(Villager villager, Item item) {
		VillagerInventory inventory = VillagerInventories.get(villager);
		if (inventory.count(item) < 1) {
			return "failed: I don't have " + name(item);
		}
		ItemStack one = new ItemStack(item);
		EquipmentSlot slot = villager.getEquipmentSlotForItem(one);
		ItemStack previous = villager.getItemBySlot(slot).copy();
		inventory.remove(item, 1);
		villager.setItemSlot(slot, one);
		if (!previous.isEmpty()) {
			ItemStack left = inventory.addItem(previous);
			if (!left.isEmpty()) {
				villager.spawnAtLocation((ServerLevel) villager.level(), left);
			}
		}
		return "ok: equipped " + name(item);
	}

	/** Eats one food item and heals by its nutrition. */
	static String eat(Villager villager, Item wanted) {
		VillagerInventory inventory = VillagerInventories.get(villager);
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			FoodProperties food = stack.get(DataComponents.FOOD);
			if (food == null || (wanted != null && !stack.is(wanted))) {
				continue;
			}
			String name = stack.getHoverName().getString();
			stack.shrink(1);
			if (stack.isEmpty()) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
			villager.heal(food.nutrition());
			return "ok: ate " + name;
		}
		return wanted == null ? "failed: I have no food" : "failed: I don't have any " + name(wanted) + " to eat";
	}

	private static CompletableFuture<String> takeFromKing(ToolContext ctx, JsonObject args) {
		Item item = item(args.get("item").getAsString());
		int count = args.get("count").getAsInt();
		if (item == null) {
			return failed("there is no item called " + args.get("item").getAsString());
		}
		ServerPlayer king = ctx.server().getPlayerList().getPlayer(ctx.kingId());
		if (king == null || king.level() != ctx.villager().level()) {
			return failed("the king is not here");
		}
		if (ctx.villager().distanceTo(king) > TAKE_DISTANCE) {
			return failed("I need to be next to you to take things");
		}
		// The model cannot prove the king ordered this, so the king always confirms the exact items.
		String question = VillagerBrains.get(ctx.villager()).name() + " wants to take " + count + " " + name(item)
				+ " from you. Allow it?";
		return AskService.confirmThen(ctx, question, yes -> {
			if (!yes) {
				return "failed: the king said no";
			}
			if (ctx.villager().isRemoved() || king.isRemoved()) {
				return "failed: that is no longer possible";
			}
			int have = 0;
			for (ItemStack stack : king.getInventory().getNonEquipmentItems()) {
				if (stack.is(item)) {
					have += stack.getCount();
				}
			}
			if (have < count) {
				return "failed: the king only has " + have + " " + name(item);
			}
			// Move the king's real stacks (keeping names, enchantments and contents), never one oversized stack.
			VillagerInventory inventory = VillagerInventories.get(ctx.villager());
			int left = count;
			int moved = 0;
			for (ItemStack stack : king.getInventory().getNonEquipmentItems()) {
				if (left <= 0 || !stack.is(item)) {
					continue;
				}
				ItemStack part = stack.split(Math.min(left, stack.getCount()));
				left -= part.getCount();
				ItemStack overflow = inventory.addItem(part);
				moved += part.getCount() - overflow.getCount();
				if (!overflow.isEmpty()) {
					king.getInventory().add(overflow);
					break;
				}
			}
			return moved == count ? "ok: took " + count + " " + name(item) + " from the king"
					: "failed: my inventory is full (took " + moved + " and returned the rest)";
		});
	}
}
