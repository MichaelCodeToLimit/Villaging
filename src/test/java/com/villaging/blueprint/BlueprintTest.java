package com.villaging.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class BlueprintTest {
	private static final Map<Character, String> PAL = Map.of('S', "minecraft:stone");
	private static final Comparator<Cell> CMP = Comparator.comparingInt(Cell::y).thenComparingInt(Cell::x).thenComparingInt(Cell::z);

	private static void assertMessage(String fragment, Executable action) {
		BlueprintException e = assertThrows(BlueprintException.class, action);
		assertTrue(e.getMessage().contains(fragment), "message \"" + e.getMessage() + "\" should contain \"" + fragment + "\"");
	}

	@Test
	void validBlueprintIsCreated() throws Exception {
		Blueprint b = Blueprint.create("hut", 3, 2, 3, PAL, List.of(new Cell(0, 0, 0, 'S'), new Cell(1, 1, 2, 'S')), "ai", 100);
		assertEquals(2, b.cells().size());
		assertEquals("minecraft:stone", b.stateAt(b.cells().get(0)));
	}

	@Test
	void badNamesAndSizesAreRejectedWithTheReason() {
		assertMessage("name", () -> Blueprint.create("Bad Name", 1, 1, 1, PAL, List.of(), "ai", 10));
		assertMessage("name", () -> Blueprint.create("", 1, 1, 1, PAL, List.of(), "ai", 10));
		assertMessage("48", () -> Blueprint.create("a", 49, 1, 1, PAL, List.of(), "ai", 10));
		assertMessage("48", () -> Blueprint.create("a", 1, 1, 49, PAL, List.of(), "ai", 10));
		assertMessage("64", () -> Blueprint.create("a", 1, 65, 1, PAL, List.of(), "ai", 10));
		assertMessage("size", () -> Blueprint.create("a", 0, 1, 1, PAL, List.of(), "ai", 10));
	}

	@Test
	void bannedBlocksAreRejectedByName() {
		assertMessage("tnt", () -> Blueprint.create("a", 1, 1, 1, Map.of('T', "minecraft:tnt"), List.of(new Cell(0, 0, 0, 'T')), "ai", 10));
		assertMessage("palette", () -> Blueprint.create("a", 1, 1, 1, Map.of('X', "not a block!"), List.of(), "ai", 10));
	}

	@Test
	void cellProblemsAreRejected() {
		assertMessage("outside", () -> Blueprint.create("a", 2, 2, 2, PAL, List.of(new Cell(2, 0, 0, 'S')), "ai", 10));
		assertMessage("outside", () -> Blueprint.create("a", 2, 2, 2, PAL, List.of(new Cell(0, -1, 0, 'S')), "ai", 10));
		assertMessage("palette", () -> Blueprint.create("a", 2, 2, 2, PAL, List.of(new Cell(0, 0, 0, 'Q')), "ai", 10));
		assertMessage("twice", () -> Blueprint.create("a", 2, 2, 2, PAL, List.of(new Cell(0, 0, 0, 'S'), new Cell(0, 0, 0, 'S')), "ai", 10));
		assertMessage("blocks", () -> Blueprint.create("a", 2, 2, 2, PAL, List.of(new Cell(0, 0, 0, 'S'), new Cell(1, 0, 0, 'S')), "ai", 1));
	}

	@Test
	void fourQuarterTurnsGiveTheOriginalAndOddTurnsSwapSizes() throws Exception {
		Blueprint b = Blueprint.create("a", 4, 2, 3, PAL, List.of(new Cell(0, 0, 0, 'S'), new Cell(3, 1, 2, 'S')), "ai", 10);
		Blueprint around = b.rotated(1).rotated(1).rotated(1).rotated(1);
		assertEquals(b.cells().stream().sorted(CMP).toList(), around.cells().stream().sorted(CMP).toList());
		assertEquals(b.sizeX(), around.sizeX());
		assertEquals(3, b.rotated(1).sizeX());
		assertEquals(4, b.rotated(1).sizeZ());
		assertTrue(b.rotated(1).cells().stream().allMatch(c -> c.x() >= 0 && c.x() < 3 && c.z() >= 0 && c.z() < 4));
		assertEquals(b.cells().stream().sorted(CMP).toList(), b.rotated(0).cells().stream().sorted(CMP).toList());
		assertEquals(b.rotated(2).cells().stream().sorted(CMP).toList(), b.rotated(1).rotated(1).cells().stream().sorted(CMP).toList());
	}

	@Test
	void aQuarterTurnMovesTheCornerWhereAClockwiseTurnPutsIt() throws Exception {
		// one block at the north-west corner (0,0) of a 4 (x) by 3 (z) plan goes to the north-east corner of the 3 by 4 plan
		Blueprint b = Blueprint.create("a", 4, 1, 3, PAL, List.of(new Cell(0, 0, 0, 'S')), "ai", 10);
		Cell moved = b.rotated(1).cells().get(0);
		assertEquals(2, moved.x());
		assertEquals(0, moved.z());
	}
}
