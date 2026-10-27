package com.villaging.villager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Handlers for the read-only look tools. */
final class LookHandlers {
	static final int MAX_CHARS = 1500;
	private static final int DEFAULT_RADIUS = 8;
	/** Blocks so common they would drown out what matters. */
	private static final Set<String> NOISE = Set.of("air", "cave_air", "void_air", "stone", "dirt", "grass_block",
			"deepslate", "tuff", "andesite", "diorite", "granite", "gravel", "netherrack", "short_grass", "tall_grass",
			"bedrock", "sand", "sandstone");

	private LookHandlers() {
	}

	static void register() {
		ToolHandlers.register("look_around", (ctx, args) -> {
			int radius = args.has("radius") ? args.get("radius").getAsInt() : DEFAULT_RADIUS;
			return CompletableFuture.completedFuture("ok: " + lookAround(ctx.villager(), radius));
		});
		ToolHandlers.register("look_at", (ctx, args) -> CompletableFuture.completedFuture("ok: " + lookAt(ctx.villager(),
				new BlockPos(args.get("x").getAsInt(), args.get("y").getAsInt(), args.get("z").getAsInt()))));
		ToolHandlers.register("check_inventory", (ctx, args) ->
				CompletableFuture.completedFuture("ok: " + inventory(ctx.villager())));
	}

	static String inventory(Villager villager) {
		Map<String, Integer> items = new TreeMap<>();
		VillagerInventory inv = VillagerInventories.get(villager);
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (!stack.isEmpty()) {
				items.merge(id(stack), stack.getCount(), Integer::sum);
			}
		}
		return items.isEmpty() ? "I am carrying nothing." : "I am carrying " + items + ".";
	}

	static String lookAt(Villager villager, BlockPos pos) {
		ServerLevel level = (ServerLevel) villager.level();
		if (!level.isLoaded(pos)) {
			return "that position is not loaded";
		}
		BlockState state = level.getBlockState(pos);
		StringBuilder sb = new StringBuilder(blockId(state)).append(" at ").append(pos.toShortString());
		BlockEntity entity = level.getBlockEntity(pos);
		if (entity instanceof Container container) {
			Map<String, Integer> items = new TreeMap<>();
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack stack = container.getItem(i);
				if (!stack.isEmpty()) {
					items.merge(id(stack), stack.getCount(), Integer::sum);
				}
			}
			sb.append(" holding ").append(items.isEmpty() ? "nothing" : items.toString());
		}
		return sb.toString();
	}

	static String lookAround(Villager villager, int radius) {
		ServerLevel level = (ServerLevel) villager.level();
		BlockPos center = villager.blockPosition();
		Map<String, int[]> counts = new LinkedHashMap<>();
		Map<String, BlockPos> nearest = new LinkedHashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
			if (!level.isLoaded(p)) {
				continue;
			}
			String id = blockId(level.getBlockState(p));
			if (NOISE.contains(id)) {
				continue;
			}
			counts.computeIfAbsent(id, k -> new int[1])[0]++;
			BlockPos known = nearest.get(id);
			if (known == null || p.distSqr(center) < known.distSqr(center)) {
				nearest.put(id, p.immutable());
			}
		}
		StringBuilder sb = new StringBuilder("Blocks: ");
		List<String> ids = new ArrayList<>(counts.keySet());
		ids.sort(Comparator.comparingInt((String k) -> -counts.get(k)[0]));
		if (ids.isEmpty()) {
			sb.append("nothing notable");
		}
		for (int i = 0; i < Math.min(ids.size(), 20); i++) {
			String id = ids.get(i);
			sb.append(i == 0 ? "" : "; ").append(id).append(" x").append(counts.get(id)[0]).append(" (nearest ")
					.append(nearest.get(id).toShortString()).append(')');
		}
		sb.append(".\nCreatures: ").append(creatures(level, villager, radius)).append(".\nDropped items: ")
				.append(drops(level, villager, radius)).append('.');
		String text = sb.toString();
		return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) + "..." : text;
	}

	private static String creatures(ServerLevel level, Villager villager, int radius) {
		Map<String, double[]> found = new TreeMap<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(radius))) {
			if (e == villager) {
				continue;
			}
			boolean pet = (e instanceof TamableAnimal t && t.isTame())
					|| (e instanceof OwnableEntity o && o.getOwnerReference() != null);
			String tag = e instanceof Player ? " (player)" : pet ? " (pet)" : "";
			String name = e.getType().getDescription().getString().toLowerCase(Locale.ROOT) + tag;
			double d = e.distanceTo(villager);
			double[] v = found.computeIfAbsent(name, k -> new double[] {0, Double.MAX_VALUE});
			v[0]++;
			v[1] = Math.min(v[1], d);
		}
		if (found.isEmpty()) {
			return "none";
		}
		List<String> parts = new ArrayList<>();
		found.forEach((name, v) -> parts.add(name + " x" + (int) v[0] + String.format(Locale.ROOT, " (nearest %.0f blocks)", v[1])));
		return String.join("; ", parts);
	}

	private static String drops(ServerLevel level, Villager villager, int radius) {
		Map<String, Integer> found = new TreeMap<>();
		for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(radius))) {
			found.merge(id(e.getItem()), e.getItem().getCount(), Integer::sum);
		}
		return found.isEmpty() ? "none" : found.toString();
	}

	private static String blockId(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
	}

	private static String id(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
	}
}
