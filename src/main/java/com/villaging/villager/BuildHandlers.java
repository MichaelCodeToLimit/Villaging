package com.villaging.villager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villaging.blueprint.AsciiView;
import com.villaging.blueprint.Blueprint;
import com.villaging.blueprint.BlueprintException;
import com.villaging.blueprint.BlueprintStore;
import com.villaging.blueprint.BuildOrder;
import com.villaging.blueprint.Cell;
import com.villaging.blueprint.MaterialPlanner;
import com.villaging.blueprint.ShapePlan;
import com.villaging.blueprint.Templates;
import com.villaging.build.BlueprintFiles;
import com.villaging.build.Blocks;
import com.villaging.build.BuildJob;
import com.villaging.build.BuildJobs;
import com.villaging.build.Stockpile;
import com.villaging.config.BuildSettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.block.state.BlockState;

/** Handlers for the tools that design, list, show and plan blueprints. Main thread. */
final class BuildHandlers {
	private BuildHandlers() {
	}

	static void register() {
		ToolHandlers.register("design_blueprint", BuildHandlers::design);
		ToolHandlers.register("use_template", BuildHandlers::template);
		ToolHandlers.register("list_blueprints", BuildHandlers::list);
		ToolHandlers.register("show_blueprint", BuildHandlers::show);
		ToolHandlers.register("plan_build", BuildHandlers::planBuild);
	}

	static CompletableFuture<String> done(String text) {
		return CompletableFuture.completedFuture("ok: " + text);
	}

	static CompletableFuture<String> failed(String text) {
		return CompletableFuture.completedFuture("failed: " + text);
	}

	private static Path dir(ToolContext ctx) {
		return BlueprintFiles.dir(ctx.server());
	}

	/** "7 wide, 6 high, 7 deep, 312 blocks" */
	static String size(Blueprint b) {
		return b.sizeX() + " wide, " + b.sizeY() + " high, " + b.sizeZ() + " deep, " + BuildOrder.order(b).size() + " blocks";
	}

	private static String materials(Blueprint b) {
		List<String> parts = new ArrayList<>();
		for (MaterialPlanner.Row r : MaterialPlanner.plan(b, Map.of(), Map.of()).rows()) {
			parts.add(r.needed() + " " + MaterialPlanner.readable(r.item()));
		}
		return String.join(", ", parts);
	}

	/** Saves the blueprint unless the world is full of them; returns an error text or null. */
	private static String save(ToolContext ctx, Blueprint b) {
		BuildSettings settings = BlueprintFiles.settings();
		List<String> names = BlueprintStore.names(dir(ctx));
		if (!names.contains(b.name()) && names.size() >= settings.maxSavedBlueprints()) {
			return "this world already has " + names.size() + " blueprints (the most is " + settings.maxSavedBlueprints()
					+ "); reuse one, or save under an existing name to replace it";
		}
		try {
			BlueprintStore.save(dir(ctx), b);
		} catch (IOException e) {
			return "the blueprint could not be saved (" + e.getMessage() + ")";
		}
		return null;
	}

	private static CompletableFuture<String> saved(ToolContext ctx, Blueprint b) {
		String problem = save(ctx, b);
		if (problem != null) {
			return failed(problem);
		}
		return done("saved " + b.name() + ": " + size(b) + ". Materials needed: " + materials(b) + ". Next: plan_build to choose where.");
	}

	private static CompletableFuture<String> design(ToolContext ctx, JsonObject args) {
		try {
			JsonObject plan = JsonParser.parseString(args.get("plan").getAsString()).getAsJsonObject();
			Blueprint b = ShapePlan.compile(args.get("name").getAsString(), plan, BlueprintFiles.settings().maxBlueprintBlocks());
			return saved(ctx, b);
		} catch (BlueprintException e) {
			return failed("the plan has a problem: " + e.getMessage());
		} catch (RuntimeException e) {
			return failed("the plan could not be read as JSON text for an object");
		}
	}

	private static CompletableFuture<String> template(ToolContext ctx, JsonObject args) {
		String id = args.get("template").getAsString();
		Templates.Params d = Templates.defaults(id);
		Templates.Params p = new Templates.Params(
				args.has("width") ? args.get("width").getAsInt() : d.width(),
				args.has("depth") ? args.get("depth").getAsInt() : d.depth(),
				args.has("height") ? args.get("height").getAsInt() : d.height(),
				args.has("style") ? args.get("style").getAsString() : d.style(),
				args.has("roof") ? args.get("roof").getAsString() : d.roof());
		try {
			Blueprint b = Templates.build(id, args.get("name").getAsString(), p, BlueprintFiles.settings().maxBlueprintBlocks());
			Blueprint tagged = new Blueprint(b.name(), b.sizeX(), b.sizeY(), b.sizeZ(), b.palette(), b.cells(), "template:" + id);
			return saved(ctx, tagged);
		} catch (BlueprintException e) {
			return failed(e.getMessage());
		}
	}

	private static CompletableFuture<String> list(ToolContext ctx, JsonObject args) {
		List<String> names = BlueprintStore.names(dir(ctx));
		if (names.isEmpty()) {
			return done("there are no blueprints yet; make one with use_template or design_blueprint");
		}
		List<String> lines = new ArrayList<>();
		int max = BlueprintFiles.settings().maxBlueprintBlocks();
		for (String name : names) {
			Optional<Blueprint> b = BlueprintStore.load(dir(ctx), name, max);
			lines.add(b.map(x -> name + " (" + size(x) + ")").orElse(name + " (damaged, could not be read)"));
		}
		return done(String.join("; ", lines));
	}

	private static Optional<Blueprint> load(ToolContext ctx, String name) {
		return BlueprintStore.load(dir(ctx), name, BlueprintFiles.settings().maxBlueprintBlocks());
	}

	private static CompletableFuture<String> noSuch(ToolContext ctx, String name) {
		List<String> names = BlueprintStore.names(dir(ctx));
		return failed("there is no blueprint called " + name + (names.isEmpty() ? "; make one with use_template or design_blueprint"
				: "; the saved ones are " + String.join(", ", names)));
	}

	private static CompletableFuture<String> show(ToolContext ctx, JsonObject args) {
		String name = args.get("name").getAsString();
		Optional<Blueprint> b = load(ctx, name);
		if (b.isEmpty()) {
			return noSuch(ctx, name);
		}
		return done(AsciiView.layer(b.get(), args.get("layer").getAsInt()));
	}

	private static int quarterTurns(JsonObject args) {
		if (!args.has("rotation")) {
			return 0;
		}
		return switch (args.get("rotation").getAsString()) {
			case "90" -> 1;
			case "180" -> 2;
			case "270" -> 3;
			default -> 0;
		};
	}

	private static CompletableFuture<String> planBuild(ToolContext ctx, JsonObject args) {
		PathfinderMob helper = ctx.villager();
		if (!(helper.level() instanceof ServerLevel level)) {
			return failed("not in a server world");
		}
		Optional<BuildJob> existing = BuildJobs.get(helper);
		if (existing.isPresent() && existing.get().status() == BuildJob.Status.RUNNING) {
			return failed("I am already building " + existing.get().blueprint() + "; use stop_build or cancel_build first");
		}
		String name = args.get("name").getAsString();
		Optional<Blueprint> loaded = load(ctx, name);
		if (loaded.isEmpty()) {
			return noSuch(ctx, name);
		}
		int turns = quarterTurns(args);
		Blueprint b = loaded.get().rotated(turns);
		BlockPos anchor = new BlockPos(args.get("x").getAsInt(), args.get("y").getAsInt(), args.get("z").getAsInt());

		// The game must know every block, and the site is checked cell by cell.
		for (String state : b.palette().values()) {
			if (Blocks.parse(level, state).isEmpty()) {
				return failed("this game has no block \"" + state + "\" used by blueprint " + name);
			}
		}
		List<Cell> order = BuildOrder.order(b);
		int alreadyBuilt = 0;
		int blocked = 0;
		int wet = 0;
		for (Cell c : order) {
			BlockPos pos = anchor.offset(c.x(), c.y(), c.z());
			if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) {
				blocked++;
				continue;
			}
			BlockState wanted = Blocks.rotate(Blocks.parse(level, b.stateAt(c)).orElseThrow(), turns);
			BlockState here = level.getBlockState(pos);
			if (here == wanted) {
				alreadyBuilt++;
			} else if (!here.isAir() && !here.canBeReplaced()) {
				blocked++;
			}
			if (!here.getFluidState().isEmpty()) {
				wet++;
			}
		}

		Optional<BlockPos> chest = args.has("stockpile_x")
				? Optional.of(new BlockPos(args.get("stockpile_x").getAsInt(), args.get("stockpile_y").getAsInt(), args.get("stockpile_z").getAsInt()))
				: Stockpile.nearestChest(level, anchor);
		Map<String, Integer> carried = Stockpile.carried(helper);
		Map<String, Integer> inChest = chest.map(p -> Stockpile.read(level, p)).orElse(Map.of());
		MaterialPlanner.Report report = MaterialPlanner.plan(b, carried, inChest);

		BuildJobs.set(helper, new BuildJob(name, new BuildJob.Pos(anchor.getX(), anchor.getY(), anchor.getZ()), turns,
				chest.map(p -> new BuildJob.Pos(p.getX(), p.getY(), p.getZ())), 0, order.size(), BuildJob.Status.PLANNED, ""));

		StringBuilder sb = new StringBuilder("planned " + name + " at " + anchor.toShortString() + (turns == 0 ? "" : ", turned " + turns * 90 + " degrees")
				+ ": " + order.size() + " blocks. ");
		sb.append(report.complete() ? "I have all the materials. " : "Missing: " + report.describeMissing() + " (I carry "
				+ describe(carried) + "; " + chest.map(p -> "the chest at " + p.toShortString() + " holds " + describe(inChest)).orElse("there is no chest nearby")
				+ "). ");
		if (alreadyBuilt > 0) {
			sb.append(alreadyBuilt).append(" blocks are already in place. ");
		}
		if (blocked > 0) {
			sb.append(blocked).append(" spots are occupied or out of reach and will be skipped. ");
		}
		if (wet > 0) {
			sb.append(wet).append(" spots are in water. ");
		}
		sb.append(report.complete() ? "Say start_build to begin." : "Get what is missing, then say start_build.");
		return done(sb.toString());
	}

	private static String describe(Map<String, Integer> items) {
		if (items.isEmpty()) {
			return "nothing";
		}
		List<String> parts = new ArrayList<>();
		items.entrySet().stream().sorted(Map.Entry.comparingByKey()).limit(8)
				.forEach(e -> parts.add(e.getValue() + " " + MaterialPlanner.readable(e.getKey())));
		return String.join(", ", parts) + (items.size() > 8 ? ", and more" : "");
	}
}
