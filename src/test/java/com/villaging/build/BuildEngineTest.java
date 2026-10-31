package com.villaging.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.villaging.blueprint.Blueprint;
import com.villaging.blueprint.BuildOrder;
import com.villaging.blueprint.Cell;
import com.villaging.build.BuildEngine.Next;
import com.villaging.build.BuildEngine.Step;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import org.junit.jupiter.api.Test;

class BuildEngineTest {
	private static final int AX = 100;
	private static final int AY = 64;
	private static final int AZ = 100;

	/** A tiny world: positions to state text, plus cells that are occupied, protected or unsupported. */
	private static final class FakeWorld implements BuildEngine.World {
		final Map<String, String> blocks = new HashMap<>();
		final Set<String> occupied = new HashSet<>();
		final Set<String> protectedCells = new HashSet<>();
		final Set<String> neverSupported = new HashSet<>();
		final Map<String, String> requires = new HashMap<>();

		static String key(int x, int y, int z) {
			return x + "," + y + "," + z;
		}

		@Override
		public boolean matches(int x, int y, int z, String state) {
			return state.equals(blocks.get(key(x, y, z)));
		}

		@Override
		public boolean isFree(int x, int y, int z) {
			return !occupied.contains(key(x, y, z)) && !blocks.containsKey(key(x, y, z));
		}

		@Override
		public boolean supported(int x, int y, int z) {
			if (neverSupported.contains(key(x, y, z))) {
				return false;
			}
			String needed = requires.get(key(x, y, z));
			return needed == null || blocks.containsKey(needed);
		}

		@Override
		public boolean mayPlace(int x, int y, int z) {
			return !protectedCells.contains(key(x, y, z));
		}
	}

	private static final class FakeItems implements BuildEngine.Items {
		final Map<String, Integer> carried = new HashMap<>();
		final Map<String, Integer> stockpile = new HashMap<>();

		@Override
		public int carried(String item) {
			return carried.getOrDefault(item, 0);
		}

		@Override
		public int stockpile(String item) {
			return stockpile.getOrDefault(item, 0);
		}
	}

	private static Blueprint row(int length) throws Exception {
		List<Cell> cells = new ArrayList<>();
		for (int x = 0; x < length; x++) {
			cells.add(new Cell(x, 0, 0, 'S'));
		}
		return Blueprint.create("row", Math.max(length, 1), 1, 1, Map.of('S', "minecraft:stone"), cells, "ai", 100);
	}

	private static BuildEngine engine(Blueprint b) {
		return new BuildEngine(BuildOrder.order(b), b, AX, AY, AZ);
	}

	/** Runs the engine like the game does: places on PLACE, fetches on FETCH, stops on a pause or the end. */
	private static List<Next> drive(BuildEngine engine, FakeWorld world, FakeItems items, int maxSteps) {
		List<Next> seen = new ArrayList<>();
		for (int i = 0; i < maxSteps; i++) {
			Next next = engine.next(world, items);
			seen.add(next);
			switch (next.step()) {
				case PLACE -> {
					Cell c = engine.cell(next.cellIndex());
					world.blocks.put(FakeWorld.key(AX + c.x(), AY + c.y(), AZ + c.z()), "minecraft:stone");
					items.carried.merge(next.item(), -next.count(), Integer::sum);
					engine.placed();
				}
				case FETCH -> {
					items.stockpile.merge(next.item(), -next.count(), Integer::sum);
					items.carried.merge(next.item(), next.count(), Integer::sum);
				}
				case PAUSE_MISSING, FINISHED -> {
					return seen;
				}
				default -> {
				}
			}
		}
		return seen;
	}

	@Test
	void placesEveryCellInOrderAndFinishes() throws Exception {
		BuildEngine e = engine(row(5));
		FakeItems items = new FakeItems();
		items.carried.put("minecraft:stone", 50);
		List<Next> seen = drive(e, new FakeWorld(), items, 50);
		assertEquals(5, seen.stream().filter(n -> n.step() == Step.PLACE).count());
		assertEquals(Step.FINISHED, seen.get(seen.size() - 1).step());
		assertEquals(5, e.done());
		assertEquals(0, e.blockedCount());
		assertEquals(45, items.carried("minecraft:stone"));
	}

	@Test
	void cellsThatAlreadyMatchAreSkippedSoAResumeNeverReplacesThem() throws Exception {
		FakeWorld world = new FakeWorld();
		for (int x = 0; x < 3; x++) {
			world.blocks.put(FakeWorld.key(AX + x, AY, AZ), "minecraft:stone");
		}
		FakeItems items = new FakeItems();
		items.carried.put("minecraft:stone", 10);
		List<Next> seen = drive(engine(row(5)), world, items, 50);
		assertEquals(3, seen.stream().filter(n -> n.step() == Step.SKIP_DONE).count());
		assertEquals(2, seen.stream().filter(n -> n.step() == Step.PLACE).count());
		assertEquals(8, items.carried("minecraft:stone"));
	}

	@Test
	void anOccupiedCellIsBlockedWithANoteNotBroken() throws Exception {
		FakeWorld world = new FakeWorld();
		world.occupied.add(FakeWorld.key(AX + 2, AY, AZ));
		FakeItems items = new FakeItems();
		items.carried.put("minecraft:stone", 10);
		BuildEngine e = engine(row(4));
		List<Next> seen = drive(e, world, items, 50);
		assertEquals(1, seen.stream().filter(n -> n.step() == Step.SKIP_BLOCKED).count());
		assertEquals(1, e.blockedCount());
		assertTrue(e.blockedNotes().get(0).contains("102,64,100"), e.blockedNotes().get(0));
		assertEquals(3, e.done());
		assertTrue(world.blocks.get(FakeWorld.key(AX + 2, AY, AZ)) == null, "nothing is placed on an occupied cell");
	}

	@Test
	void protectedAndNeverSupportedCellsAreBlockedAndNotRetriedForever() throws Exception {
		FakeWorld world = new FakeWorld();
		world.protectedCells.add(FakeWorld.key(AX, AY, AZ));
		world.neverSupported.add(FakeWorld.key(AX + 3, AY, AZ));
		FakeItems items = new FakeItems();
		items.carried.put("minecraft:stone", 10);
		BuildEngine e = engine(row(5));
		List<Next> seen = drive(e, world, items, 100);
		assertEquals(Step.FINISHED, seen.get(seen.size() - 1).step());
		assertEquals(2, e.blockedCount());
		assertEquals(3, e.done());
		assertTrue(e.blockedNotes().stream().anyMatch(n -> n.contains("protected")));
		assertTrue(e.blockedNotes().stream().anyMatch(n -> n.contains("nothing to build against")));
	}

	@Test
	void aCellThatBecomesSupportedLaterIsPlacedOnTheSecondPass() throws Exception {
		FakeWorld world = new FakeWorld();
		world.requires.put(FakeWorld.key(AX + 2, AY, AZ), FakeWorld.key(AX + 1, AY, AZ));   // cell 2 hangs on cell 1
		FakeItems items = new FakeItems();
		items.carried.put("minecraft:stone", 10);
		BuildEngine e = new BuildEngine(List.of(new Cell(2, 0, 0, 'S'), new Cell(1, 0, 0, 'S')), rowOf(3), AX, AY, AZ);
		List<Next> seen = drive(e, world, items, 50);
		assertEquals(0, e.blockedCount(), e.blockedNotes().toString());
		assertEquals(2, e.done());
		assertEquals(Step.FINISHED, seen.get(seen.size() - 1).step());
		// cell 1 was placed before cell 2 even though the order listed cell 2 first
		List<Integer> placedOrder = seen.stream().filter(n -> n.step() == Step.PLACE).map(Next::cellIndex).toList();
		assertEquals(List.of(1, 0), placedOrder);
	}

	private static Blueprint rowOf(int length) throws Exception {
		return row(length);
	}

	@Test
	void whenItemsAreNotCarriedItFetchesExactlyWhatTheRestNeedsFromTheStockpile() throws Exception {
		FakeItems items = new FakeItems();
		items.stockpile.put("minecraft:stone", 100);
		BuildEngine e = engine(row(5));
		Next first = e.next(new FakeWorld(), items);
		assertEquals(Step.FETCH, first.step());
		assertEquals("minecraft:stone", first.item());
		assertEquals(5, first.count());
	}

	@Test
	void aBigNeedIsFetchedInBatchesOfAtMost64() throws Exception {
		List<Cell> cells = new ArrayList<>();
		for (int i = 0; i < 100; i++) {
			cells.add(new Cell(i % 10, i / 100, i / 10, 'S'));
		}
		Blueprint b = Blueprint.create("big", 10, 1, 10, Map.of('S', "minecraft:stone"), cells, "ai", 1000);
		FakeItems items = new FakeItems();
		items.stockpile.put("minecraft:stone", 500);
		assertEquals(64, engine(b).next(new FakeWorld(), items).count());
	}

	@Test
	void whenNothingIsLeftAnywhereItPausesNamingTheItem() throws Exception {
		FakeItems items = new FakeItems();
		items.carried.put("minecraft:stone", 2);
		BuildEngine e = engine(row(5));
		List<Next> seen = drive(e, new FakeWorld(), items, 50);
		Next last = seen.get(seen.size() - 1);
		assertEquals(Step.PAUSE_MISSING, last.step());
		assertEquals("minecraft:stone", last.item());
		assertEquals(3, last.count());
		assertTrue(last.note().contains("I need 3 more stone"), last.note());
		assertEquals(2, e.done());
	}

	@Test
	void aResumedEngineContinuesWhereTheSavedCountSaysAndAFreshOneRechecksEverything() throws Exception {
		FakeWorld world = new FakeWorld();
		for (int x = 0; x < 2; x++) {
			world.blocks.put(FakeWorld.key(AX + x, AY, AZ), "minecraft:stone");
		}
		FakeItems items = new FakeItems();
		items.carried.put("minecraft:stone", 10);
		BuildEngine resumed = engine(row(5));
		resumed.startAt(2);
		Next next = resumed.next(world, items);
		assertEquals(Step.PLACE, next.step());
		assertEquals(2, next.cellIndex());
		BuildEngine fresh = engine(row(5));
		assertEquals(Step.SKIP_DONE, fresh.next(world, items).step());
	}

	@Test
	void aDoubleSlabCellNeedsTwoItems() throws Exception {
		Blueprint b = Blueprint.create("slab", 1, 1, 1, Map.of('D', "minecraft:oak_slab[type=double]"), List.of(new Cell(0, 0, 0, 'D')), "ai", 10);
		FakeItems items = new FakeItems();
		items.carried.put("minecraft:oak_slab", 1);
		Next next = engine(b).next(new FakeWorld(), items);
		assertEquals(Step.PAUSE_MISSING, next.step());
		assertEquals(1, next.count());
	}
}
