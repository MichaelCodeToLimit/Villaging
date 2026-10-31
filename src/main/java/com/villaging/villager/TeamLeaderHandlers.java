package com.villaging.villager;

import com.google.gson.JsonObject;
import com.villaging.Villaging;
import com.villaging.blueprint.Blueprint;
import com.villaging.blueprint.BlueprintException;
import com.villaging.blueprint.BlueprintStore;
import com.villaging.blueprint.BuildOrder;
import com.villaging.build.BuildJob;
import com.villaging.build.BuildJobs;
import com.villaging.build.BuildRegistry;
import com.villaging.command.CommandService.CommandResult;
import com.villaging.command.VillagingRuntime;
import com.villaging.team.Team;
import com.villaging.team.TeamJobs;
import com.villaging.team.TeamSplitter;
import com.villaging.team.Teams;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PathfinderMob;

/** The tools only a team's leader may use: assign, team_status and assign_build. */
final class TeamLeaderHandlers {
	private TeamLeaderHandlers() {
	}

	private static CompletableFuture<String> failed(String text) {
		return CompletableFuture.completedFuture("failed: " + text);
	}

	private static CompletableFuture<String> done(String text) {
		return CompletableFuture.completedFuture("ok: " + text);
	}

	static void register() {
		ToolHandlers.register("assign", TeamLeaderHandlers::assign);
		ToolHandlers.register("team_status", TeamLeaderHandlers::status);
		ToolHandlers.register("assign_build", TeamLeaderHandlers::assignBuild);
	}

	/** The team this helper leads and can give jobs to, or the reason it cannot. */
	private static Object leadership(ToolContext ctx) {
		Optional<Team> team = Teams.teamOf(ctx.villager());
		if (team.isEmpty() || !team.get().leader().equals(ctx.villager().getUUID())) {
			return "only a team leader can do that; the king can make me a leader with create_team";
		}
		if (!team.get().canWork()) {
			return "my team needs at least one more helper before it can take jobs; ask the king to add someone";
		}
		return team.get();
	}

	private static CompletableFuture<String> assign(ToolContext ctx, JsonObject args) {
		if (!(leadership(ctx) instanceof Team team)) {
			return failed((String) leadership(ctx));
		}
		ServerLevel level = (ServerLevel) ctx.villager().level();
		List<PathfinderMob> others = Teams.loadedMembers(level, team).stream()
				.filter(m -> !m.getUUID().equals(ctx.villager().getUUID())).toList();
		TeamHandlers.Pick member = TeamHandlers.pick(level, others, args.get("member").getAsString());
		if (member.problem() != null) {
			return failed(member.problem() + " in my team");
		}
		VillagingRuntime runtime = Villaging.runtime();
		ServerPlayer king = ctx.server().getPlayerList().getPlayer(ctx.kingId());
		if (runtime == null || king == null) {
			return failed("the king is not here to hear the order");
		}
		String order = TeamJobs.order(TeamHandlers.nameOf(ctx.villager()), args.get("task").getAsString());
		CommandResult result = runtime.handleAnnouncement(king, member.mob().getId(), order);
		return result == CommandResult.ACCEPTED ? done("passed the job to " + TeamHandlers.nameOf(member.mob()))
				: failed(VillagingRuntime.rejectionMessage(result, runtime.problem()));
	}

	private static CompletableFuture<String> status(ToolContext ctx, JsonObject args) {
		if (!(leadership(ctx) instanceof Team team)) {
			return failed((String) leadership(ctx));
		}
		ServerLevel level = (ServerLevel) ctx.villager().level();
		List<String> lines = new ArrayList<>();
		for (PathfinderMob member : Teams.loadedMembers(level, team)) {
			Optional<BuildJob> job = BuildJobs.get(member);
			String state;
			if (job.isEmpty()) {
				state = "no build";
			} else {
				BuildJob j = job.get();
				int percent = j.total() == 0 ? 0 : j.done() * 100 / j.total();
				state = switch (j.status()) {
					case PLANNED -> "build planned, not started";
					case RUNNING -> "building, about " + percent + "% done";
					case PAUSED_MISSING_MATERIAL -> "paused, short of materials (" + j.note() + ")";
					case PAUSED_BLOCKED -> "paused, spots blocked";
					case PAUSED_BY_KING -> "paused at " + percent + "%";
				};
			}
			lines.add(TeamHandlers.nameOf(member) + ": " + state);
		}
		int away = team.members().size() - lines.size();
		return done(String.join("; ", lines) + (away > 0 ? "; " + away + " not nearby" : ""));
	}

	private static CompletableFuture<String> assignBuild(ToolContext ctx, JsonObject args) {
		if (!(leadership(ctx) instanceof Team team)) {
			return failed((String) leadership(ctx));
		}
		PathfinderMob leader = ctx.villager();
		ServerLevel level = (ServerLevel) leader.level();
		String name = args.get("blueprint").getAsString();
		Optional<BuildJob> planned = BuildJobs.get(leader);
		if (planned.isEmpty() || !planned.get().blueprint().equals(name)) {
			return failed("plan " + name + " first with plan_build (where to build it), then use assign_build");
		}
		BuildJob job = planned.get();
		if (job.status() == BuildJob.Status.RUNNING || BuildRegistry.isBuilding(leader.getUUID())) {
			return failed("I am already building " + job.blueprint() + "; stop_build or cancel_build first");
		}
		Optional<Blueprint> whole = BuildHandlers.load(ctx, name);
		if (whole.isEmpty()) {
			return BuildHandlers.noSuch(ctx, name);
		}
		List<PathfinderMob> members = Teams.loadedMembers(level, team);
		for (PathfinderMob member : members) {
			if (BuildRegistry.isBuilding(member.getUUID())) {
				return failed(TeamHandlers.nameOf(member) + " is already building something; wait until it is done or stopped");
			}
		}
		List<Blueprint> parts;
		try {
			parts = TeamSplitter.split(whole.get(), members.size(), TeamJobs.partPrefix(team.name()));
			for (Blueprint part : parts) {
				BlueprintStore.save(BuildHandlers.dir(ctx), part);
			}
		} catch (BlueprintException e) {
			return failed("the build could not be split: " + e.getMessage());
		} catch (IOException e) {
			return failed("the build parts could not be saved (" + e.getMessage() + ")");
		}
		List<PathfinderMob> workers = members.subList(0, parts.size());
		for (int i = 0; i < parts.size(); i++) {
			BuildJob part = new BuildJob(parts.get(i).name(), job.anchor(), job.rotation(), job.stockpile(), 0,
					BuildOrder.order(parts.get(i)).size(), BuildJob.Status.PLANNED, "");
			BuildJobs.set(workers.get(i), part);
		}
		boolean big = job.total() > ctx.config().build().buildAskThreshold() || job.note().startsWith("blocked:");
		String question = "Let team " + team.name() + " (" + workers.size() + " helpers) build " + name + " (" + job.total() + " blocks) at "
				+ job.anchor().x() + "," + job.anchor().y() + "," + job.anchor().z() + "?";
		if (big) {
			return AskService.confirmThen(ctx, question, yes -> yes ? startAll(ctx, workers, parts, job, name)
					: "ok: not building; each helper keeps its part planned, say assign_build again when the king wants it");
		}
		return CompletableFuture.completedFuture(startAll(ctx, workers, parts, job, name));
	}

	private static String startAll(ToolContext ctx, List<PathfinderMob> workers, List<Blueprint> parts, BuildJob job, String name) {
		int free = ctx.config().build().maxActiveBuilds() - BuildRegistry.count();
		List<Integer> now = TeamJobs.startOrder(parts.size(), free);
		int started = 0;
		int waiting = 0;
		for (int i = 0; i < parts.size(); i++) {
			PathfinderMob member = workers.get(i);
			if (member.isRemoved()) {
				continue;
			}
			Optional<BuildJob> own = BuildJobs.get(member);
			if (own.isEmpty()) {
				continue;
			}
			if (now.contains(i)) {
				String result = BuildHandlers.begin(ctx, member, own.get(), parts.get(i).rotated(job.rotation()));
				if (result.startsWith("ok")) {
					started++;
					continue;
				}
			}
			TeamBuilds.wait(new TeamBuilds.Waiting(member.getUUID(), ctx.kingId(), ctx.config()));
			waiting++;
		}
		return "ok: " + started + " helpers are building " + name + " together" + (waiting > 0 ? "; " + waiting
				+ " are waiting for a free build place (the limit is " + ctx.config().build().maxActiveBuilds()
				+ " builds at once) and start by themselves when one finishes" : "") + ". Each will say when its part is done.";
	}
}
