package com.villaging.team;

import com.villaging.blueprint.Blueprint;
import com.villaging.blueprint.BlueprintException;
import com.villaging.blueprint.BuildOrder;
import com.villaging.blueprint.Cell;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Cuts a blueprint into slices, one per team member. Slices follow whole layers where they can, so members build
 * floor-by-floor side by side; a layer bigger than a fair share is cut by position. Pure.
 */
public final class TeamSplitter {
	private TeamSplitter() {
	}

	/** One or more slices ({@code parts} at most), each the same size as {@code whole}, named {@code namePrefix + 1, 2, ...}. */
	public static List<Blueprint> split(Blueprint whole, int parts, String namePrefix) throws BlueprintException {
		int wanted = Math.max(1, parts);
		Set<Cell> placeable = new HashSet<>(BuildOrder.order(whole));
		int total = placeable.size();
		int share = Math.max(1, (total + wanted - 1) / wanted);

		// Units: each layer, or pieces of a layer when it alone is more than a fair share.
		List<List<Cell>> units = new ArrayList<>();
		TreeMap<Integer, List<Cell>> layers = new TreeMap<>();
		for (Cell c : whole.cells()) {
			layers.computeIfAbsent(c.y(), y -> new ArrayList<>()).add(c);
		}
		for (List<Cell> layer : layers.values()) {
			layer.sort(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z));
			int weight = (int) layer.stream().filter(placeable::contains).count();
			if (weight <= share) {
				units.add(layer);
				continue;
			}
			List<Cell> piece = new ArrayList<>();
			int inPiece = 0;
			for (Cell c : layer) {
				piece.add(c);
				if (placeable.contains(c) && ++inPiece == share) {
					units.add(piece);
					piece = new ArrayList<>();
					inPiece = 0;
				}
			}
			if (!piece.isEmpty()) {
				units.add(piece);
			}
		}

		// Pack consecutive units into slices; the last slice takes whatever is left.
		List<List<Cell>> slices = new ArrayList<>();
		List<Cell> current = new ArrayList<>();
		int weight = 0;
		int remaining = total;
		for (List<Cell> unit : units) {
			int w = (int) unit.stream().filter(placeable::contains).count();
			int slicesLeft = wanted - slices.size();
			int fair = (remaining + slicesLeft - 1) / slicesLeft;
			boolean overshootsMore = weight + w - fair > fair - weight;
			if (!current.isEmpty() && slicesLeft > 1 && weight > 0 && weight + w > fair && overshootsMore) {
				slices.add(current);
				remaining -= weight;
				current = new ArrayList<>();
				weight = 0;
			}
			current.addAll(unit);
			weight += w;
		}
		if (!current.isEmpty()) {
			slices.add(current);
		}

		List<Blueprint> result = new ArrayList<>();
		for (int i = 0; i < slices.size(); i++) {
			result.add(Blueprint.create(namePrefix + (i + 1), whole.sizeX(), whole.sizeY(), whole.sizeZ(), whole.palette(),
					slices.get(i), whole.origin(), Blueprint.MAX_CELLS));
		}
		return result;
	}
}
