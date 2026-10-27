package com.villaging.ai.script;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.villaging.ai.ToolDef;
import com.villaging.ai.Tools;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns the JSON a model sends into a {@link Script}, rejecting anything outside the small allowed language: tool
 * calls, {@code repeat}, {@code until} and {@code if}. Pure; throws {@link ScriptException} with a fix-it message.
 */
public final class ScriptParser {
	public static final int MAX_STEPS_IN_SOURCE = 200;
	public static final int MAX_REPEAT = 100;

	/** Tools a script may not call, so a script can never start or stop scripts. */
	private static final Set<String> FORBIDDEN_TOOLS = Set.of("run_script", "stop_script");
	private static final Pattern ITEM_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
	private static final Set<String> CONDITION_KEYS = Set.of("have", "has_block", "health_below", "night");

	private ScriptParser() {
	}

	public static Script parse(JsonObject root, int maxDepth) {
		for (String key : root.keySet()) {
			if (!key.equals("steps")) {
				throw new ScriptException("Unknown key \"" + key + "\" in the script; only \"steps\" is allowed at the top.");
			}
		}
		JsonElement steps = root.get("steps");
		if (steps == null || !steps.isJsonArray()) {
			throw new ScriptException("The script needs a \"steps\" list.");
		}
		int[] total = {0};
		return new Script(parseSteps(steps.getAsJsonArray(), 0, maxDepth, total));
	}

	private static List<Step> parseSteps(JsonArray array, int depth, int maxDepth, int[] total) {
		List<Step> steps = new ArrayList<>();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				throw new ScriptException("Every step must be an object.");
			}
			if (++total[0] > MAX_STEPS_IN_SOURCE) {
				throw new ScriptException("The script has more than " + MAX_STEPS_IN_SOURCE + " steps; split the job up.");
			}
			steps.add(parseStep(element.getAsJsonObject(), depth, maxDepth, total));
		}
		return steps;
	}

	private static Step parseStep(JsonObject step, int depth, int maxDepth, int[] total) {
		if (step.has("tool")) {
			allow(step, "tool", "tool", "args");
			return parseCall(step);
		}
		if (step.has("repeat")) {
			allow(step, "repeat", "repeat", "steps");
			JsonElement times = step.get("repeat");
			if (!times.isJsonPrimitive() || !((JsonPrimitive) times).isNumber() || times.getAsDouble() != Math.rint(times.getAsDouble())
					|| times.getAsInt() < 1 || times.getAsInt() > MAX_REPEAT) {
				throw new ScriptException("\"repeat\" must be a whole number from 1 to " + MAX_REPEAT + ".");
			}
			return new Step.Repeat(times.getAsInt(), body(step, "steps", depth, maxDepth, total, true));
		}
		if (step.has("until")) {
			allow(step, "until", "until", "steps");
			return new Step.Until(parseCondition(step.get("until")), body(step, "steps", depth, maxDepth, total, true));
		}
		if (step.has("if")) {
			allow(step, "if", "if", "then", "else");
			List<Step> then = body(step, "then", depth, maxDepth, total, true);
			List<Step> orElse = step.has("else") ? body(step, "else", depth, maxDepth, total, true) : List.of();
			return new Step.If(parseCondition(step.get("if")), then, orElse);
		}
		throw new ScriptException("A step must have \"tool\", \"repeat\", \"until\" or \"if\".");
	}

	private static Step parseCall(JsonObject step) {
		JsonElement tool = step.get("tool");
		if (!tool.isJsonPrimitive() || !((JsonPrimitive) tool).isString()) {
			throw new ScriptException("\"tool\" must be a tool name.");
		}
		String name = tool.getAsString();
		if (FORBIDDEN_TOOLS.contains(name)) {
			throw new ScriptException("A script cannot call " + name + ".");
		}
		if (Tools.all().stream().map(ToolDef::name).noneMatch(name::equals)) {
			throw new ScriptException("Unknown tool \"" + name + "\" in the script.");
		}
		JsonElement args = step.get("args");
		if (args != null && !args.isJsonObject()) {
			throw new ScriptException("\"args\" must be an object.");
		}
		return new Step.Call(name, args == null ? new JsonObject() : args.getAsJsonObject());
	}

	private static List<Step> body(JsonObject step, String key, int depth, int maxDepth, int[] total, boolean required) {
		JsonElement body = step.get(key);
		if (body == null || !body.isJsonArray()) {
			throw new ScriptException("This step needs a \"" + key + "\" list.");
		}
		if (depth + 1 > maxDepth) {
			throw new ScriptException("The script is nested too deep (limit " + maxDepth + " levels).");
		}
		return parseSteps(body.getAsJsonArray(), depth + 1, maxDepth, total);
	}

	private static void allow(JsonObject step, String first, String... allowed) {
		Set<String> ok = Set.of(allowed);
		for (String key : step.keySet()) {
			if (!ok.contains(key)) {
				throw new ScriptException("Unknown key \"" + key + "\" in a \"" + first + "\" step.");
			}
		}
	}

	private static Condition parseCondition(JsonElement element) {
		if (element == null || !element.isJsonObject() || element.getAsJsonObject().size() != 1) {
			throw new ScriptException("A condition must have exactly one of " + new java.util.TreeSet<>(CONDITION_KEYS) + ".");
		}
		JsonObject condition = element.getAsJsonObject();
		String key = condition.keySet().iterator().next();
		JsonElement value = condition.get(key);
		switch (key) {
			case "night" -> {
				return new Condition.Night();
			}
			case "health_below" -> {
				if (!value.isJsonPrimitive() || !((JsonPrimitive) value).isNumber()) {
					throw new ScriptException("\"health_below\" must be a number.");
				}
				return new Condition.HealthBelow(value.getAsDouble());
			}
			case "has_block" -> {
				return new Condition.HasBlock(whole(value, "x"), whole(value, "y"), whole(value, "z"));
			}
			case "have" -> {
				if (!value.isJsonObject() || !value.getAsJsonObject().has("item")
						|| !value.getAsJsonObject().get("item").isJsonPrimitive()
						|| !ITEM_ID.matcher(value.getAsJsonObject().get("item").getAsString()).matches()) {
					throw new ScriptException("\"have\" needs an \"item\" id like minecraft:oak_log.");
				}
				int count = value.getAsJsonObject().has("count") ? whole(value, "count") : 1;
				return new Condition.Have(value.getAsJsonObject().get("item").getAsString(), count);
			}
			default -> throw new ScriptException("Unknown condition \"" + key + "\"; use one of " + new java.util.TreeSet<>(CONDITION_KEYS) + ".");
		}
	}

	private static int whole(JsonElement object, String key) {
		JsonElement v = object.isJsonObject() ? object.getAsJsonObject().get(key) : null;
		if (v == null || !v.isJsonPrimitive() || !((JsonPrimitive) v).isNumber() || v.getAsDouble() != Math.rint(v.getAsDouble())) {
			throw new ScriptException("\"" + key + "\" must be a whole number.");
		}
		return v.getAsInt();
	}
}
