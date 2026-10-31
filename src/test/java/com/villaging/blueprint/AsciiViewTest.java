package com.villaging.blueprint;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AsciiViewTest {
	@Test
	void aLayerShowsRowsAndALegend() throws Exception {
		List<Cell> cells = new ArrayList<>();
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				if (!(x == 1 && z == 1)) {
					cells.add(new Cell(x, 0, z, 'S'));
				}
			}
		}
		cells.add(new Cell(1, 1, 1, 'G'));
		Blueprint b = Blueprint.create("t", 3, 2, 3, Map.of('S', "minecraft:stone", 'G', "minecraft:glass"), cells, "ai", 100);
		String layer0 = AsciiView.layer(b, 0);
		assertTrue(layer0.contains("SSS\nS.S\nSSS"), layer0);
		assertTrue(layer0.contains("S = minecraft:stone"), layer0);
		assertTrue(!layer0.contains("minecraft:glass"), "the legend lists only keys used on this layer");
		assertTrue(AsciiView.layer(b, 1).contains("G = minecraft:glass"));
	}

	@Test
	void aLayerOutsideTheBlueprintSaysSo() throws Exception {
		Blueprint b = Blueprint.create("t", 1, 1, 1, Map.of('S', "minecraft:stone"), List.of(), "ai", 10);
		assertTrue(AsciiView.layer(b, 5).contains("0"));
	}
}
