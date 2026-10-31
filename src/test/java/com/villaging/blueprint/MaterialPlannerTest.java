package com.villaging.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.villaging.blueprint.MaterialPlanner.Report;
import com.villaging.blueprint.MaterialPlanner.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MaterialPlannerTest {
	private static Blueprint stone(int count) throws Exception {
		List<Cell> cells = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			cells.add(new Cell(i % 20, i / 400, (i / 20) % 20, 'S'));
		}
		return Blueprint.create("t", 20, 2, 20, Map.of('S', "minecraft:stone"), cells, "ai", 5000);
	}

	@Test
	void havePlusStockpileCoverNeedAndTheRestIsMissing() throws Exception {
		Report r = MaterialPlanner.plan(stone(312), Map.of("minecraft:stone", 100), Map.of("minecraft:stone", 150));
		assertEquals(new Row("minecraft:stone", 312, 250, 62), r.rows().get(0));
		assertFalse(r.complete());
		assertEquals(312, r.blocks());
		assertEquals("62 stone", r.describeMissing());
	}

	@Test
	void everythingAvailableMeansCompleteAndNothingToDescribe() throws Exception {
		Report r = MaterialPlanner.plan(stone(10), Map.of("minecraft:stone", 4), Map.of("minecraft:stone", 6));
		assertTrue(r.complete());
		assertTrue(r.missingRows().isEmpty());
		assertEquals("", r.describeMissing());
	}

	@Test
	void severalMissingItemsAreListedInPlainWords() throws Exception {
		Blueprint b = Blueprint.create("t", 2, 1, 2, Map.of('S', "minecraft:stone", 'P', "minecraft:oak_planks"),
				List.of(new Cell(0, 0, 0, 'S'), new Cell(1, 0, 0, 'P'), new Cell(0, 0, 1, 'P')), "ai", 100);
		Report r = MaterialPlanner.plan(b, Map.of(), Map.of());
		assertEquals("2 oak planks, 1 stone", r.describeMissing());
	}

	@Test
	void doubleSlabsAndDoorsAreCountedInItems() throws Exception {
		Blueprint b = Blueprint.create("t", 2, 3, 1, Map.of('S', "minecraft:oak_slab[type=double]", 'D', "minecraft:oak_door[half=lower]",
				'U', "minecraft:oak_door[half=upper]"), List.of(new Cell(0, 0, 0, 'S'), new Cell(1, 0, 0, 'D'), new Cell(1, 1, 0, 'U')), "ai", 100);
		Report r = MaterialPlanner.plan(b, Map.of(), Map.of());
		assertEquals(2, r.rows().stream().filter(row -> row.item().equals("minecraft:oak_slab")).findFirst().orElseThrow().needed());
		assertEquals(1, r.rows().stream().filter(row -> row.item().equals("minecraft:oak_door")).findFirst().orElseThrow().needed());
		assertEquals(2, r.blocks());
	}
}
