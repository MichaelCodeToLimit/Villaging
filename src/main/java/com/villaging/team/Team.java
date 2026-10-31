package com.villaging.team;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** A named group of helpers with one leader (always the first member). Pure rules, no game classes. */
public record Team(String name, UUID leader, List<UUID> members) {
	private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,32}");

	public Team {
		members = List.copyOf(members);
	}

	public static boolean isValidName(String name) {
		return name != null && NAME.matcher(name).matches();
	}

	public static Team create(String name, UUID leader, List<UUID> others, int maxSize) throws TeamException {
		if (!isValidName(name)) {
			throw new TeamException("A team name is 1 to 32 lowercase letters, digits or underscores, like builders.");
		}
		List<UUID> all = new ArrayList<>();
		all.add(leader);
		for (UUID other : others) {
			if (!all.contains(other)) {
				all.add(other);
			}
		}
		if (all.size() > maxSize) {
			throw new TeamException("A team holds at most " + maxSize + " helpers, leader included.");
		}
		return new Team(name, leader, all);
	}

	public Team with(UUID helper, int maxSize) throws TeamException {
		if (has(helper)) {
			throw new TeamException("That helper is already in team " + name + ".");
		}
		if (members.size() >= maxSize) {
			throw new TeamException("Team " + name + " is full: at most " + maxSize + " helpers.");
		}
		List<UUID> next = new ArrayList<>(members);
		next.add(helper);
		return new Team(name, leader, next);
	}

	public Team without(UUID helper) throws TeamException {
		if (helper.equals(leader)) {
			throw new TeamException("That helper leads team " + name + ". Disband the team, or make a new team with another leader.");
		}
		if (!has(helper)) {
			throw new TeamException("That helper is not in team " + name + ".");
		}
		List<UUID> next = new ArrayList<>(members);
		next.remove(helper);
		return new Team(name, leader, next);
	}

	public boolean has(UUID helper) {
		return members.contains(helper);
	}

	/** A team needs a leader and at least one more helper to be given jobs. */
	public boolean canWork() {
		return members.size() >= 2;
	}
}
