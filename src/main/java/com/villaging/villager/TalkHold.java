package com.villaging.villager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;

/**
 * While the king has the chat box open with a villager (or golem), it stands still and faces the king. It never
 * interrupts an order it is carrying out; it only stops idle wandering. Main thread only.
 */
public final class TalkHold {
	/** A hold lapses after this many ticks without the king saying anything (5 minutes). */
	public static final long HOLD_TICKS = 6000;

	private record Hold(PathfinderMob mob, UUID king, long expiresAt) {
	}

	private static final Map<UUID, Hold> HOLDS = new HashMap<>();

	private TalkHold() {
	}

	public static void start(PathfinderMob mob, UUID king) {
		HOLDS.put(mob.getUUID(), new Hold(mob, king, mob.level().getGameTime() + HOLD_TICKS));
	}

	/** The king said something: keep the hold going. */
	public static void renew(PathfinderMob mob, UUID king) {
		Hold hold = HOLDS.get(mob.getUUID());
		if (hold != null && hold.king().equals(king)) {
			start(mob, king);
		}
	}

	public static boolean holds(PathfinderMob mob) {
		return HOLDS.containsKey(mob.getUUID());
	}

	public static void release(PathfinderMob mob) {
		HOLDS.remove(mob.getUUID());
	}

	/** The chat box for this entity id was closed by this king. */
	public static void release(int entityId, UUID king) {
		HOLDS.values().removeIf(h -> h.mob().getId() == entityId && h.king().equals(king));
	}

	public static void releaseKing(UUID king) {
		HOLDS.values().removeIf(h -> h.king().equals(king));
	}

	public static void clear() {
		HOLDS.clear();
	}

	public static void tick(MinecraftServer server) {
		for (Hold hold : new ArrayList<>(HOLDS.values())) {
			PathfinderMob mob = hold.mob();
			ServerPlayer king = server.getPlayerList().getPlayer(hold.king());
			if (mob.isRemoved() || !mob.isAlive() || king == null || mob.level().getGameTime() >= hold.expiresAt()) {
				HOLDS.remove(mob.getUUID());
				continue;
			}
			if (!ActionManager.isControlled(mob)) {
				mob.getNavigation().stop();
				mob.getLookControl().setLookAt(king, 30f, 30f);
			}
		}
	}
}
