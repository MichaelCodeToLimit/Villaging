package com.villaging.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildOrderTest {
	private static Blueprint blueprint(List<Cell> cells) throws Exception {
		return Blueprint.create("t", 4, 3, 4, Map.of('S', "minecraft:stone", 'T', "minecraft:wall_torch[facing=east]",
				'D', "minecraft:oak_door[half=lower,facing=north]", 'U', "minecraft:oak_door[half=upper,facing=north]"), cells, "ai", 1000);
	}

	@Test
	void orderIsBottomUpAndIncludesEveryPlaceableCellOnce() throws Exception {
		List<Cell> cells = new ArrayList<>();
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 4; x++) {
				cells.add(new Cell(x, y, 0, 'S'));
			}
		}
		cells.add(new Cell(1, 1, 3, 'D'));
		cells.add(new Cell(1, 2, 3, 'U'));
		Blueprint b = blueprint(cells);
		List<Cell> order = BuildOrder.order(b);
		assertEquals(13, order.size());                                  // the upper door half is not placed on its own
		assertEquals(order.size(), new HashSet<>(order).size());
		for (int i = 1; i < order.size(); i++) {
			assertTrue(order.get(i - 1).y() <= order.get(i).y(), "y never decreases");
		}
		assertTrue(order.stream().noneMatch(c -> c.key() == 'U'));
	}

	@Test
	void attachmentsComeAfterFullBlocksInTheSameLayer() throws Exception {
		Blueprint b = blueprint(List.of(new Cell(0, 0, 0, 'T'), new Cell(3, 0, 3, 'S'), new Cell(1, 0, 0, 'D')));
		List<Cell> order = BuildOrder.order(b);
		assertEquals('S', order.get(0).key());
		assertEquals(Set.of('T', 'D'), Set.of(order.get(1).key(), order.get(2).key()));
	}

	@Test
	void layersGrowOutwardFromTheNearCorner() throws Exception {
		Blueprint b = blueprint(List.of(new Cell(3, 0, 3, 'S'), new Cell(0, 0, 0, 'S'), new Cell(1, 0, 0, 'S'), new Cell(0, 0, 2, 'S')));
		List<Cell> order = BuildOrder.order(b);
		assertEquals(new Cell(0, 0, 0, 'S'), order.get(0));
		assertEquals(new Cell(3, 0, 3, 'S'), order.get(3));
	}

	@Test
	void orderIsDeterministic() throws Exception {
		Blueprint b = blueprint(List.of(new Cell(2, 0, 1, 'S'), new Cell(1, 0, 2, 'S'), new Cell(0, 1, 0, 'S')));
		assertEquals(BuildOrder.order(b), BuildOrder.order(b));
	}
}
