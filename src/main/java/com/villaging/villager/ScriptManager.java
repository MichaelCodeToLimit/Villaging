package com.villaging.villager;

import com.villaging.Villaging;
import com.villaging.ai.script.Facts;
import com.villaging.ai.script.Script;
import com.villaging.ai.script.ScriptRunner;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;

/**
 * Runs task scripts on a background thread, one per villager. The runner hands every step to the server thread through
 * the same executor the model's own calls use, so a script gets no powers a single tool call lacks.
 */
public final class ScriptManager {
	public static final ScriptManager INSTANCE = new ScriptManager();

	private final Map<UUID, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();
	private final AtomicInteger counter = new AtomicInteger();
	private final ExecutorService threads = Executors.newCachedThreadPool(r -> {
		Thread t = new Thread(r, "villaging-script-" + counter.incrementAndGet());
		t.setDaemon(true);
		return t;
	});

	private ScriptManager() {
	}

	/** Starts {@code script} for the villager, cancelling any script it was already running. */
	public CompletableFuture<String> start(ToolContext ctx, Script script) {
		UUID id = ctx.villager().getUUID();
		AtomicBoolean cancelled = new AtomicBoolean();
		AtomicBoolean previous = cancelFlags.put(id, cancelled);
		if (previous != null) {
			previous.set(true);
		}
		CompletableFuture<String> result = new CompletableFuture<>();
		// If the model's call is cancelled (a newer command arrives), the script stops too.
		result.whenComplete((value, error) -> {
			if (result.isCancelled()) {
				cancelled.set(true);
			}
		});
		MinecraftActionExecutor executor = new MinecraftActionExecutor(ctx.server(), ctx.villager(), ctx.kingId(), ctx.config());
		Facts facts = new WorldFacts(ctx.server(), ctx.villager());
		long maxNanos = ctx.config().maxScriptSeconds() * 1_000_000_000L;
		threads.execute(() -> {
			try {
				result.complete(ScriptRunner.run(script, executor, facts, ctx.config().maxScriptSteps(), maxNanos, System::nanoTime,
						cancelled::get));
			} catch (Throwable t) {
				Villaging.LOGGER.error("Task script crashed", t);
				result.complete("failed: the script crashed: " + t.getMessage());
			} finally {
				cancelFlags.remove(id, cancelled);
			}
		});
		return result;
	}

	/** Stops the villager's script, if any. Returns whether one was running. */
	public boolean cancel(PathfinderMob villager) {
		AtomicBoolean flag = cancelFlags.remove(villager.getUUID());
		if (flag != null) {
			flag.set(true);
			return true;
		}
		return false;
	}

	public void cancelAll() {
		cancelFlags.values().forEach(flag -> flag.set(true));
		cancelFlags.clear();
	}

	/** Reads the world for script conditions, hopping to the server thread for each question. */
	private record WorldFacts(MinecraftServer server, PathfinderMob villager) implements Facts {
		private <T> T onServer(java.util.function.Supplier<T> read) {
			return server.submit(read).join();
		}

		@Override
		public int count(String itemId) {
			Item item = ItemHandlers.item(itemId);
			return item == null ? 0 : onServer(() -> VillagerInventories.get(villager).count(item));
		}

		@Override
		public boolean hasBlock(int x, int y, int z) {
			return onServer(() -> villager.level() instanceof ServerLevel level && level.isLoaded(new BlockPos(x, y, z))
					&& !level.getBlockState(new BlockPos(x, y, z)).isAir());
		}

		@Override
		public double health() {
			return onServer(() -> (double) villager.getHealth());
		}

		@Override
		public boolean isNight() {
			return onServer(() -> villager.level() instanceof ServerLevel level && level.isDarkOutside());
		}
	}
}
