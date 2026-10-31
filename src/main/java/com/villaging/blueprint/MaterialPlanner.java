package com.villaging.blueprint;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Counts what a blueprint needs and what is missing from the helper's items and the stockpile. Pure. */
public final class MaterialPlanner {
	/** One item: how many the build needs, how many are available (carried plus stockpile, at most the need) and how many are missing. */
	public record Row(String item, int needed, int have, int missing) {
	}

	public record Report(List<Row> rows, int blocks) {
		public boolean complete() {
			return missingRows().isEmpty();
		}

		public List<Row> missingRows() {
			return rows.stream().filter(r -> r.missing() > 0).toList();
		}

		/** For example {@code 62 stone, 12 oak planks}; empty when nothing is missing. */
		public String describeMissing() {
			List<String> parts = new ArrayList<>();
			for (Row r : missingRows()) {
				parts.add(r.missing() + " " + readable(r.item()));
			}
			return String.join(", ", parts);
		}
	}

	private MaterialPlanner() {
	}

	public static Report plan(Blueprint b, Map<String, Integer> carried, Map<String, Integer> stockpile) {
		Map<String, Integer> needed = new TreeMap<>();
		int blocks = 0;
		for (Cell c : b.cells()) {
			BlockRules.ItemCost cost = BlockRules.cost(BlockRules.parse(b.stateAt(c)));
			if (cost.count() > 0) {
				needed.merge(cost.item(), cost.count(), Integer::sum);
				blocks++;
			}
		}
		List<Row> rows = new ArrayList<>();
		for (Map.Entry<String, Integer> e : needed.entrySet()) {
			int available = carried.getOrDefault(e.getKey(), 0) + stockpile.getOrDefault(e.getKey(), 0);
			int have = Math.min(e.getValue(), available);
			rows.add(new Row(e.getKey(), e.getValue(), have, e.getValue() - have));
		}
		return new Report(rows, blocks);
	}

	/** {@code minecraft:oak_planks} as {@code oak planks}. */
	public static String readable(String item) {
		String name = item.startsWith("minecraft:") ? item.substring("minecraft:".length()) : item;
		return name.replace('_', ' ');
	}
}
