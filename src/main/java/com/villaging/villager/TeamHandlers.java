package com.villaging.villager;

import com.google.gson.JsonElement;
import com.villaging.command.HelperRoster;
import com.villaging.command.HelperRoster.Ambiguous;
import com.villaging.command.HelperRoster.Found;
import com.villaging.command.HelperRoster.Helper;
import com.villaging.config.VillagingConfig;
import com.villaging.king.Kings;
import com.villaging.team.Team;
import com.villaging.team.TeamException;
import com.villaging.team.Teams;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;

/**
 * Making and changing teams. The static methods are shared by the king's tools and the /villaging team commands; each
 * returns {@code ok: ...} or {@code failed: ...} in plain words.
 */
public final class TeamHandlers {
	private TeamHandlers() {
	}

	static void register() {
		ToolHandlers.register("create_team", (ctx, args) -> {
			List<String> members = new ArrayList<>();
			for (JsonElement m : args.getAsJsonArray("members")) {
				members.add(m.getAsString());
			}
			return kingOnly(ctx, () -> create(ctx.villager(), ctx.config(), args.get("name").getAsString(),
					args.get("leader").getAsString(), members));
		});
		ToolHandlers.register("add_to_team", (ctx, args) -> kingOnly(ctx,
				() -> add(ctx.villager(), ctx.config(), args.get("team").getAsString(), args.get("helper").getAsString())));
		ToolHandlers.register("remove_from_team", (ctx, args) -> kingOnly(ctx,
				() -> remove(ctx.villager(), args.get("team").getAsString(), args.get("helper").getAsString())));
		ToolHandlers.register("disband_team", (ctx, args) -> kingOnly(ctx,
				() -> disband(ctx.villager(), args.get("team").getAsString())));
		ToolHandlers.register("list_teams", (ctx, args) -> CompletableFuture.completedFuture(list(ctx.villager())));
	}

	private static CompletableFuture<String> kingOnly(ToolContext ctx, java.util.function.Supplier<String> action) {
		if (!Kings.isKing(ctx.server(), ctx.kingId())) {
			return CompletableFuture.completedFuture("failed: only the king makes or changes teams");
		}
		return CompletableFuture.completedFuture(action.get());
	}

	record Pick(PathfinderMob mob, String problem) {
	}

	static String nameOf(PathfinderMob mob) {
		return VillagerBrains.get(mob).name();
	}

	/** The helper called {@code query} among {@code pool}: a full name or an unambiguous start of one. */
	static Pick pick(ServerLevel level, List<PathfinderMob> pool, String query) {
		List<Helper> helpers = pool.stream().map(m -> new Helper(m.getId(), nameOf(m))).toList();
		HelperRoster.Match match = HelperRoster.find(helpers, query);
		if (match instanceof Ambiguous ambiguous) {
			return new Pick(null, "which one: " + String.join(", ", ambiguous.names()) + "? Use more of the name");
		}
		if (match instanceof Found found && level.getEntity(found.helper().id()) instanceof PathfinderMob mob) {
			return new Pick(mob, null);
		}
		return new Pick(null, "no smart helper called " + query.trim() + " is here"
				+ (helpers.isEmpty() ? "" : "; helpers: " + String.join(", ", helpers.stream().map(Helper::name).sorted().toList())));
	}

	public static String create(Entity origin, VillagingConfig config, String name, String leaderName, List<String> memberNames) {
		if (!(origin.level() instanceof ServerLevel level)) {
			return "failed: not in a world";
		}
		List<PathfinderMob> pool = Teams.pool(origin);
		Pick leader = pick(level, pool, leaderName);
		if (leader.problem() != null) {
			return "failed: " + leader.problem();
		}
		List<PathfinderMob> mobs = new ArrayList<>();
		mobs.add(leader.mob());
		for (String memberName : memberNames) {
			Pick member = pick(level, pool, memberName);
			if (member.problem() != null) {
				return "failed: " + member.problem();
			}
			if (!mobs.contains(member.mob())) {
				mobs.add(member.mob());
			}
		}
		Team team;
		try {
			team = Team.create(name, leader.mob().getUUID(), mobs.stream().skip(1).map(PathfinderMob::getUUID).toList(),
					config.teams().maxSize());
		} catch (TeamException e) {
			return "failed: " + e.getMessage();
		}
		if (Teams.find(pool, name).isPresent()) {
			return "failed: a team called " + name + " already exists; pick another name or disband it";
		}
		for (PathfinderMob mob : mobs) {
			Optional<Team> other = Teams.teamOf(mob);
			if (other.isPresent()) {
				return "failed: " + nameOf(mob) + " is already in team " + other.get().name() + "; take " + nameOf(mob) + " out of it first";
			}
		}
		Teams.save(level, team);
		return "ok: team " + name + " made. Leader " + nameOf(leader.mob()) + "; members " + names(mobs.subList(1, mobs.size()))
				+ (team.canWork() ? "" : ". It needs at least one more helper before it can take jobs");
	}

	public static String add(Entity origin, VillagingConfig config, String teamName, String helperName) {
		if (!(origin.level() instanceof ServerLevel level)) {
			return "failed: not in a world";
		}
		List<PathfinderMob> pool = Teams.pool(origin);
		Optional<Team> found = Teams.find(pool, teamName);
		if (found.isEmpty()) {
			return "failed: there is no team called " + teamName + " here";
		}
		Pick helper = pick(level, pool, helperName);
		if (helper.problem() != null) {
			return "failed: " + helper.problem();
		}
		Optional<Team> other = Teams.teamOf(helper.mob());
		if (other.isPresent()) {
			return "failed: " + nameOf(helper.mob()) + " is already in team " + other.get().name() + "; take them out of it first";
		}
		try {
			Team grown = found.get().with(helper.mob().getUUID(), config.teams().maxSize());
			Teams.save(level, grown);
			return "ok: " + nameOf(helper.mob()) + " joined team " + teamName + " (" + grown.members().size() + " members)";
		} catch (TeamException e) {
			return "failed: " + e.getMessage();
		}
	}

	public static String remove(Entity origin, String teamName, String helperName) {
		if (!(origin.level() instanceof ServerLevel level)) {
			return "failed: not in a world";
		}
		List<PathfinderMob> pool = Teams.pool(origin);
		Optional<Team> found = Teams.find(pool, teamName);
		if (found.isEmpty()) {
			return "failed: there is no team called " + teamName + " here";
		}
		Pick helper = pick(level, pool, helperName);
		if (helper.problem() != null) {
			return "failed: " + helper.problem();
		}
		try {
			Team smaller = found.get().without(helper.mob().getUUID());
			Teams.clear(helper.mob());
			Teams.save(level, smaller);
			return "ok: " + nameOf(helper.mob()) + " left team " + teamName;
		} catch (TeamException e) {
			return "failed: " + e.getMessage();
		}
	}

	public static String disband(Entity origin, String teamName) {
		if (!(origin.level() instanceof ServerLevel level)) {
			return "failed: not in a world";
		}
		Optional<Team> found = Teams.find(Teams.pool(origin), teamName);
		if (found.isEmpty()) {
			return "failed: there is no team called " + teamName + " here";
		}
		for (PathfinderMob mob : Teams.loadedMembers(level, found.get())) {
			Teams.clear(mob);
		}
		java.nio.file.Path dir = com.villaging.build.BlueprintFiles.dir(level.getServer());
		String prefix = com.villaging.team.TeamJobs.partPrefix(teamName);
		for (String saved : com.villaging.blueprint.BlueprintStore.names(dir)) {
			if (saved.startsWith(prefix)) {
				com.villaging.blueprint.BlueprintStore.delete(dir, saved);
			}
		}
		return "ok: team " + teamName + " is disbanded; its helpers stay smart helpers";
	}

	public static String list(Entity origin) {
		if (!(origin.level() instanceof ServerLevel level)) {
			return "failed: not in a world";
		}
		List<String> lines = new ArrayList<>();
		for (PathfinderMob mob : Teams.pool(origin)) {
			Optional<Team> team = Teams.teamOf(mob);
			if (team.isPresent() && team.get().leader().equals(mob.getUUID())) {
				List<PathfinderMob> rest = Teams.loadedMembers(level, team.get()).stream().filter(m -> !m.getUUID().equals(mob.getUUID())).toList();
				lines.add(team.get().name() + ": leader " + nameOf(mob) + (rest.isEmpty() ? "" : ", members " + names(rest)));
			}
		}
		return lines.isEmpty() ? "ok: there are no teams yet" : "ok: " + String.join("; ", lines);
	}

	static String names(List<PathfinderMob> mobs) {
		return mobs.isEmpty() ? "none" : String.join(", ", mobs.stream().map(TeamHandlers::nameOf).toList());
	}

}
