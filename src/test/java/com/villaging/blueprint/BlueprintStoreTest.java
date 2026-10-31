package com.villaging.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlueprintStoreTest {
	@TempDir
	Path dir;

	private static final Comparator<Cell> CMP = Comparator.comparingInt(Cell::y).thenComparingInt(Cell::z).thenComparingInt(Cell::x);

	private static Blueprint sample(String name) throws Exception {
		List<Cell> cells = new ArrayList<>();
		for (int y = 0; y < 6; y++) {
			for (int z = 0; z < 8; z++) {
				for (int x = 0; x < 10; x++) {
					if ((x + y + z) % 3 == 0) {
						cells.add(new Cell(x, y, z, (x + z) % 2 == 0 ? 'S' : 'G'));
					}
				}
			}
		}
		return Blueprint.create(name, 10, 6, 8, Map.of('S', "minecraft:stone", 'G', "minecraft:oak_stairs[facing=north,half=bottom]"),
				cells, "template:cottage", 20_000);
	}

	@Test
	void roundTripKeepsEveryCell() throws Exception {
		Blueprint original = sample("hut");
		BlueprintStore.save(dir, original);
		Blueprint loaded = BlueprintStore.load(dir, "hut", 20_000).orElseThrow();
		assertEquals(original.cells().stream().sorted(CMP).toList(), loaded.cells().stream().sorted(CMP).toList());
		assertEquals(original.palette(), loaded.palette());
		assertEquals(original.sizeX(), loaded.sizeX());
		assertEquals("template:cottage", loaded.origin());
	}

	@Test
	void namesAreSortedAndDeleteRemovesTheFile() throws Exception {
		BlueprintStore.save(dir, sample("zeta"));
		BlueprintStore.save(dir, sample("alpha"));
		assertEquals(List.of("alpha", "zeta"), BlueprintStore.names(dir));
		assertTrue(BlueprintStore.delete(dir, "alpha"));
		assertEquals(List.of("zeta"), BlueprintStore.names(dir));
		assertFalse(BlueprintStore.delete(dir, "alpha"));
		assertTrue(BlueprintStore.load(dir, "alpha", 100).isEmpty());
	}

	@Test
	void namesOfAMissingFolderIsEmpty() {
		assertTrue(BlueprintStore.names(dir.resolve("nothing")).isEmpty());
	}

	@Test
	void aCorruptOrTruncatedFileLoadsAsEmptyInsteadOfThrowing() throws Exception {
		Files.writeString(dir.resolve("bad.json"), "{\"name\":");
		assertTrue(BlueprintStore.load(dir, "bad", 100).isEmpty());
		Files.writeString(dir.resolve("odd.json"), "{\"name\":\"odd\",\"size\":[1,1,1],\"palette\":{\"S\":\"stone\"},\"layers\":[[\"SS\"]]}");
		assertTrue(BlueprintStore.load(dir, "odd", 100).isEmpty());          // a row of the wrong width
	}

	@Test
	void aFileWithABannedBlockIsRefusedOnLoad() throws Exception {
		Files.writeString(dir.resolve("boom.json"),
				"{\"name\":\"boom\",\"size\":[1,1,1],\"origin\":\"ai\",\"palette\":{\"T\":\"minecraft:tnt\"},\"layers\":[[\"T\"]]}");
		assertTrue(BlueprintStore.load(dir, "boom", 100).isEmpty());
	}

	@Test
	void pathTricksInNamesAreRefused() throws Exception {
		Files.writeString(dir.resolveSibling("evil.json"), "{}");
		assertTrue(BlueprintStore.load(dir, "../evil", 100).isEmpty());
		assertFalse(BlueprintStore.delete(dir, "../evil"));
		assertTrue(Files.exists(dir.resolveSibling("evil.json")));
		assertFalse(BlueprintStore.names(dir).contains("evil"));
	}

	@Test
	void aFullSizeBlueprintStaysUnder200Kilobytes() throws Exception {
		List<Cell> cells = new ArrayList<>();
		for (int i = 0; i < 20_000; i++) {
			cells.add(new Cell(i % 48, (i / 2304) % 64, (i / 48) % 48, 'S'));
		}
		Blueprint big = Blueprint.create("big", 48, 64, 48, Map.of('S', "minecraft:stone"), cells, "ai", 20_000);
		BlueprintStore.save(dir, big);
		assertTrue(Files.size(dir.resolve("big.json")) < 200_000, "size " + Files.size(dir.resolve("big.json")));
		assertEquals(20_000, BlueprintStore.load(dir, "big", 20_000).orElseThrow().cells().size());
	}
}
