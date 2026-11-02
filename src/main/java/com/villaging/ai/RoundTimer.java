package com.villaging.ai;

import java.util.List;
import java.util.Locale;

/** The one-line timing note for a model call. It holds sizes and times only, never message text or keys. */
public final class RoundTimer {
	private static final long SLOW_MILLIS = 1_500;

	private RoundTimer() {
	}

	public static String line(int requestChars, long millis, int toolCalls) {
		return String.format(Locale.ROOT, "AI round: %.1f s, %,d chars, %d tool call%s", millis / 1000.0, requestChars,
				toolCalls, toolCalls == 1 ? "" : "s");
	}

	/** Slow rounds are always worth a line; every round is logged when {@code -Dvillaging.debug=true}. */
	public static boolean shouldLog(long millis) {
		return millis >= SLOW_MILLIS || Boolean.getBoolean("villaging.debug");
	}

	/** Characters the model is sent for one round: instructions, conversation and tool definitions. */
	public static int requestChars(String system, List<ChatMessage> messages, List<ToolDef> tools) {
		int total = system.length();
		for (ChatMessage m : messages) {
			total += m.text() == null ? 0 : m.text().length();
		}
		for (ToolDef t : tools) {
			total += t.name().length() + t.description().length() + t.parametersSchema().toString().length();
		}
		return total;
	}
}
