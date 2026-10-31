package com.villaging.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.villaging.blueprint.Blueprint;
import com.villaging.blueprint.BlueprintException;
import com.villaging.blueprint.BuildOrder;
import com.villaging.blueprint.Cell;
import com.villaging.blueprint.Templates;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TeamSplitterTest {
	private static Blueprint cottage() throws BlueprintException {
		return Templates.build("cottage", "c", Templates.defaults("cottage"), 20_000);
	}

	private static Blueprint flatFloor(int w, int d) throws BlueprintException {
		List<Cell> cells = new ArrayList<>();
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				cells.add(new Cell(x, 0, z, 's'));
			}
		}
		return Blueprint.create("floor", w, 1, d, Map.of('s', "minecraft:stone"), cells, "test", 20_000);
	}

	@Test
	void unionEqualsWholeAndNoOverlap() throws BlueprintException {
		Blueprint whole = cottage();
		List<Blueprint> parts = TeamSplitter.split(whole, 3, "c_");
		Set<Cell> seen = new HashSet<>();
		for (Blueprint p : parts) {
			for (Cell c : p.cells()) {
				assertTrue(seen.add(c), "cell in two slices: " + c);
			}
		}
		assertEquals(new HashSet<>(whole.cells()), seen);
	}

	@Test
	void slicesAreVerticalSlabsSoNoSliceNeedsAnotherToStandOn() throws BlueprintException {
		Blueprint whole = cottage();
		List<Blueprint> parts = TeamSplitter.split(whole, 3, "c_");
		Set<Integer> usedByEarlier = new HashSet<>();
		for (Blueprint p : parts) {
			Set<Integer> mine = new HashSet<>();
			for (Cell c : p.cells()) {
				mine.add(whole.sizeX() >= whole.sizeZ() ? c.x() : c.z());
			}
			for (int line : mine) {
				assertFalse(usedByEarlier.contains(line), "column " + line + " is in two slices");
			}
			usedByEarlier.addAll(mine);
		}
	}

	@Test
	void singlePartReturnsAllCells() throws BlueprintException {
		Blueprint whole = cottage();
		List<Blueprint> parts = TeamSplitter.split(whole, 1, "c_");
		assertEquals(1, parts.size());
		assertEquals(whole.cells().size(), parts.get(0).cells().size());
	}

	@Test
	void fewerLayersThanPartsStillSplitsAndNeverMakesAnEmptySlice() throws BlueprintException {
		List<Blueprint> parts = TeamSplitter.split(flatFloor(8, 8), 4, "f_");
		assertEquals(4, parts.size());
		for (Blueprint p : parts) {
			assertFalse(p.cells().isEmpty());
		}
	}

	@Test
	void moreMembersThanBlocksUsesFewerSlices() throws BlueprintException {
		List<Blueprint> parts = TeamSplitter.split(flatFloor(2, 1), 5, "f_");
		assertEquals(2, parts.size());
	}

	@Test
	void slicesAreBalancedToWithinOneLayer() throws BlueprintException {
		Blueprint whole = cottage();
		int layer = 0;
		for (int y = 0; y < whole.sizeY(); y++) {
			final int yy = y;
			layer = Math.max(layer, (int) whole.cells().stream().filter(c -> c.y() == yy).count());
		}
		List<Blueprint> parts = TeamSplitter.split(whole, 3, "c_");
		int min = Integer.MAX_VALUE;
		int max = 0;
		for (Blueprint p : parts) {
			int n = BuildOrder.order(p).size();
			min = Math.min(min, n);
			max = Math.max(max, n);
		}
		assertTrue(max - min <= layer, "max " + max + " min " + min + " layer " + layer);
	}

	@Test
	void namesAreValidAndSizeIsKept() throws BlueprintException {
		Blueprint whole = cottage();
		List<Blueprint> parts = TeamSplitter.split(whole, 2, "crew_");
		assertEquals("crew_1", parts.get(0).name());
		for (Blueprint p : parts) {
			assertTrue(Blueprint.isValidName(p.name()));
			assertEquals(whole.sizeX(), p.sizeX());
			assertEquals(whole.sizeY(), p.sizeY());
			assertEquals(whole.sizeZ(), p.sizeZ());
		}
	}

	@Test
	void partsBelowOneBecomesOne() throws BlueprintException {
		assertEquals(1, TeamSplitter.split(cottage(), 0, "c_").size());
	}
}
