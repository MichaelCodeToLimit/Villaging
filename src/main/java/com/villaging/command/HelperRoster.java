package com.villaging.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure rules for finding a smart helper by name (/villaging summon) and for spreading an announcement over time. */
public final class HelperRoster {
	/** Most helpers one announcement reaches. */
	public static final int MAX_ANNOUNCED = 12;
	/** Ticks between two helpers being asked (1.5 seconds), so the AI service is not flooded. */
	public static final int ANNOUNCE_GAP_TICKS = 30;

	public record Helper(int id, String name) {
	}

	public sealed interface Match permits Found, Ambiguous, NotFound {
	}

	public record Found(Helper helper) implements Match {
	}

	public record Ambiguous(List<String> names) implements Match {
	}

	public record NotFound() implements Match {
	}

	private HelperRoster() {
	}

	/**
	 * The helper named {@code query}, ignoring capitals and surrounding spaces. A full name wins; otherwise a start of
	 * a name is enough if it picks out exactly one helper. Never guesses between several.
	 */
	public static Match find(List<Helper> helpers, String query) {
		String wanted = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
		if (wanted.isEmpty()) {
			return new NotFound();
		}
		List<Helper> exact = new ArrayList<>();
		List<Helper> prefix = new ArrayList<>();
		for (Helper h : helpers) {
			String name = h.name().toLowerCase(Locale.ROOT);
			if (name.equals(wanted)) {
				exact.add(h);
			} else if (name.startsWith(wanted)) {
				prefix.add(h);
			}
		}
		List<Helper> candidates = exact.isEmpty() ? prefix : exact;
		if (candidates.isEmpty()) {
			return new NotFound();
		}
		if (candidates.size() == 1) {
			return new Found(candidates.get(0));
		}
		return new Ambiguous(candidates.stream().map(Helper::name).toList());
	}

	/** The first {@code max} helpers, in the order given (nearest first). */
	public static List<Helper> limit(List<Helper> helpers, int max) {
		return helpers.size() <= max ? helpers : List.copyOf(helpers.subList(0, max));
	}

	/** When each of {@code count} helpers is asked: 0, gap, 2*gap, ... in ticks. */
	public static List<Integer> staggerTicks(int count, int gapTicks) {
		List<Integer> ticks = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ticks.add(i * gapTicks);
		}
		return ticks;
	}
}
