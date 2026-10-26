package com.villaging.villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

/** Shows a villager's reply in the king's chat and as a short-lived speech bubble over its head. */
public final class VillagerSpeech {
	private static final int BUBBLE_TICKS = 120;
	private static final int BUBBLE_MAX_CHARS = 60;

	private record Bubble(Villager villager, String originalName, long expiresAt) {
	}

	private static final Map<UUID, Bubble> BUBBLES = new HashMap<>();

	private VillagerSpeech() {
	}

	public static void say(MinecraftServer server, Villager villager, UUID kingId, String text) {
		if (villager.isRemoved()) {
			return;
		}
		VillagerBrain brain = VillagerBrains.get(villager);
		String name = brain.name().isEmpty() ? "Villager" : brain.name();
		ServerPlayer king = server.getPlayerList().getPlayer(kingId);
		if (king != null) {
			king.sendSystemMessage(Component.literal("<" + name + "> " + text));
		}
		String shown = text.length() > BUBBLE_MAX_CHARS ? text.substring(0, BUBBLE_MAX_CHARS - 1) + "…" : text;
		villager.setCustomName(Component.literal(shown));
		villager.setCustomNameVisible(true);
		BUBBLES.put(villager.getUUID(), new Bubble(villager, name, villager.level().getGameTime() + BUBBLE_TICKS));
	}

	public static void tick() {
		for (Bubble b : new ArrayList<>(BUBBLES.values())) {
			Villager v = b.villager();
			if (v.isRemoved()) {
				BUBBLES.remove(v.getUUID());
			} else if (v.level().getGameTime() >= b.expiresAt()) {
				v.setCustomName(Component.literal(b.originalName()));
				BUBBLES.remove(v.getUUID());
			}
		}
	}

	public static void clear() {
		BUBBLES.clear();
	}
}
