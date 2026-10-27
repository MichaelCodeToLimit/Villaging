package com.villaging.villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;

/** Shows a villager's reply in the king's chat and as a short-lived speech bubble over its head. */
public final class VillagerSpeech {
	private static final int BUBBLE_TICKS = 120;

	private record Bubble(PathfinderMob villager, long expiresAt) {
	}

	private static final Map<UUID, Bubble> BUBBLES = new HashMap<>();

	private VillagerSpeech() {
	}

	public static void say(MinecraftServer server, PathfinderMob villager, UUID kingId, String text) {
		if (villager.isRemoved() || !villager.isAlive()) {
			return;
		}
		VillagerBrain brain = VillagerBrains.get(villager);
		String name = brain.name().isEmpty() ? "Villager" : brain.name();
		ServerPlayer king = server.getPlayerList().getPlayer(kingId);
		if (king != null) {
			king.sendSystemMessage(Component.literal("<" + name + "> " + text));
		}
		villager.setCustomName(Component.literal(SpeechBubble.format(text)));
		villager.setCustomNameVisible(true);
		BUBBLES.put(villager.getUUID(), new Bubble(villager, villager.level().getGameTime() + BUBBLE_TICKS));
	}

	/** Puts the villager's real name back if (and only if) it is currently showing a speech bubble. */
	public static void heal(PathfinderMob villager) {
		VillagerBrain brain = VillagerBrains.get(villager);
		if (!brain.smart() || villager.getCustomName() == null) {
			return;
		}
		if (SpeechBubble.isBubble(villager.getCustomName().getString())) {
			villager.setCustomName(Component.literal(brain.name()));
		}
	}

	public static void tick() {
		for (Bubble b : new ArrayList<>(BUBBLES.values())) {
			PathfinderMob v = b.villager();
			if (v.isRemoved()) {
				BUBBLES.remove(v.getUUID());
			} else if (v.level().getGameTime() >= b.expiresAt()) {
				heal(v);
				BUBBLES.remove(v.getUUID());
			}
		}
	}

	/** Server is stopping: restore every villager's real name before the world is saved. */
	public static void clear() {
		for (Bubble b : new ArrayList<>(BUBBLES.values())) {
			if (!b.villager().isRemoved()) {
				heal(b.villager());
			}
		}
		BUBBLES.clear();
	}
}
