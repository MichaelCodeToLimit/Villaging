package com.villaging.blueprint;

import java.util.Map;
import java.util.TreeMap;

/** A text picture of one layer of a blueprint, so the AI can check its own design. Pure. */
public final class AsciiView {
	private AsciiView() {
	}

	/** One line per z (north to south), one character per x (west to east); {@code .} is empty. */
	public static String layer(Blueprint b, int y) {
		if (y < 0 || y >= b.sizeY()) {
			return "There is no layer " + y + "; the layers are 0 to " + (b.sizeY() - 1) + ".";
		}
		char[][] rows = new char[b.sizeZ()][b.sizeX()];
		for (char[] row : rows) {
			java.util.Arrays.fill(row, '.');
		}
		Map<Character, String> legend = new TreeMap<>();
		for (Cell c : b.cells()) {
			if (c.y() == y) {
				rows[c.z()][c.x()] = c.key();
				legend.put(c.key(), b.stateAt(c));
			}
		}
		StringBuilder sb = new StringBuilder("Layer " + y + " of " + (b.sizeY() - 1) + " (x across, z down):\n");
		for (char[] row : rows) {
			sb.append(new String(row)).append('\n');
		}
		for (Map.Entry<Character, String> e : legend.entrySet()) {
			sb.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
		}
		return sb.toString();
	}
}
