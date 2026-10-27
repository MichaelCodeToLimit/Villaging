package com.villaging.villager;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

/** Registry of the Minecraft-side handlers that run the tools in {@link com.villaging.ai.Tools}. */
public final class ToolHandlers {
	private static final Map<String, ToolHandler> HANDLERS = new ConcurrentHashMap<>();

	private ToolHandlers() {
	}

	public static void register(String name, ToolHandler handler) {
		HANDLERS.put(name, handler);
	}

	public static void unregister(String name) {
		HANDLERS.remove(name);
	}

	public static Optional<ToolHandler> get(String name) {
		return Optional.ofNullable(HANDLERS.get(name));
	}

	/** Registers every ability group's handlers; safe to call more than once. */
	public static void registerAbilities() {
		AskHandlers.register();
		LookHandlers.register();
		WorldHandlers.register();
		ItemHandlers.register();
		ContainerHandlers.register();
		CraftHandlers.register();
		GatherHandlers.register();
		CombatHandlers.register();
		MoveHandlers.register();
	}

	/** Registers the Foundation tools; safe to call more than once. */
	public static void registerBase() {
		register("say", (ctx, args) -> {
			VillagerSpeech.say(ctx.server(), ctx.villager(), ctx.kingId(), args.get("text").getAsString());
			return CompletableFuture.completedFuture("ok");
		});
		register("stop", (ctx, args) -> {
			ActionManager.INSTANCE.stop(ctx.villager());
			return CompletableFuture.completedFuture("ok: stopped");
		});
		register("follow_me", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(), new FollowAction(ctx.kingId())));
		register("go_to", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(),
				new GoToAction(args.get("x").getAsDouble(), args.get("y").getAsDouble(), args.get("z").getAsDouble())));
		register("mine_block", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(),
				new MineAction(new BlockPos(args.get("x").getAsInt(), args.get("y").getAsInt(), args.get("z").getAsInt()),
						ctx.kingId(), ctx.config())));
	}
}
