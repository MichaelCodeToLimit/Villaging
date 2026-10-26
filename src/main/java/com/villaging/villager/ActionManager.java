package com.villaging.villager;

import com.villaging.Villaging;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Holds at most one running {@link VillagerAction} per villager and ticks them. Main thread only,
 * except {@link #isControlled}, which is read by the villager mixin on the same thread.
 */
public final class ActionManager {
	public static final ActionManager INSTANCE = new ActionManager();

	private record Running(Villager villager, VillagerAction action, CompletableFuture<String> future) {
	}

	private final Map<UUID, Running> running = new HashMap<>();

	private ActionManager() {
	}

	public static boolean isControlled(Villager villager) {
		return INSTANCE.running.containsKey(villager.getUUID());
	}

	/** Starts {@code action}, replacing any current one. The future completes with the result text. */
	public CompletableFuture<String> run(Villager villager, VillagerAction action) {
		abandon(villager, "failed: replaced by a newer action");
		CompletableFuture<String> future = new CompletableFuture<>();
		running.put(villager.getUUID(), new Running(villager, action, future));
		if (action.isContinuous()) {
			future.complete("ok");
		}
		return future;
	}

	public void stop(Villager villager) {
		abandon(villager, "failed: stopped");
	}

	private void abandon(Villager villager, String reason) {
		Running existing = running.remove(villager.getUUID());
		if (existing != null) {
			existing.action().cancel(villager);
			existing.future().complete(reason);
		}
	}

	public void tick(ServerLevel level) {
		for (Running r : new ArrayList<>(running.values())) {
			Villager villager = r.villager();
			if (villager.level() != level) {
				continue;
			}
			if (villager.isRemoved() || !villager.isAlive()) {
				running.remove(villager.getUUID());
				r.future().completeExceptionally(new IllegalStateException("villager is gone"));
				continue;
			}
			if (!r.action().isContinuous() && r.future().isCancelled()) {
				running.remove(villager.getUUID());
				r.action().cancel(villager);
				continue;
			}
			VillagerAction.Status status;
			try {
				status = r.action().tick(villager);
			} catch (RuntimeException e) {
				Villaging.LOGGER.error("Villager action crashed", e);
				status = VillagerAction.Status.failed("internal error: " + e.getMessage());
			}
			switch (status.state()) {
				case RUNNING -> {
				}
				case DONE -> {
					running.remove(villager.getUUID());
					villager.getNavigation().stop();
					r.future().complete(status.message().isEmpty() ? "ok" : "ok: " + status.message());
				}
				case FAILED -> {
					running.remove(villager.getUUID());
					r.action().cancel(villager);
					r.future().complete("failed: " + status.message());
				}
			}
		}
	}

	/** Server is stopping: drop everything. */
	public void clear() {
		for (Running r : running.values()) {
			r.future().cancel(true);
		}
		running.clear();
	}
}
