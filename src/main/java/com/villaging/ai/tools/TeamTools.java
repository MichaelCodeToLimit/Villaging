package com.villaging.ai.tools;

import static com.villaging.ai.ToolSchema.arrayOfStrings;
import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import com.villaging.team.Team;
import java.util.Optional;

/** Tool definitions for making and changing teams. The handlers live in the Minecraft layer. */
public final class TeamTools {
	/** Most names one create_team call may list (the config's own limit is checked when it runs). */
	public static final int MAX_LISTED = 20;
	/** Longest job text a leader may pass on. */
	public static final int MAX_TASK = 500;

	private TeamTools() {
	}

	private static Optional<String> teamName(JsonObject args) {
		Optional<String> text = ToolSchema.stringNonBlank(args, "name", 32);
		if (text.isPresent()) {
			return text;
		}
		return Team.isValidName(args.get("name").getAsString()) ? Optional.empty()
				: Optional.of("\"name\" must be 1 to 32 lowercase letters, digits or underscores, like builders.");
	}

	private static Optional<String> teamRef(JsonObject args) {
		return ToolSchema.stringNonBlank(args, "team", 32);
	}

	private static Optional<String> teamAndHelper(JsonObject args) {
		Optional<String> bad = teamRef(args);
		return bad.isPresent() ? bad : ToolSchema.stringNonBlank(args, "helper", 100);
	}

	public static void register() {
		Tools.register(new ToolDef("create_team",
				"Make a team of smart helpers with one leader, when the king asks. The leader is given the team's jobs. "
						+ "Names are those of smart villagers or golems.",
				object(prop("name", "string", "Team name: lowercase letters, digits, underscores, like builders."),
						prop("leader", "string", "The leader's name."),
						arrayOfStrings("members", "Names of the other helpers in the team.")).required("name", "leader", "members").build()),
				(args, range, ox, oy, oz) -> {
					Optional<String> bad = teamName(args);
					if (bad.isPresent()) {
						return bad;
					}
					bad = ToolSchema.stringNonBlank(args, "leader", 100);
					if (bad.isPresent()) {
						return bad;
					}
					JsonElement members = args.get("members");
					if (members == null || !members.isJsonArray()) {
						return Optional.of("\"members\" must be a list of helper names.");
					}
					if (members.getAsJsonArray().size() > MAX_LISTED) {
						return Optional.of("\"members\" lists too many names (the most is " + MAX_LISTED + ").");
					}
					for (JsonElement m : members.getAsJsonArray()) {
						if (!m.isJsonPrimitive() || !m.getAsJsonPrimitive().isString() || m.getAsString().isBlank()) {
							return Optional.of("\"members\" must be a list of helper names (text).");
						}
					}
					return Optional.empty();
				});
		Tools.register(new ToolDef("add_to_team", "Add a smart helper to a team, when the king asks.",
				object(prop("team", "string", "The team's name."), prop("helper", "string", "The helper's name."))
						.required("team", "helper").build()),
				(args, range, ox, oy, oz) -> teamAndHelper(args));
		Tools.register(new ToolDef("remove_from_team", "Take a helper out of a team (not its leader), when the king asks.",
				object(prop("team", "string", "The team's name."), prop("helper", "string", "The helper's name."))
						.required("team", "helper").build()),
				(args, range, ox, oy, oz) -> teamAndHelper(args));
		Tools.register(new ToolDef("disband_team", "End a team, when the king asks. The helpers stay smart helpers.",
				object(prop("team", "string", "The team's name.")).required("team").build()),
				(args, range, ox, oy, oz) -> teamRef(args));
		Tools.register(new ToolDef("assign",
				"Team leaders only: pass a job to one member of your team, in plain words. The member does it as if the king said it. "
						+ "Use it to split gathering, guarding or any other work.",
				object(prop("member", "string", "The member's name."),
						prop("task", "string", "What they should do, up to " + MAX_TASK + " characters.")).required("member", "task").build()),
				(args, range, ox, oy, oz) -> {
					Optional<String> bad = ToolSchema.stringNonBlank(args, "member", 100);
					return bad.isPresent() ? bad : ToolSchema.stringNonBlank(args, "task", MAX_TASK);
				});
		Tools.register(new ToolDef("assign_build",
				"Team leaders only: after plan_build, split that build between all your team members so they build at the same time. "
						+ "The king is asked first for big builds.",
				object(prop("blueprint", "string", "The blueprint you planned with plan_build.")).required("blueprint").build()),
				(args, range, ox, oy, oz) -> {
					Optional<String> bad = ToolSchema.stringNonBlank(args, "blueprint", 32);
					if (bad.isPresent()) {
						return bad;
					}
					return com.villaging.blueprint.Blueprint.isValidName(args.get("blueprint").getAsString()) ? Optional.empty()
							: Optional.of("\"blueprint\" must be a blueprint name like stone_cottage.");
				});
		Tools.register(new ToolDef("team_status", "Team leaders only: what each member of your team is doing.", object().build()),
				(args, range, ox, oy, oz) -> Optional.empty());
		Tools.register(new ToolDef("list_teams", "List the teams nearby, their leaders and members.", object().build()),
				(args, range, ox, oy, oz) -> Optional.empty());
	}
}
