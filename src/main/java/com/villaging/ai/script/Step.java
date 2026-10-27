package com.villaging.ai.script;

import com.google.gson.JsonObject;
import java.util.List;

/** One step of a task script: a tool call, or a loop or branch around more steps. */
public sealed interface Step {
	record Call(String tool, JsonObject args) implements Step {
	}

	record Repeat(int times, List<Step> body) implements Step {
	}

	/** Repeats the body until the condition is true (checked before each round). */
	record Until(Condition condition, List<Step> body) implements Step {
	}

	record If(Condition condition, List<Step> then, List<Step> orElse) implements Step {
	}
}
