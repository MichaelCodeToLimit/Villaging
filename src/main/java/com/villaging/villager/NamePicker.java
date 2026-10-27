package com.villaging.villager;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/** Picks a first name for a new smart villager that no nearby smart villager already uses. */
public final class NamePicker {
	public static final List<String> NAMES = List.of("Bob", "Alma", "Odo", "Mira", "Tobin", "Elsa", "Garrick", "Lysa");

	private NamePicker() {
	}

	/** {@code random} maps a bound to a number in [0, bound), like {@code RandomSource::nextInt}. */
	public static String pick(List<String> pool, Set<String> taken, IntUnaryOperator random) {
		Set<String> takenLower = new java.util.HashSet<>();
		taken.forEach(n -> takenLower.add(n.toLowerCase(Locale.ROOT)));
		List<String> free = pool.stream().filter(n -> !takenLower.contains(n.toLowerCase(Locale.ROOT))).toList();
		if (!free.isEmpty()) {
			return free.get(random.applyAsInt(free.size()));
		}
		for (int n = 1;; n++) {
			String candidate = "Villager " + n;
			if (!takenLower.contains(candidate.toLowerCase(Locale.ROOT))) {
				return candidate;
			}
		}
	}
}
