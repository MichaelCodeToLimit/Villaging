package com.villaging.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Open registry of the tools the model may call. Later sub-projects register more. */
public final class Tools {
	public static final int MAX_SAY_LENGTH = 500;

	private record Entry(ToolDef def, ArgValidator validator) {
	}

	private static final Map<String, Entry> REGISTRY = new LinkedHashMap<>();

	static {
		register(new ToolDef("say", "Speak to the king in character.",
				schema(prop("text", "string", "What to say.")).required("text").build()),
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
		register(new ToolDef("follow_me", "Follow the king until told to stop.", schema().build()),
				(args, range, ox, oy, oz) -> Optional.empty());
		register(new ToolDef("stop", "Stop the current action.", schema().build()),
				(args, range, ox, oy, oz) -> Optional.empty());
		register(new ToolDef("go_to", "Walk to a position in the world.",
				schema(prop("x", "number", "X coordinate."), prop("y", "number", "Y coordinate."),
						prop("z", "number", "Z coordinate.")).required("x", "y", "z").build()),
				(args, range, ox, oy, oz) -> checkCoordinates(args, false, range, ox, oy, oz));
		register(new ToolDef("mine_block", "Walk next to a block and mine it.",
				schema(prop("x", "integer", "Block X."), prop("y", "integer", "Block Y."),
						prop("z", "integer", "Block Z.")).required("x", "y", "z").build()),
				(args, range, ox, oy, oz) -> checkCoordinates(args, true, range, ox, oy, oz));
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

	private static Optional<String> checkCoordinates(JsonObject args, boolean whole, int range, double ox,
			double oy, double oz) {
		double[] v = new double[3];
		String[] keys = {"x", "y", "z"};
		for (int i = 0; i < 3; i++) {
			JsonElement e = args.get(keys[i]);
			if (e == null || !e.isJsonPrimitive() || !((JsonPrimitive) e).isNumber()) {
				return Optional.of("\"" + keys[i] + "\" must be a number.");
			}
			v[i] = e.getAsDouble();
			if (Double.isNaN(v[i]) || Double.isInfinite(v[i])) {
				return Optional.of("\"" + keys[i] + "\" must be a finite number.");
			}
			if (whole && v[i] != Math.rint(v[i])) {
				return Optional.of("\"" + keys[i] + "\" must be a whole number.");
			}
		}
		double dx = v[0] - ox;
		double dy = v[1] - oy;
		double dz = v[2] - oz;
		if (Math.sqrt(dx * dx + dy * dy + dz * dz) > range) {
			return Optional.of("That position is too far away (limit " + range + " blocks).");
		}
		return Optional.empty();
	}

	private record Prop(String name, String type, String description) {
	}

	private static Prop prop(String name, String type, String description) {
		return new Prop(name, type, description);
	}

	private static SchemaBuilder schema(Prop... props) {
		return new SchemaBuilder(props);
	}

	private static final class SchemaBuilder {
		private final JsonObject root = new JsonObject();
		private final JsonObject properties = new JsonObject();

		SchemaBuilder(Prop[] props) {
			root.addProperty("type", "object");
			for (Prop p : props) {
				JsonObject o = new JsonObject();
				o.addProperty("type", p.type());
				o.addProperty("description", p.description());
				properties.add(p.name(), o);
			}
			root.add("properties", properties);
		}

		SchemaBuilder required(String... names) {
			com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
			for (String n : names) {
				arr.add(n);
			}
			root.add("required", arr);
			return this;
		}

		JsonObject build() {
			return root;
		}
	}
}
