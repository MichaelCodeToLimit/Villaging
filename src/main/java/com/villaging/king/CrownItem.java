package com.villaging.king;

import com.villaging.villager.VillagerBrain;
import com.villaging.villager.VillagerBrains;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Using the crown in the air claims kingship; using it on a villager makes that villager smart. */
public class CrownItem extends Item {
	private static final List<String> NAMES = List.of("Bob", "Alma", "Odo", "Mira", "Tobin", "Elsa", "Garrick", "Lysa");

	public CrownItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (Kings.claim(sp.level().getServer(), sp.getUUID())) {
			sp.sendSystemMessage(Component.literal("You are the king. Villagers touched by the crown will serve you."));
		} else {
			sp.sendSystemMessage(Component.literal("This land already has a king."));
		}
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
			InteractionHand hand) {
		if (!(target instanceof Villager villager)) {
			return InteractionResult.PASS;
		}
		if (player.level().isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (!Kings.isKing(sp.level().getServer(), sp.getUUID())) {
			sp.sendSystemMessage(Component.literal("Only the king may use this."));
			return InteractionResult.FAIL;
		}
		VillagerBrain brain = VillagerBrains.get(villager);
		if (brain.smart()) {
			sp.sendSystemMessage(Component.literal(brain.name() + " already serves you."));
			return InteractionResult.SUCCESS_SERVER;
		}
		String name = NAMES.get(villager.getRandom().nextInt(NAMES.size()));
		VillagerBrains.set(villager, brain.makeSmart(name));
		villager.setCustomName(Component.literal(name));
		villager.setCustomNameVisible(true);
		sp.sendSystemMessage(Component.literal(name + " is now your smart villager. Sneak and right-click to talk."));
		return InteractionResult.SUCCESS_SERVER;
	}
}
