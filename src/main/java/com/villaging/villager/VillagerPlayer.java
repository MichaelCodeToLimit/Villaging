package com.villaging.villager;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.level.ServerLevel;

/**
 * The fake player behind {@link VillagerHands}. Fabric's fake player never ticks, so its attack cooldown would stay
 * empty and every swing would be a weak one; {@link #chargeAttack()} fills it before a swing.
 */
public final class VillagerPlayer extends FakePlayer {
	public VillagerPlayer(ServerLevel level, GameProfile profile) {
		super(level, profile);
	}

	public void chargeAttack() {
		this.attackStrengthTicker = 100;
	}
}
