package com.villaging.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Builders for tool parameter schemas and the argument checks every tool validator uses. */
public final class ToolSchema {
	private ToolSchema() {
	}

	public record Prop(String name, JsonObject schema) {
	}

	public static Prop prop(String name, String type, String description) {
		JsonObject o = new JsonObject();
		o.addProperty("type", type);
		o.addProperty("description", description);
		return new Prop(name, o);
	}

	public static Prop enumProp(String name, String description, String... values) {
		Prop p = prop(name, "string", description);
		JsonArray arr = new JsonArray();
		for (String v : values) {
			arr.add(v);
		}
		p.schema().add("enum", arr);
		return p;
	}

	public static Prop arrayOfStrings(String name, String description) {
		Prop p = prop(name, "array", description);
		JsonObject items = new JsonObject();
		items.addProperty("type", "string");
		p.schema().add("items", items);
		return p;
	}

	public static Builder object(Prop... props) {
		return new Builder(props);
	}

	public static final class Builder {
		private final JsonObject root = new JsonObject();

		private Builder(Prop[] props) {
			JsonObject properties = new JsonObject();
			root.addProperty("type", "object");
			for (Prop p : props) {
				properties.add(p.name(), p.schema());
			}
			root.add("properties", properties);
		}

		public Builder required(String... names) {
			JsonArray arr = new JsonArray();
			for (String n : names) {
				arr.add(n);
			}
			root.add("required", arr);
			return this;
		}

		public JsonObject build() {
			return root;
		}
	}

	/** A whole number between {@code min} and {@code max}, both inclusive. */
	public static Optional<String> intIn(JsonObject args, String key, int min, int max) {
		JsonElement e = args.get(key);
		if (e == null || !e.isJsonPrimitive() || !((JsonPrimitive) e).isNumber()) {
			return Optional.of("\"" + key + "\" must be a number.");
		}
		double v = e.getAsDouble();
		if (Double.isNaN(v) || Double.isInfinite(v) || v != Math.rint(v)) {
			return Optional.of("\"" + key + "\" must be a whole number.");
		}
		if (v < min || v > max) {
			return Optional.of("\"" + key + "\" must be between " + min + " and " + max + ".");
		}
		return Optional.empty();
	}

	public static Optional<String> stringNonBlank(JsonObject args, String key, int maxLength) {
		JsonElement e = args.get(key);
		if (e == null || !e.isJsonPrimitive() || !((JsonPrimitive) e).isString() || e.getAsString().isBlank()) {
			return Optional.of("\"" + key + "\" must be non-blank text.");
		}
		if (e.getAsString().length() > maxLength) {
			return Optional.of("\"" + key + "\" is over " + maxLength + " characters; shorten it.");
		}
		return Optional.empty();
	}

	public static Optional<String> oneOf(JsonObject args, String key, Set<String> allowed) {
		Optional<String> text = stringNonBlank(args, key, 100);
		if (text.isPresent()) {
			return text;
		}
		if (!allowed.contains(args.get(key).getAsString())) {
			return Optional.of("\"" + key + "\" must be one of " + new TreeSet<>(allowed) + ".");
		}
		return Optional.empty();
	}

	/** Checks x, y and z are finite numbers (whole when {@code whole}) and within {@code range} of the origin. */
	public static Optional<String> coordinates(JsonObject args, boolean whole, int range, double ox, double oy,
			double oz) {
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
}
