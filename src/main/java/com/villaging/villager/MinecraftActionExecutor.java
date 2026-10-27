package com.villaging.villager;

import com.google.gson.JsonObject;
import com.villaging.ai.ActionExecutor;
import com.villaging.ai.PendingQuestions;
import com.villaging.ai.RiskPolicy;
import com.villaging.ai.ToolCall;
import com.villaging.ai.Tools;
import com.villaging.config.VillagingConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Runs the model's tool calls for one villager on the server main thread. */
public final class MinecraftActionExecutor implements ActionExecutor {
	private final MinecraftServer server;
	private final Villager villager;
	private final UUID kingId;
	private final VillagingConfig config;
	/** Blocks this executor has been asked to break so far; many small jobs add up to mass destruction. */
	private long destroyed;
	private boolean massDestructionApproved;

	public MinecraftActionExecutor(MinecraftServer server, Villager villager, UUID kingId, VillagingConfig config) {
		this.server = server;
		this.villager = villager;
		this.kingId = kingId;
		this.config = config;
	}

	@Override
	public Optional<String> validate(ToolCall call) {
		// With protection switched off the range limit is one of the checks that is skipped.
		int range = config.respectProtection() ? config.actionRange() : 30_000_000;
		return Tools.validate(call, range, villager.getX(), villager.getY(), villager.getZ());
	}

	@Override
	public CompletableFuture<String> execute(ToolCall call) {
		CompletableFuture<String> result = new CompletableFuture<>();
		server.execute(() -> {
			if (villager.isRemoved() || !villager.isAlive()) {
				result.completeExceptionally(new IllegalStateException("villager is gone"));
				return;
			}
			Optional<ToolHandler> handler = ToolHandlers.get(call.name());
			if (handler.isEmpty()) {
				result.complete("failed: unknown tool " + call.name());
				return;
			}
			JsonObject args = call.args() == null ? new JsonObject() : call.args();
			try {
				ToolContext ctx = new ToolContext(server, villager, kingId, config);
				ToolHandler toRun = handler.get();
				Optional<RiskPolicy.Category> risk = riskOf(call, args);
				if (RiskPolicy.mustAsk(risk, VillagerBrains.get(villager).askOff())) {
					chain(askThenRun(ctx, toRun, call, args, risk.get()), result);
				} else {
					chain(toRun.run(ctx, args), result);
				}
			} catch (RuntimeException e) {
				result.complete("failed: " + e.getMessage());
			}
		});
		return result;
	}

	@Override
	public boolean isAlive() {
		return !villager.isRemoved() && villager.isAlive();
	}

	/** Copies the action's outcome into {@code result}, and cancels the action if {@code result} is cancelled. */
	private static void chain(CompletableFuture<String> action, CompletableFuture<String> result) {
		action.whenComplete((value, error) -> {
			if (error != null) {
				result.completeExceptionally(error);
			} else {
				result.complete(value);
			}
		});
		result.whenComplete((value, error) -> {
			if (result.isCancelled()) {
				action.cancel(true);
			}
		});
	}

	private String heldItemId() {
		ItemStack held = villager.getMainHandItem();
		return held.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
	}

	/** Everything that makes this call risky: its own kind, a bed or anchor it would use, or the running destruction total. */
	private Optional<RiskPolicy.Category> riskOf(ToolCall call, JsonObject args) {
		Optional<RiskPolicy.Category> risk = RiskPolicy.classify(call.name(), args, heldItemId());
		if (risk.isEmpty()) {
			risk = blockUseRisk(call, args);
		}
		long volume = RiskPolicy.destructionVolume(call.name(), args);
		if (volume > 0) {
			destroyed += volume;
			if (risk.isEmpty() && destroyed > RiskPolicy.MASS_VOLUME && !massDestructionApproved) {
				risk = Optional.of(RiskPolicy.Category.MASS_DESTRUCTION);
			}
		}
		return risk;
	}

	/** Using a bed outside the Overworld or a respawn anchor explodes, whether used directly or clicked while placing. */
	private Optional<RiskPolicy.Category> blockUseRisk(ToolCall call, JsonObject args) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return Optional.empty();
		}
		boolean inOverworld = level.dimension() == Level.OVERWORLD;
		List<BlockPos> spots = new ArrayList<>();
		if (call.name().equals("use_block") && args.has("x") && args.has("y") && args.has("z")) {
			spots.add(WorldHandlers.pos(args, "x", "y", "z"));
		} else if (call.name().equals("place_block") && args.has("x") && args.has("y") && args.has("z")) {
			BlockPos target = WorldHandlers.pos(args, "x", "y", "z");
			spots.add(target);
			for (Direction d : Direction.values()) {
				spots.add(target.relative(d));
			}
		}
		for (BlockPos p : spots) {
			if (level.isLoaded(p)) {
				Optional<RiskPolicy.Category> risk = RiskPolicy.classifyBlockUse(
						BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).getPath(), inOverworld);
				if (risk.isPresent()) {
					return risk;
				}
			}
		}
		return Optional.empty();
	}

	/**
	 * Risky acts run only after the king says yes in the dialog. Cancelling the call closes the question, and once the act
	 * has started, cancelling stops it too.
	 */
	private CompletableFuture<String> askThenRun(ToolContext ctx, ToolHandler handler, ToolCall call, JsonObject args,
			RiskPolicy.Category category) {
		String name = VillagerBrains.get(villager).name();
		String what = call.name() + " " + args;
		if (what.length() > 200) {
			what = what.substring(0, 200) + "...";
		}
		String question = name + " wants to do something risky (" + category.key() + "): " + what + ". Allow it?";
		CompletableFuture<String> answer = AskService.ask(ctx, question, List.of("Yes", "No"));
		CompletableFuture<String> result = new CompletableFuture<>();
		AtomicReference<CompletableFuture<String>> started = new AtomicReference<>();
		answer.whenComplete((a, error) -> {
			if (result.isDone()) {
				return;
			}
			if (error != null || a.equals(PendingQuestions.NO_ANSWER)) {
				result.complete("failed: the king did not answer");
			} else if (!a.equalsIgnoreCase("yes")) {
				result.complete("failed: the king said no");
			} else {
				if (category == RiskPolicy.Category.MASS_DESTRUCTION) {
					massDestructionApproved = true;
				}
				CompletableFuture<String> run;
				try {
					run = handler.run(ctx, args);
				} catch (RuntimeException e) {
					result.complete("failed: " + e.getMessage());
					return;
				}
				started.set(run);
				run.whenComplete((value, runError) -> {
					if (runError != null) {
						result.completeExceptionally(runError);
					} else {
						result.complete(value);
					}
				});
				if (result.isDone()) {
					run.cancel(true);
				}
			}
		});
		result.whenComplete((value, error) -> {
			if (result.isCancelled()) {
				answer.cancel(true);
				CompletableFuture<String> run = started.get();
				if (run != null) {
					run.cancel(true);
				}
			}
		});
		return result;
	}
}
