package com.villaging.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.villaging.blueprint.BlockRules.ItemCost;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlockRulesTest {
	@Test
	void parsesIdsWithAndWithoutNamespaceAndProperties() {
		assertEquals("minecraft:stone", BlockRules.parse("stone").id());
		BlockRules.State s = BlockRules.parse("minecraft:oak_stairs[facing=north,half=bottom]");
		assertEquals("minecraft:oak_stairs", s.id());
		assertEquals("north", s.properties().get("facing"));
		assertEquals(2, s.properties().size());
	}

	@Test
	void badSyntaxIsAPreciseError() {
		assertTrue(assertThrows(IllegalArgumentException.class, () -> BlockRules.parse("stone[facing")).getMessage().contains("stone[facing"));
		assertThrows(IllegalArgumentException.class, () -> BlockRules.parse(""));
		assertThrows(IllegalArgumentException.class, () -> BlockRules.parse("stone[facing]"));
		assertThrows(IllegalArgumentException.class, () -> BlockRules.parse("Bad Block"));
	}

	@Test
	void bannedBlocksHaveReasons() {
		for (String id : List.of("tnt", "minecraft:lava", "bedrock", "spawner", "water", "command_block", "fire", "minecraft:air")) {
			assertNotNull(BlockRules.denyReason(id), id);
		}
		assertNull(BlockRules.denyReason("minecraft:stone_bricks"));
	}

	@Test
	void costCountsItemsNotCells() {
		assertEquals(new ItemCost("minecraft:stone", 1), BlockRules.cost(BlockRules.parse("stone")));
		assertEquals(2, BlockRules.cost(BlockRules.parse("oak_slab[type=double]")).count());
		assertEquals(1, BlockRules.cost(BlockRules.parse("oak_slab[type=bottom]")).count());
		assertEquals(1, BlockRules.cost(BlockRules.parse("oak_door[half=lower]")).count());
		assertEquals(0, BlockRules.cost(BlockRules.parse("oak_door[half=upper]")).count());
		assertEquals(0, BlockRules.cost(BlockRules.parse("red_bed[part=head]")).count());
		assertEquals("minecraft:torch", BlockRules.cost(BlockRules.parse("wall_torch[facing=east]")).item());
		assertEquals("minecraft:soul_torch", BlockRules.cost(BlockRules.parse("soul_wall_torch")).item());
		assertEquals("minecraft:oak_sign", BlockRules.cost(BlockRules.parse("oak_wall_sign")).item());
	}
}
