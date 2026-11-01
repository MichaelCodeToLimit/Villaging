package com.villaging.villager;

import com.villaging.blueprint.Blueprint;
import com.villaging.blueprint.BlueprintStore;
import com.villaging.build.BlueprintFiles;
import com.villaging.build.BuildJob;
import com.villaging.build.BuildJobs;
import com.villaging.build.BuildRegistry;
import com.villaging.config.VillagingConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;

/**
 * Team build slices that are planned but could not start because the world's limit of builds at once was reached. When a
 * build finishes they start, in the order they were queued. Main thread.
 */
public final class TeamBuilds {
	record Waiting(UUID member, UUID king, VillagingConfig config) {
	}

	private static final List<Waiting> WAITING = new ArrayList<>();

	private TeamBuilds() {
	}

	static void wait(Waiting entry) {
		WAITING.removeIf(w -> w.member().equals(entry.member()));
		WAITING.add(entry);
	}

	static int waiting() {
		return WAITING.size();
	}

	public static void clear() {
		WAITING.clear();
	}

	/** Called every server tick. */
	public static void tick(MinecraftServer server) {
		if (WAITING.isEmpty() || !BuildRegistry.consumeSlotFreed()) {
			return;
		}
		Iterator<Waiting> it = WAITING.iterator();
		while (it.hasNext()) {
			Waiting w = it.next();
			PathfinderMob member = find(server, w.member());
			if (member == null) {
				continue; // not loaded right now; keeps its place
			}
			Optional<BuildJob> job = BuildJobs.get(member);
			if (member.isRemoved() || job.isEmpty() || job.get().status() != BuildJob.Status.PLANNED) {
				it.remove();
				continue;
			}
			Optional<Blueprint> part = BlueprintStore.load(BlueprintFiles.dir(server), job.get().blueprint(),
					w.config().build().maxBlueprintBlocks());
			if (part.isEmpty()) {
				it.remove();
				continue;
			}
			ToolContext ctx = new ToolContext(server, member, w.king(), w.config());
			String result = BuildHandlers.begin(ctx, member, job.get(), part.get().rotated(job.get().rotation()));
			if (result.startsWith("failed")) {
				return; // the limit is reached again; wait for the next free place
			}
			it.remove();
		}
	}

	private static PathfinderMob find(MinecraftServer server, UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof PathfinderMob mob) {
				return mob;
			}
		}
		return null;
	}
}
