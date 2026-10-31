package com.villaging.blueprint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** The order cells are built in: bottom layer first, full blocks before things that hang on them, growing outward from the near corner. Pure. */
public final class BuildOrder {
	private static final List<String> ATTACHED = List.of("torch", "lantern", "_door", "ladder", "button", "lever", "sign", "banner",
			"carpet", "pressure_plate", "flower_pot", "redstone", "trapdoor", "rail", "pane", "bed");

	private BuildOrder() {
	}

	/** Every cell a helper must place (the second half of a door or bed comes with the first and is left out). */
	public static List<Cell> order(Blueprint b) {
		List<Cell> placeable = new ArrayList<>();
		for (Cell c : b.cells()) {
			if (BlockRules.cost(BlockRules.parse(b.stateAt(c))).count() > 0) {
				placeable.add(c);
			}
		}
		placeable.sort(Comparator.<Cell>comparingInt(Cell::y)
				.thenComparingInt(c -> attached(b.stateAt(c)) ? 1 : 0)
				.thenComparingInt(c -> c.x() + c.z())
				.thenComparingInt(Cell::x)
				.thenComparingInt(Cell::z));
		return placeable;
	}

	private static boolean attached(String state) {
		for (String word : ATTACHED) {
			if (state.contains(word)) {
				return true;
			}
		}
		return false;
	}
}
