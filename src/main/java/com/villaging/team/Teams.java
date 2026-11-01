package com.villaging.team;

import com.villaging.Villaging;
import com.villaging.villager.Kinds;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;

/**
 * Where teams live: every member carries a copy of its {@link Team}, and the leader's copy is the true one. A member whose
 * leader no longer lists it is treated as having no team.
 */
public final class Teams {
	public static final AttachmentType<Team> ATTACHMENT = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "team"), TeamData.CODEC);
	/** Helpers within this many blocks of the king can be named in team commands. */
	public static final double SEARCH = 512;

	private Teams() {
	}

	/** Forces class initialisation so the attachment is registered during mod init. */
	public static void init() {
	}

	/** The team this helper belongs to, if it still does. */
	public static Optional<Team> teamOf(PathfinderMob helper) {
		Team saved = helper.getAttached(ATTACHMENT);
		if (saved == null) {
			return Optional.empty();
		}
		if (saved.leader().equals(helper.getUUID())) {
			return Optional.of(saved);
		}
		if (helper.level() instanceof ServerLevel level && level.getEntity(saved.leader()) instanceof PathfinderMob leader) {
			Team truth = leader.getAttached(ATTACHMENT);
			if (truth == null || !truth.name().equals(saved.name()) || !truth.has(helper.getUUID())) {
				helper.removeAttached(ATTACHMENT);
				return Optional.empty();
			}
			return Optional.of(truth);
		}
		return Optional.of(saved);
	}

	/** Smart helpers within {@link #SEARCH} blocks of {@code origin}. */
	public static List<PathfinderMob> pool(Entity origin) {
		if (!(origin.level() instanceof ServerLevel level)) {
			return List.of();
		}
		return level.getEntitiesOfClass(PathfinderMob.class, origin.getBoundingBox().inflate(SEARCH), Kinds::isSmart);
	}

	/** The team called {@code name}, read from its leader among the helpers in {@code pool}. */
	public static Optional<Team> find(List<PathfinderMob> pool, String name) {
		for (PathfinderMob helper : pool) {
			Optional<Team> team = teamOf(helper);
			if (team.isPresent() && team.get().name().equals(name) && team.get().leader().equals(helper.getUUID())) {
				return team;
			}
		}
		return Optional.empty();
	}

	/** The members of {@code team} that are loaded right now, leader first. */
	public static List<PathfinderMob> loadedMembers(ServerLevel level, Team team) {
		List<PathfinderMob> loaded = new ArrayList<>();
		for (UUID id : team.members()) {
			if (level.getEntity(id) instanceof PathfinderMob mob && !mob.isRemoved()) {
				loaded.add(mob);
			}
		}
		return loaded;
	}

	/** Saves {@code team} on every loaded member. */
	public static void save(ServerLevel level, Team team) {
		for (PathfinderMob mob : loadedMembers(level, team)) {
			mob.setAttached(ATTACHMENT, team);
		}
	}

	public static void clear(PathfinderMob helper) {
		helper.removeAttached(ATTACHMENT);
	}
}
