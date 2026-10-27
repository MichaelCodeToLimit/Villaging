package com.villaging.villager;

import com.villaging.ai.SmartKind;
import com.villaging.ai.tools.PeopleTools;
import com.villaging.king.Kings;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
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
			PathfinderMob target = ctx.villager();
			if (args.has("villager_name")) {
				String wanted = args.get("villager_name").getAsString().trim();
				target = findSmart(ctx, wanted);
				if (target == null) {
					return done("failed: I can't find a smart villager or golem called " + wanted + " nearby");
				}
			}
			for (PathfinderMob other : Smartify.smartNear(ctx.villager(), ctx.config().actionRange())) {
				if (other != target && VillagerBrains.get(other).name().equalsIgnoreCase(newName)) {
					return done("failed: someone is already called " + newName);
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
				return done("ok: everyone will call the king " + title + " (anyone with their own title keeps it)");
			}
			VillagerBrains.set(ctx.villager(), VillagerBrains.get(ctx.villager()).withKingTitle(title));
			return done("ok: I will call the king " + title);
		});

		ToolHandlers.register("recruit", (ctx, args) -> {
			int count = args.has("count") ? args.get("count").getAsInt() : 1;
			Optional<SmartKind> kind = args.has("kind") ? SmartKind.fromKey(args.get("kind").getAsString())
					: Optional.empty();
			List<PathfinderMob> candidates = candidates(ctx.villager(), count, kind);
			if (candidates.isEmpty()) {
				return done("failed: there is nobody like that to recruit within " + (int) RECRUIT_RANGE + " blocks");
			}
			String me = VillagerBrains.get(ctx.villager()).name();
			return AskService.confirmThen(ctx, me + " wants to recruit " + describe(candidates)
					+ " as smart helpers. Allow it?", yes -> {
						if (!yes) {
							return "failed: the king said no";
						}
						if (ctx.villager().isRemoved()) {
							return "failed: that is no longer possible";
						}
						// Things change while the king decides: choose again from whoever is still there.
						Set<String> taken = new HashSet<>(Smartify.nearbyNames(ctx.villager()));
						List<String> names = new ArrayList<>();
						for (PathfinderMob mob : candidates(ctx.villager(), count, kind)) {
							String name = Smartify.makeSmart(mob, taken);
							taken.add(name);
							names.add(name + " (" + Kinds.kindOf(mob).label() + ")");
						}
						return names.isEmpty() ? "failed: nobody like that is left nearby"
								: "ok: recruited " + String.join(", ", names);
					});
		});
	}

	private static String describe(List<PathfinderMob> mobs) {
		if (mobs.size() == 1) {
			return "a " + Kinds.kindOf(mobs.get(0)).label();
		}
		return mobs.size() + " creatures (" + String.join(", ",
				mobs.stream().map(m -> Kinds.kindOf(m).label()).toList()) + ")";
	}

	/** Ordinary (not yet smart) adult villagers and golems within range, nearest first, at most {@code limit}. */
	static List<PathfinderMob> candidates(PathfinderMob from, int limit, Optional<SmartKind> kind) {
		if (!(from.level() instanceof ServerLevel level)) {
			return List.of();
		}
		return level.getEntitiesOfClass(PathfinderMob.class, from.getBoundingBox().inflate(RECRUIT_RANGE)).stream()
				.filter(m -> m != from && Kinds.canBeSmart(m) && !m.isRemoved() && m.isAlive()
						&& !(m instanceof Villager v && v.isBaby()) && !VillagerBrains.get(m).smart()
						&& kind.map(k -> Kinds.kindOf(m) == k).orElse(true))
				.sorted(Comparator.comparingDouble(from::distanceToSqr))
				.limit(Math.min(limit, PeopleTools.MAX_RECRUITS)).toList();
	}

	private static PathfinderMob findSmart(ToolContext ctx, String name) {
		String wanted = name.toLowerCase(Locale.ROOT);
		for (PathfinderMob mob : Smartify.smartNear(ctx.villager(), ctx.config().actionRange())) {
			if (VillagerBrains.get(mob).name().toLowerCase(Locale.ROOT).equals(wanted)) {
				return mob;
			}
		}
		return null;
	}
}
