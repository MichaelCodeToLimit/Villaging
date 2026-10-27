package com.villaging.villager;

import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The one place that decides whether a villager may change a block or hurt an entity. */
public final class Protection {
	private Protection() {
	}

	/**
	 * Null when the block may be changed; otherwise why not. With {@code respectProtection} the king is treated as the
	 * one acting: build rights, spawn protection, the world border and protection mods all apply.
	 */
	public static String blockProblem(ServerLevel level, UUID kingId, BlockPos pos, BlockState state, BlockEntity entity,
			boolean respectProtection) {
		if (!respectProtection) {
			return null;
		}
		ServerPlayer king = level.getServer().getPlayerList().getPlayer(kingId);
		if (king == null) {
			return "the king is not here to authorise that";
		}
		if (!king.getAbilities().mayBuild || !level.mayInteract(king, pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return "that area is protected";
		}
		if (!PlayerBlockBreakEvents.BEFORE.invoker().beforeBlockBreak(level, king, pos, state, entity)) {
			return "something is protecting that block";
		}
		return null;
	}

	/** Null when the entity may be attacked; otherwise why not. This never depends on a setting. */
	public static String entityProblem(Entity target) {
		boolean owned = (target instanceof TamableAnimal t && t.isTame())
				|| (target instanceof OwnableEntity o && o.getOwnerReference() != null);
		if (ProtectionRules.isForbiddenTarget(target instanceof Player, owned)) {
			return "I won't hurt a player or a pet";
		}
		return null;
	}

	/**
	 * Null when the king could place or use something at {@code pos}; otherwise why not. With {@code respectProtection}
	 * off nothing is checked.
	 */
	public static String interactProblem(ServerLevel level, UUID kingId, BlockPos pos, boolean respectProtection) {
		if (!respectProtection) {
			return null;
		}
		ServerPlayer king = level.getServer().getPlayerList().getPlayer(kingId);
		if (king == null) {
			return "the king is not here to authorise that";
		}
		if (!king.getAbilities().mayBuild || !level.mayInteract(king, pos) || !level.getWorldBorder().isWithinBounds(pos)) {
			return "that area is protected";
		}
		return null;
	}
}
