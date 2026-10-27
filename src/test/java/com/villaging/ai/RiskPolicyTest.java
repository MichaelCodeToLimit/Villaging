package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RiskPolicyTest {
	private static JsonObject args(String json) {
		return JsonParser.parseString(json).getAsJsonObject();
	}

	@Test
	void lavaFireAndExplosivesAreRiskyWhenPlacedOrUsed() {
		assertEquals(Optional.of(RiskPolicy.Category.LAVA),
				RiskPolicy.classify("place_block", args("{\"item\":\"minecraft:lava_bucket\"}"), null));
		assertEquals(Optional.of(RiskPolicy.Category.FIRE),
				RiskPolicy.classify("use_item", args("{\"item\":\"minecraft:flint_and_steel\"}"), null));
		assertEquals(Optional.of(RiskPolicy.Category.FIRE),
				RiskPolicy.classify("place_block", args("{\"item\":\"minecraft:fire_charge\"}"), null));
		assertEquals(Optional.of(RiskPolicy.Category.EXPLOSIVE),
				RiskPolicy.classify("place_block", args("{\"item\":\"minecraft:tnt\"}"), null));
		assertEquals(Optional.of(RiskPolicy.Category.EXPLOSIVE),
				RiskPolicy.classify("use_item", args("{\"item\":\"minecraft:end_crystal\"}"), null));
	}

	@Test
	void theHeldItemCountsWhenTheCallNamesNoItem() {
		assertEquals(Optional.of(RiskPolicy.Category.LAVA),
				RiskPolicy.classify("use_block", args("{\"x\":1,\"y\":2,\"z\":3}"), "minecraft:lava_bucket"));
	}

	@Test
	void breakAreaIsMassDestructionOnlyAboveSixtyFourBlocks() {
		assertEquals(Optional.empty(),
				RiskPolicy.classify("break_area", args("{\"x1\":1,\"y1\":1,\"z1\":1,\"x2\":4,\"y2\":4,\"z2\":4}"), null));
		assertEquals(Optional.of(RiskPolicy.Category.MASS_DESTRUCTION),
				RiskPolicy.classify("break_area", args("{\"x1\":1,\"y1\":1,\"z1\":1,\"x2\":5,\"y2\":4,\"z2\":4}"), null));
		// Corners may be given in either order.
		assertEquals(Optional.of(RiskPolicy.Category.MASS_DESTRUCTION),
				RiskPolicy.classify("break_area", args("{\"x1\":5,\"y1\":4,\"z1\":4,\"x2\":1,\"y2\":1,\"z2\":1}"), null));
	}

	@Test
	void ordinaryCallsAreNotRisky() {
		assertEquals(Optional.empty(), RiskPolicy.classify("place_block", args("{\"item\":\"minecraft:stone\"}"), null));
		assertEquals(Optional.empty(), RiskPolicy.classify("go_to", args("{}"), null));
		assertEquals(Optional.empty(), RiskPolicy.classify("use_item", args("{}"), null));
	}

	@Test
	void mustAskUnlessTheCategoryWasSwitchedOff() {
		Optional<RiskPolicy.Category> fire = Optional.of(RiskPolicy.Category.FIRE);
		assertTrue(RiskPolicy.mustAsk(fire, Set.of()));
		assertFalse(RiskPolicy.mustAsk(fire, Set.of("fire")));
		assertTrue(RiskPolicy.mustAsk(fire, Set.of("lava")));
		assertFalse(RiskPolicy.mustAsk(Optional.empty(), Set.of()));
	}

	@Test
	void destructionVolumeCountsBlocksEachCallCanBreak() {
		assertEquals(1L, RiskPolicy.destructionVolume("mine_block", args("{\"x\":1,\"y\":2,\"z\":3}")));
		assertEquals(64L, RiskPolicy.destructionVolume("break_area", args("{\"x1\":1,\"y1\":1,\"z1\":1,\"x2\":4,\"y2\":4,\"z2\":4}")));
		assertEquals((long) RiskPolicy.TREE_VOLUME, RiskPolicy.destructionVolume("chop_tree", args("{\"x\":1,\"y\":2,\"z\":3}")));
		assertEquals(0L, RiskPolicy.destructionVolume("place_block", args("{}")));
		assertEquals(0L, RiskPolicy.destructionVolume("look_around", args("{}")));
	}

	@Test
	void bedsOutsideTheOverworldAndRespawnAnchorsExplode() {
		assertEquals(Optional.of(RiskPolicy.Category.EXPLOSIVE), RiskPolicy.classifyBlockUse("red_bed", false));
		assertEquals(Optional.empty(), RiskPolicy.classifyBlockUse("red_bed", true));
		assertEquals(Optional.of(RiskPolicy.Category.EXPLOSIVE), RiskPolicy.classifyBlockUse("respawn_anchor", true));
		assertEquals(Optional.empty(), RiskPolicy.classifyBlockUse("oak_door", false));
	}
}
