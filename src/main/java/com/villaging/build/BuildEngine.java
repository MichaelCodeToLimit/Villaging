package com.villaging.build;

import com.villaging.blueprint.BlockRules;
import com.villaging.blueprint.Blueprint;
import com.villaging.blueprint.Cell;
import com.villaging.blueprint.MaterialPlanner;
import java.util.ArrayList;
import java.util.List;

/**
 * The decisions of a build, with no Minecraft in it: given the next cell, the world and what the helper has, what should
 * happen now (place it, skip it, fetch materials, pause because something is missing, or finish)? The game layer asks
 * {@link #next}, does what it says, and reports back with {@link #placed} or {@link #blocked}.
 *
 * <p>A cell that cannot be built against yet is put off to a second pass at the end; a cell that is occupied, protected or
 * still unsupported on the second pass is skipped and reported, never forced.
 */
public final class BuildEngine {
	/** Largest batch taken from the stockpile at once. */
	public static final int FETCH_BATCH = 64;

	public interface World {
		/** True when the world already holds this exact state at the position. */
		boolean matches(int x, int y, int z, String stateText);

		/** True when nothing but air (or something a placed block may replace) is there. */
		boolean isFree(int x, int y, int z);

		/** True when a block can be placed there against something that is already built. */
		boolean supported(int x, int y, int z);

		/** False for spots that protection (spawn protection, claims) forbids. */
		boolean mayPlace(int x, int y, int z);
	}

	public interface Items {
		int carried(String item);

		int stockpile(String item);
	}

	public enum Step { PLACE, SKIP_DONE, SKIP_BLOCKED, FETCH, PAUSE_MISSING, FINISHED }

	/** {@code cellIndex} is the position in the ordered cell list (-1 when there is none); item and count apply to PLACE, FETCH and PAUSE_MISSING. */
	public record Next(Step step, int cellIndex, String item, int count, String note) {
	}

	private final List<Cell> order;
	private final Blueprint blueprint;
	private final int ax;
	private final int ay;
	private final int az;
	private List<Integer> queue = new ArrayList<>();
	private final List<Integer> retry = new ArrayList<>();
	private final List<String> blockedNotes = new ArrayList<>();
	private int pointer;
	private boolean secondPass;
	private int done;

	/** @param orderedCells the cells to place, in build order (see BuildOrder); positions are relative to the anchor */
	public BuildEngine(List<Cell> orderedCells, Blueprint blueprint, int anchorX, int anchorY, int anchorZ) {
		this.order = List.copyOf(orderedCells);
		this.blueprint = blueprint;
		this.ax = anchorX;
		this.ay = anchorY;
		this.az = anchorZ;
		for (int i = 0; i < order.size(); i++) {
			queue.add(i);
		}
	}

	public Cell cell(int index) {
		return order.get(index);
	}

	/** Cells in the build, whether placed or not. */
	public int total() {
		return order.size();
	}

	/** Cells that are now in place (placed by this helper or found already built). */
	public int done() {
		return done;
	}

	public int blockedCount() {
		return blockedNotes.size();
	}

	public List<String> blockedNotes() {
		return List.copyOf(blockedNotes);
	}

	/** Carries on at {@code cellsDone} in the list, counting everything before it as finished. */
	public void startAt(int cellsDone) {
		pointer = Math.max(0, Math.min(cellsDone, queue.size()));
		done = pointer;
	}

	/** The cell last returned as PLACE is now in the world. */
	public void placed() {
		done++;
		pointer++;
	}

	/**
	 * Placing the cell last returned as PLACE did not work. The first time it is put off to the end (often the helper itself was
	 * standing in the way and will be elsewhere by then); a second failure skips and reports it.
	 */
	public void failed(String why) {
		if (!secondPass) {
			retry.add(queue.get(pointer));
			pointer++;
		} else {
			blocked(why);
		}
	}

	/** The cell last returned as PLACE cannot be built; it is skipped and reported at once. */
	public void blocked(String why) {
		blockedNotes.add(why);
		pointer++;
	}

	public Next next(World world, Items items) {
		while (true) {
			if (pointer >= queue.size()) {
				if (!secondPass && !retry.isEmpty()) {
					queue = new ArrayList<>(retry);
					retry.clear();
					pointer = 0;
					secondPass = true;
					continue;
				}
				return new Next(Step.FINISHED, -1, "", 0, summary());
			}
			int index = queue.get(pointer);
			Cell c = order.get(index);
			int x = ax + c.x();
			int y = ay + c.y();
			int z = az + c.z();
			String state = blueprint.stateAt(c);
			String where = x + "," + y + "," + z;
			if (world.matches(x, y, z, state)) {
				pointer++;
				done++;
				return new Next(Step.SKIP_DONE, index, "", 0, "");
			}
			if (!world.mayPlace(x, y, z)) {
				return skip(index, where + " is protected");
			}
			if (!world.isFree(x, y, z)) {
				return skip(index, where + " is occupied");
			}
			if (!world.supported(x, y, z)) {
				if (!secondPass) {
					retry.add(index);
					pointer++;
					continue;
				}
				return skip(index, where + " has nothing to build against");
			}
			BlockRules.ItemCost cost = BlockRules.cost(BlockRules.parse(state));
			int have = items.carried(cost.item());
			if (have >= cost.count()) {
				return new Next(Step.PLACE, index, cost.item(), cost.count(), "");
			}
			int stillNeeded = remainingNeed(world, cost.item());
			int inChest = items.stockpile(cost.item());
			int fetch = Math.min(FETCH_BATCH, Math.min(inChest, Math.max(cost.count() - have, stillNeeded - have)));
			if (fetch > 0) {
				return new Next(Step.FETCH, index, cost.item(), fetch, "");
			}
			int missing = Math.max(1, stillNeeded - have - inChest);
			return new Next(Step.PAUSE_MISSING, index, cost.item(), missing,
					"I need " + missing + " more " + MaterialPlanner.readable(cost.item()));
		}
	}

	private Next skip(int index, String note) {
		blockedNotes.add(note);
		pointer++;
		return new Next(Step.SKIP_BLOCKED, index, "", 0, note);
	}

	/** How many more of {@code item} the cells not yet built still need. */
	private int remainingNeed(World world, String item) {
		int need = 0;
		List<Integer> remaining = new ArrayList<>(queue.subList(pointer, queue.size()));
		if (!secondPass) {
			remaining.addAll(retry);
		}
		for (int index : remaining) {
			Cell c = order.get(index);
			String state = blueprint.stateAt(c);
			if (world.matches(ax + c.x(), ay + c.y(), az + c.z(), state)) {
				continue;
			}
			BlockRules.ItemCost cost = BlockRules.cost(BlockRules.parse(state));
			if (cost.item().equals(item)) {
				need += cost.count();
			}
		}
		return need;
	}

	private String summary() {
		String text = "Done: " + done + " of " + order.size() + " blocks are in place";
		return blockedNotes.isEmpty() ? text + "." : text + "; " + blockedNotes.size() + " were skipped (" + String.join("; ", firstFew()) + ").";
	}

	private List<String> firstFew() {
		return blockedNotes.size() <= 3 ? blockedNotes : blockedNotes.subList(0, 3);
	}
}
