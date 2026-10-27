package com.villaging.ai;

import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Open registry of the tools the model may call. Ability groups register more. */
public final class Tools {
	public static final int MAX_SAY_LENGTH = 500;

	private record Entry(ToolDef def, ArgValidator validator) {
	}

	private static final Map<String, Entry> REGISTRY = new LinkedHashMap<>();

	static {
		register(new ToolDef("say", "Speak to the king in character.",
				object(prop("text", "string", "What to say.")).required("text").build()),
				(args, range, ox, oy, oz) -> {
					JsonElement text = args.get("text");
					if (text == null || !text.isJsonPrimitive() || text.getAsString().isBlank()) {
						return Optional.of("say needs non-blank \"text\".");
					}
					if (text.getAsString().length() > MAX_SAY_LENGTH) {
						return Optional.of("say text is over " + MAX_SAY_LENGTH + " characters; shorten it.");
					}
					return Optional.empty();
				});
		register(new ToolDef("follow_me", "Follow the king until told to stop.", object().build()),
				(args, range, ox, oy, oz) -> Optional.empty());
		register(new ToolDef("stop", "Stop the current action.", object().build()),
				(args, range, ox, oy, oz) -> Optional.empty());
		register(new ToolDef("go_to", "Walk to a position in the world.",
				object(prop("x", "number", "X coordinate."), prop("y", "number", "Y coordinate."),
						prop("z", "number", "Z coordinate.")).required("x", "y", "z").build()),
				(args, range, ox, oy, oz) -> ToolSchema.coordinates(args, false, range, ox, oy, oz));
		register(new ToolDef("mine_block", "Walk next to a block and mine it.",
				object(prop("x", "integer", "Block X."), prop("y", "integer", "Block Y."),
						prop("z", "integer", "Block Z.")).required("x", "y", "z").build()),
				(args, range, ox, oy, oz) -> ToolSchema.coordinates(args, true, range, ox, oy, oz));
	}

	private Tools() {
	}

	public static synchronized void register(ToolDef def, ArgValidator validator) {
		REGISTRY.put(def.name(), new Entry(def, validator));
	}

	public static synchronized void unregister(String name) {
		REGISTRY.remove(name);
	}

	public static synchronized List<ToolDef> all() {
		List<ToolDef> defs = new ArrayList<>();
		for (Entry e : REGISTRY.values()) {
			defs.add(e.def());
		}
		return defs;
	}

	public static Optional<String> validate(ToolCall call, int range, double originX, double originY,
			double originZ) {
		Entry entry;
		synchronized (Tools.class) {
			entry = REGISTRY.get(call.name());
		}
		if (entry == null) {
			return Optional.of("Unknown tool \"" + call.name() + "\". Use only the tools you were given.");
		}
		JsonObject args = call.args() == null ? new JsonObject() : call.args();
		return entry.validator().check(args, range, originX, originY, originZ);
	}
}
