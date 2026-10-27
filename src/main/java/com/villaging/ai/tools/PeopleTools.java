package com.villaging.ai.tools;

import static com.villaging.ai.ToolSchema.enumProp;
import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.google.gson.JsonObject;
import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import java.util.Optional;
import java.util.Set;

/** Tools about people: renaming villagers, what they call the king, and recruiting more smart villagers. */
public final class PeopleTools {
	public static final int MAX_NAME = 24;
	public static final int MAX_RECRUITS = 5;
	public static final Set<String> SCOPES = Set.of("me", "everyone");
	public static final String[] KINDS = {"any", "villager", "iron_golem", "copper_golem"};

	private PeopleTools() {
	}

	/** A name or title: non-blank, short, no control characters and no speech-bubble quotes. */
	public static Optional<String> checkName(JsonObject args, String key) {
		Optional<String> problem = ToolSchema.stringNonBlank(args, key, MAX_NAME);
		if (problem.isPresent()) {
			return problem;
		}
		String value = args.get(key).getAsString();
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isISOControl(c) || c == '“' || c == '”') {
				return Optional.of("\"" + key + "\" must be plain text without line breaks or curly quotes.");
			}
		}
		return Optional.empty();
	}

	public static void register() {
		Tools.register(new ToolDef("rename",
				"Change a smart villager's name. Without villager_name it renames you; with it, the nearby smart villager "
						+ "of that name. Only do it when the king asks.",
				object(prop("new_name", "string", "The new name, up to " + MAX_NAME + " characters."),
						prop("villager_name", "string", "Current name of another smart villager; leave out to rename yourself."))
						.required("new_name").build()),
				(args, range, ox, oy, oz) -> {
					Optional<String> name = checkName(args, "new_name");
					if (name.isPresent() || !args.has("villager_name")) {
						return name;
					}
					return ToolSchema.stringNonBlank(args, "villager_name", 100);
				});
		Tools.register(new ToolDef("set_king_title",
				"Change what villagers call the king (for example \"Boss\" or \"Sire\"). Scope \"everyone\" changes it for "
						+ "all villagers, \"me\" only for you.",
				object(prop("title", "string", "What to call the king, up to " + MAX_NAME + " characters."),
						enumProp("scope", "Who uses the new title.", "me", "everyone")).required("title", "scope").build()),
				(args, range, ox, oy, oz) -> {
					Optional<String> title = checkName(args, "title");
					return title.isPresent() ? title : ToolSchema.oneOf(args, "scope", SCOPES);
				});
		Tools.register(new ToolDef("recruit",
				"Turn nearby ordinary villagers, iron golems or copper golems into new smart ones, up to " + MAX_RECRUITS
						+ " per order. The king is asked to approve first.",
				object(prop("count", "integer", "How many to recruit, 1 to " + MAX_RECRUITS + "; default 1."),
						enumProp("kind", "What to recruit; default any (the nearest of any kind).", KINDS)).build()),
				(args, range, ox, oy, oz) -> {
					Optional<String> count = args.has("count") ? ToolSchema.intIn(args, "count", 1, MAX_RECRUITS)
							: Optional.empty();
					if (count.isPresent() || !args.has("kind")) {
						return count;
					}
					return ToolSchema.oneOf(args, "kind", Set.of(KINDS));
				});
	}
}
