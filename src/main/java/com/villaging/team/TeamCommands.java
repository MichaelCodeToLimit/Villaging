package com.villaging.team;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pure reading of the names typed after /villaging team create. */
public final class TeamCommands {
	/** The first name is the leader; the rest are the other members. */
	public record Parsed(String leader, List<String> members) {
	}

	private TeamCommands() {
	}

	/** Names separated by commas or the word "and"; empty text gives nothing. */
	public static Optional<Parsed> parse(String text) {
		if (text == null) {
			return Optional.empty();
		}
		List<String> names = new ArrayList<>();
		for (String part : text.split(",|(?i)\\band\\b")) {
			String name = part.trim();
			if (!name.isEmpty()) {
				names.add(name);
			}
		}
		if (names.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new Parsed(names.get(0), List.copyOf(names.subList(1, names.size()))));
	}
}
