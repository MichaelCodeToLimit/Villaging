package com.villaging.build;

import com.villaging.Villaging;
import com.villaging.blueprint.Blueprint;
import com.villaging.blueprint.Cell;
import com.villaging.command.VillagingRuntime;
import com.villaging.config.VillagingConfig;
import com.villaging.villager.ContainerAction;
import com.villaging.villager.PlaceBlockAction;
import com.villaging.villager.Protection;
import com.villaging.villager.VillagerAction;
import com.villaging.villager.VillagerInventories;
import com.villaging.villager.VillagerSpeech;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builds a blueprint one block at a time. It is a <em>continuous</em> action: {@code start_build} returns at once and the
 * build keeps ticking until it finishes, pauses for missing materials, or is stopped. All decisions come from the pure
 * {@link BuildEngine}; this class walks, fetches and places by reusing {@link ContainerAction} and {@link PlaceBlockAction}.
 */
public final class BuildAction implements VillagerAction {
	private static final int SAVE_EVERY = 25;
	private static final int MAX_STEPS_PER_TICK = 400;

	private final UUID kingId;
	private final VillagingConfig config;
	private final BuildJob job;
	private final Blueprint blueprint;
	private final BlockPos anchor;
	private final int turns;
	private final Map<String, BlockState> wanted = new HashMap<>();
	private BuildEngine engine;
	private VillagerAction inner;
	private boolean placing;
	private int placingIndex = -1;
	private BlockPos placingPos;
	private int waitTicks;
	private int nextAnnounce = 25;
	private int lastSaved;
	private boolean stockpileBroken;
	private boolean ended;

	public BuildAction(UUID kingId, VillagingConfig config, BuildJob job, Blueprint rotatedBlueprint) {
		this.kingId = kingId;
		this.config = config;
		this.job = job;
		this.blueprint = rotatedBlueprint;
		this.anchor = new BlockPos(job.anchor().x(), job.anchor().y(), job.anchor().z());
		this.turns = job.rotation();
	}

	@Override
	public Status tick(PathfinderMob helper) {
		if (!(helper.level() instanceof ServerLevel level)) {
			return end(helper, Status.failed("not in a server world"));
		}
		if (engine == null) {
			engine = new BuildEngine(com.villaging.blueprint.BuildOrder.order(blueprint), blueprint, anchor.getX(), anchor.getY(), anchor.getZ());
			BuildJobs.set(helper, job.withStatus(BuildJob.Status.RUNNING, ""));
		}
		if (waitTicks > 0) {
			waitTicks--;
			return Status.RUNNING;
		}
		if (inner != null) {
			Status s = inner.tick(helper);
			switch (s.state()) {
				case RUNNING -> {
					return Status.RUNNING;
				}
				case DONE -> finishInner(level, helper, null);
				case FAILED -> finishInner(level, helper, s.message());
			}
			inner = null;
			if (waitTicks > 0) {
				return Status.RUNNING;
			}
		}
		World world = new World(level);
		Carried items = new Carried(helper, level);
		for (int step = 0; step < MAX_STEPS_PER_TICK; step++) {
			BuildEngine.Next next = engine.next(world, items);
			switch (next.step()) {
				case SKIP_DONE, SKIP_BLOCKED -> {
				}
				case PLACE -> {
					Item item = item(next.item());
					placingIndex = next.cellIndex();
					Cell c = engine.cell(placingIndex);
					placingPos = anchor.offset(c.x(), c.y(), c.z());
					placing = true;
					inner = new PlaceBlockAction(item, placingPos, kingId, config);
					return Status.RUNNING;
				}
				case FETCH -> {
					BuildJob.Pos chest = job.stockpile().orElseThrow();
					placing = false;
					inner = new ContainerAction(ContainerAction.Mode.TAKE, new BlockPos(chest.x(), chest.y(), chest.z()), item(next.item()),
							next.count(), kingId, config);
					return Status.RUNNING;
				}
				case PAUSE_MISSING -> {
					return pauseMissing(helper, level, next);
				}
				case FINISHED -> {
					return finished(helper, next.note());
				}
			}
		}
		return Status.RUNNING;
	}

	private void finishInner(ServerLevel level, PathfinderMob helper, String failure) {
		if (placing) {
			if (failure != null) {
				engine.blocked(placingPos.toShortString() + ": " + failure);
			} else {
				BlockState here = level.getBlockState(placingPos);
				BlockState target = wanted(level, engine.cell(placingIndex));
				if (here != target && !here.isAir()) {
					level.setBlock(placingPos, target, 3);
				}
				engine.placed();
				progress(helper, level);
			}
			waitTicks = Math.max(0, config.build().buildTicksPerBlock() - 1);
		} else if (failure != null) {
			stockpileBroken = true;
		}
	}

	private void progress(PathfinderMob helper, ServerLevel level) {
		int percent = engine.total() == 0 ? 100 : engine.done() * 100 / engine.total();
		if (engine.done() - lastSaved >= SAVE_EVERY) {
			lastSaved = engine.done();
			BuildJobs.set(helper, job.withDone(engine.done()).withStatus(BuildJob.Status.RUNNING, ""));
		}
		if (percent >= nextAnnounce && nextAnnounce < 100) {
			VillagerSpeech.say(level.getServer(), helper, kingId, "The build is " + nextAnnounce + "% done, my king.");
			nextAnnounce += 25;
		}
	}

	private Status pauseMissing(PathfinderMob helper, ServerLevel level, BuildEngine.Next next) {
		String text = next.note() + ", my king. Bring it to the chest or tell me to gather it, then say start_build.";
		BuildJobs.set(helper, job.withDone(engine.done()).withStatus(BuildJob.Status.PAUSED_MISSING_MATERIAL, next.note()));
		VillagerSpeech.say(level.getServer(), helper, kingId, text);
		wake(helper, level, "(The build of " + job.blueprint() + " is paused: " + next.note() + ". Gather or craft it with your normal tools, "
				+ "then call start_build.)");
		return end(helper, Status.done("paused: " + next.note()));
	}

	private Status finished(PathfinderMob helper, String summary) {
		BuildJobs.clear(helper);
		if (helper.level() instanceof ServerLevel level) {
			VillagerSpeech.say(level.getServer(), helper, kingId, summary + " Your " + job.blueprint() + " is built, my king.");
		}
		return end(helper, Status.done(summary));
	}

	/** One wake-up of the AI so it can gather what is missing without the king retyping; skipped when the AI is unusable. */
	private void wake(PathfinderMob helper, ServerLevel level, String message) {
		VillagingRuntime runtime = Villaging.runtime();
		ServerPlayer king = level.getServer().getPlayerList().getPlayer(kingId);
		if (runtime != null && king != null && runtime.problem().isEmpty()) {
			runtime.handleAnnouncement(king, helper.getId(), message);
		}
	}

	private Status end(PathfinderMob helper, Status status) {
		if (!ended) {
			ended = true;
			BuildRegistry.finish(helper.getUUID());
		}
		return status;
	}

	/** Stopped, replaced or failed: the job stays saved and paused so {@code start_build} can carry on. */
	@Override
	public void cancel(PathfinderMob helper) {
		if (inner != null) {
			inner.cancel(helper);
		}
		if (!ended && engine != null) {
			BuildJobs.get(helper).ifPresent(saved -> {
				if (saved.status() == BuildJob.Status.RUNNING) {
					BuildJobs.set(helper, saved.withDone(engine.done()).withStatus(BuildJob.Status.PAUSED_BY_KING, "Stopped at "
							+ engine.done() + " of " + engine.total() + "; say start_build to carry on."));
				}
			});
		}
		end(helper, null);
		VillagerAction.super.cancel(helper);
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).orElseThrow();
	}

	private BlockState wanted(ServerLevel level, Cell c) {
		String text = blueprint.stateAt(c);
		return wanted.computeIfAbsent(text, t -> Blocks.rotate(Blocks.parse(level, t).orElseThrow(), turns));
	}

	/** The world as the engine sees it. */
	private final class World implements BuildEngine.World {
		private final ServerLevel level;

		World(ServerLevel level) {
			this.level = level;
		}

		@Override
		public boolean matches(int x, int y, int z, String stateText) {
			BlockState target = wanted.computeIfAbsent(stateText, t -> Blocks.rotate(Blocks.parse(level, t).orElseThrow(), turns));
			return level.isLoaded(new BlockPos(x, y, z)) && level.getBlockState(new BlockPos(x, y, z)) == target;
		}

		@Override
		public boolean isFree(int x, int y, int z) {
			BlockPos pos = new BlockPos(x, y, z);
			if (!level.isLoaded(pos) || !level.isInWorldBounds(pos)) {
				return false;
			}
			BlockState here = level.getBlockState(pos);
			return here.isAir() || here.canBeReplaced();
		}

		@Override
		public boolean supported(int x, int y, int z) {
			BlockPos pos = new BlockPos(x, y, z);
			for (Direction d : Direction.values()) {
				BlockState n = level.getBlockState(pos.relative(d));
				if (!n.isAir() && !n.canBeReplaced()) {
					return true;
				}
			}
			return false;
		}

		@Override
		public boolean mayPlace(int x, int y, int z) {
			return Protection.interactProblem(level, kingId, new BlockPos(x, y, z), config.respectProtection()) == null;
		}
	}

	/** What the helper carries and what is in the chest, as the engine sees it. */
	private final class Carried implements BuildEngine.Items {
		private final PathfinderMob helper;
		private final ServerLevel level;

		Carried(PathfinderMob helper, ServerLevel level) {
			this.helper = helper;
			this.level = level;
		}

		@Override
		public int carried(String itemId) {
			return VillagerInventories.get(helper).count(item(itemId));
		}

		@Override
		public int stockpile(String itemId) {
			if (stockpileBroken || job.stockpile().isEmpty()) {
				return 0;
			}
			BuildJob.Pos chest = job.stockpile().get();
			return Stockpile.read(level, new BlockPos(chest.x(), chest.y(), chest.z())).getOrDefault(itemId, 0);
		}
	}
}
