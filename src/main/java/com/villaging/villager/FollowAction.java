package com.villaging.villager;

import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;

/** Follow a player until replaced or stopped. */
public final class FollowAction implements VillagerAction {
	private static final double SPEED = 0.7;
	private static final double COMFORT_DISTANCE = 3.5;

	private final UUID playerId;
	private int ticks;

	public FollowAction(UUID playerId) {
		this.playerId = playerId;
	}

	@Override
	public boolean isContinuous() {
		return true;
	}

	@Override
	public Status tick(Villager villager) {
		Player player = villager.level().getPlayerByUUID(playerId);
		if (player == null) {
			return Status.RUNNING; // king is elsewhere; wait for them
		}
		if (villager.distanceTo(player) > COMFORT_DISTANCE) {
			if (ticks++ % 10 == 0) {
				villager.getNavigation().moveTo(player, SPEED);
			}
		} else {
			villager.getNavigation().stop();
			villager.getLookControl().setLookAt(player);
		}
		return Status.RUNNING;
	}
}
