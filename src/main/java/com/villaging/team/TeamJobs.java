package com.villaging.team;

import java.util.List;
import java.util.stream.IntStream;

/** Pure rules for handing a team its work. */
public final class TeamJobs {
	private TeamJobs() {
	}

	/** Which of {@code slices} slices (by index, in order) may start now when {@code free} build places are free; the rest wait. */
	public static List<Integer> startOrder(int slices, int free) {
		int n = Math.min(slices, Math.max(0, free));
		return IntStream.range(0, n).boxed().toList();
	}

	/** Name start for the saved slices of a team's build; with a one or two digit number it is a valid blueprint name. */
	public static String partPrefix(String teamName) {
		return "t" + (teamName.length() > 27 ? teamName.substring(0, 27) : teamName) + "_";
	}

	/** The words a member hears when its leader passes on an order. */
	public static String order(String leaderName, String task) {
		return "(Your team leader " + leaderName + " passes on this order from the king) " + task.trim();
	}
}
