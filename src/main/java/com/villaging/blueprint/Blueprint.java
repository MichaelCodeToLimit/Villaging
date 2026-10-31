package com.villaging.blueprint;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A saved design: a bounding box, a palette of block states and the non-air cells. Pure data; every blueprint that
 * exists has passed {@link #create}, so its blocks are allowed and its cells are inside the box.
 */
public record Blueprint(String name, int sizeX, int sizeY, int sizeZ, Map<Character, String> palette, List<Cell> cells,
		String origin) {
	public static final int MAX_SIDE = 48;
	public static final int MAX_HEIGHT = 64;
	public static final int MAX_CELLS = 20_000;

	private static final Pattern NAME = Pattern.compile("[a-z0-9_]{1,32}");

	public Blueprint {
		palette = Map.copyOf(palette);
		cells = List.copyOf(cells);
	}

	/** True when {@code name} is a legal blueprint name (also guards file names). */
	public static boolean isValidName(String name) {
		return name != null && NAME.matcher(name).matches();
	}

	/** Checks everything and builds the blueprint, or says precisely what is wrong. */
	public static Blueprint create(String name, int sx, int sy, int sz, Map<Character, String> palette, List<Cell> cells,
			String origin, int maxCells) throws BlueprintException {
		if (!isValidName(name)) {
			throw new BlueprintException("The blueprint name \"" + name + "\" is not allowed; use 1 to 32 lowercase letters, digits or underscores.");
		}
		if (sx < 1 || sy < 1 || sz < 1) {
			throw new BlueprintException("The size must be at least 1 in every direction (size " + sx + " x " + sy + " x " + sz + ").");
		}
		if (sx > MAX_SIDE || sz > MAX_SIDE) {
			throw new BlueprintException("A blueprint may be at most " + MAX_SIDE + " blocks wide and deep (size " + sx + " x " + sz + ").");
		}
		if (sy > MAX_HEIGHT) {
			throw new BlueprintException("A blueprint may be at most " + MAX_HEIGHT + " blocks high (height " + sy + ").");
		}
		for (Map.Entry<Character, String> e : palette.entrySet()) {
			BlockRules.State state;
			try {
				state = BlockRules.parse(e.getValue());
			} catch (IllegalArgumentException bad) {
				throw new BlueprintException("palette entry " + e.getKey() + ": " + bad.getMessage());
			}
			String denied = BlockRules.denyReason(state.id());
			if (denied != null) {
				throw new BlueprintException("palette entry " + e.getKey() + " uses a block that is not allowed (" + denied + ").");
			}
		}
		if (cells.size() > maxCells) {
			throw new BlueprintException("That is " + cells.size() + " blocks; the most allowed is " + maxCells + " blocks.");
		}
		Set<Long> seen = new HashSet<>();
		for (Cell c : cells) {
			if (c.x() < 0 || c.x() >= sx || c.y() < 0 || c.y() >= sy || c.z() < 0 || c.z() >= sz) {
				throw new BlueprintException("A block at " + c.x() + "," + c.y() + "," + c.z() + " is outside the " + sx + " x " + sy
						+ " x " + sz + " box.");
			}
			if (!palette.containsKey(c.key())) {
				throw new BlueprintException("A block uses \"" + c.key() + "\", which is not in the palette.");
			}
			long id = ((long) c.y() * 64 + c.x()) * 64 + c.z();
			if (!seen.add(id)) {
				throw new BlueprintException("There are two blocks at " + c.x() + "," + c.y() + "," + c.z() + " (a position is used twice).");
			}
		}
		return new Blueprint(name, sx, sy, sz, palette, cells, origin);
	}

	/** The block state text of a cell. */
	public String stateAt(Cell c) {
		return palette.get(c.key());
	}

	/** Turned clockwise (looking down) {@code quarterTurns} times; sizes swap on odd turns and cells stay inside the box. */
	public Blueprint rotated(int quarterTurns) {
		int turns = Math.floorMod(quarterTurns, 4);
		Blueprint current = this;
		for (int i = 0; i < turns; i++) {
			List<Cell> turned = new ArrayList<>(current.cells.size());
			for (Cell c : current.cells) {
				turned.add(new Cell(current.sizeZ - 1 - c.z(), c.y(), c.x(), c.key()));
			}
			current = new Blueprint(current.name, current.sizeZ, current.sizeY, current.sizeX, current.palette, turned, current.origin);
		}
		return current;
	}
}
