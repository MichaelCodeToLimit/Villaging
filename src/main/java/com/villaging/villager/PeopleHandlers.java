package com.villaging.villager;

import com.villaging.ai.tools.PeopleTools;
import com.villaging.king.Kings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/** Handlers for rename, set_king_title and recruit. */
final class PeopleHandlers {
	static final double RECRUIT_RANGE = 32;

	private PeopleHandlers() {
	}

	private static CompletableFuture<String> done(String result) {
		return CompletableFuture.completedFuture(result);
	}

	static void register() {
		ToolHandlers.register("rename", (ctx, args) -> {
			String newName = args.get("new_name").getAsString().trim();
			Villager target = ctx.villager();
			if (args.has("villager_name")) {
				String wanted = args.get("villager_name").getAsString().trim();
				target = findSmart(ctx, wanted);
				if (target == null) {
					return done("failed: I can't find a smart villager called " + wanted + " nearby");
				}
			}
			for (Villager other : smartNearby(ctx.villager(), ctx.config().actionRange())) {
				if (other != target && VillagerBrains.get(other).name().equalsIgnoreCase(newName)) {
					return done("failed: a villager is already called " + newName);
				}
			}
			String oldName = VillagerBrains.get(target).name();
			Smartify.rename(target, newName);
			return done("ok: " + oldName + " is now called " + newName);
		});

		ToolHandlers.register("set_king_title", (ctx, args) -> {
			String title = args.get("title").getAsString().trim();
			if (args.get("scope").getAsString().equals("everyone")) {
				Kings.setTitle(ctx.server(), title);
				return done("ok: villagers will call the king " + title
						+ " (a villager with its own title keeps it)");
			}
			VillagerBrains.set(ctx.villager(), VillagerBrains.get(ctx.villager()).withKingTitle(title));
			return done("ok: I will call the king " + title);
		});

		ToolHandlers.register("recruit", (ctx, args) -> {
			int count = args.has("count") ? args.get("count").getAsInt() : 1;
			List<Villager> candidates = candidates(ctx.villager(), count);
			if (candidates.isEmpty()) {
				return done("failed: there are no ordinary villagers within " + (int) RECRUIT_RANGE + " blocks");
			}
			String me = VillagerBrains.get(ctx.villager()).name();
			return AskService.confirmThen(ctx, me + " wants to recruit " + candidates.size() + " villager"
					+ (candidates.size() == 1 ? "" : "s") + " as smart villagers. Allow it?", yes -> {
						if (!yes) {
							return "failed: the king said no";
						}
						if (ctx.villager().isRemoved()) {
							return "failed: that is no longer possible";
						}
						// Things change while the king decides: choose again from whoever is still there.
						Set<String> taken = new HashSet<>(Smartify.nearbyNames(ctx.villager()));
						List<String> names = new ArrayList<>();
						for (Villager v : candidates(ctx.villager(), count)) {
							String name = Smartify.makeSmart(v, taken);
							taken.add(name);
							names.add(name);
						}
						return names.isEmpty() ? "failed: no ordinary villagers are left nearby"
								: "ok: recruited " + String.join(", ", names);
					});
		});
	}

	/** Ordinary (not yet smart) adult villagers within range, nearest first, at most {@code limit}. */
	static List<Villager> candidates(Villager from, int limit) {
		if (!(from.level() instanceof ServerLevel level)) {
			return List.of();
		}
		return level.getEntitiesOfClass(Villager.class, from.getBoundingBox().inflate(RECRUIT_RANGE)).stream()
				.filter(v -> v != from && !v.isRemoved() && v.isAlive() && !v.isBaby() && !VillagerBrains.get(v).smart())
				.sorted(Comparator.comparingDouble(from::distanceToSqr))
				.limit(Math.min(limit, PeopleTools.MAX_RECRUITS)).toList();
	}

	private static List<Villager> smartNearby(Villager from, double range) {
		if (!(from.level() instanceof ServerLevel level)) {
			return List.of();
		}
		return level.getEntitiesOfClass(Villager.class, from.getBoundingBox().inflate(range)).stream()
				.filter(v -> VillagerBrains.get(v).smart()).toList();
	}

	private static Villager findSmart(ToolContext ctx, String name) {
		String wanted = name.toLowerCase(Locale.ROOT);
		for (Villager v : smartNearby(ctx.villager(), ctx.config().actionRange())) {
			if (VillagerBrains.get(v).name().toLowerCase(Locale.ROOT).equals(wanted)) {
				return v;
			}
		}
		return null;
	}
}
