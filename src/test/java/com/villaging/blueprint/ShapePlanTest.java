package com.villaging.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class ShapePlanTest {
	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	/** A plan with palette S=stone, G=glass, R=oak stairs, D=oak door and the given size and shapes. */
	private static JsonObject plan(String size, String... shapes) {
		return json("{\"size\":" + size + ",\"palette\":{\"S\":\"minecraft:stone\",\"G\":\"minecraft:glass\","
				+ "\"R\":\"minecraft:oak_stairs\",\"D\":\"minecraft:oak_door\"},\"shapes\":[" + String.join(",", shapes) + "]}");
	}

	private static Blueprint compile(JsonObject plan) throws BlueprintException {
		return ShapePlan.compile("test", plan, 20_000);
	}

	private static String at(Blueprint b, int x, int y, int z) {
		for (Cell c : b.cells()) {
			if (c.x() == x && c.y() == y && c.z() == z) {
				return b.stateAt(c);
			}
		}
		return null;
	}

	private static long count(Blueprint b, String fragment) {
		return b.cells().stream().filter(c -> b.stateAt(c).contains(fragment)).count();
	}

	private static void assertMessage(String fragment, Executable action) {
		BlueprintException e = assertThrows(BlueprintException.class, action);
		assertTrue(e.getMessage().contains(fragment), "message \"" + e.getMessage() + "\" should contain \"" + fragment + "\"");
	}

	@Test
	void hollowBoxHasWallsFloorAndRoofButNoInterior() throws Exception {
		Blueprint b = compile(plan("[5,4,5]", "{\"type\":\"hollow_box\",\"x\":0,\"y\":0,\"z\":0,\"w\":5,\"h\":4,\"d\":5,\"block\":\"S\"}"));
		assertEquals(5 * 4 * 5 - 3 * 2 * 3, b.cells().size());
		assertNull(at(b, 2, 1, 2));
		assertEquals("minecraft:stone", at(b, 0, 0, 0));
	}

	@Test
	void hollowBoxCanOmitItsFloorAndRoof() throws Exception {
		Blueprint b = compile(plan("[5,4,5]",
				"{\"type\":\"hollow_box\",\"x\":0,\"y\":0,\"z\":0,\"w\":5,\"h\":4,\"d\":5,\"block\":\"S\",\"floor\":false,\"roof\":false}"));
		assertEquals(16 * 4, b.cells().size());
		assertNull(at(b, 2, 0, 2));
	}

	@Test
	void wallsAreAnOpenRingAndFloorIsOneLayer() throws Exception {
		assertEquals(16 * 3, compile(plan("[5,3,5]", "{\"type\":\"walls\",\"x\":0,\"y\":0,\"z\":0,\"w\":5,\"h\":3,\"d\":5,\"block\":\"S\"}")).cells().size());
		Blueprint floor = compile(plan("[4,2,3]", "{\"type\":\"floor\",\"x\":0,\"y\":1,\"z\":0,\"w\":4,\"d\":3,\"block\":\"S\"}"));
		assertEquals(12, floor.cells().size());
		assertTrue(floor.cells().stream().allMatch(c -> c.y() == 1));
	}

	@Test
	void laterShapesOverwriteEarlierOnes() throws Exception {
		Blueprint b = compile(plan("[2,1,1]", "{\"type\":\"box\",\"x\":0,\"y\":0,\"z\":0,\"w\":2,\"h\":1,\"d\":1,\"block\":\"S\"}",
				"{\"type\":\"box\",\"x\":1,\"y\":0,\"z\":0,\"w\":1,\"h\":1,\"d\":1,\"block\":\"G\"}"));
		assertEquals("minecraft:stone", at(b, 0, 0, 0));
		assertEquals("minecraft:glass", at(b, 1, 0, 0));
	}

	@Test
	void windowReplacesCellsOfAWall() throws Exception {
		Blueprint b = compile(plan("[5,3,3]", "{\"type\":\"walls\",\"x\":0,\"y\":0,\"z\":0,\"w\":5,\"h\":3,\"d\":3,\"block\":\"S\"}",
				"{\"type\":\"window\",\"x\":1,\"y\":1,\"z\":0,\"w\":2,\"h\":1,\"axis\":\"x\",\"block\":\"G\"}"));
		assertEquals("minecraft:glass", at(b, 1, 1, 0));
		assertEquals("minecraft:glass", at(b, 2, 1, 0));
		assertEquals("minecraft:stone", at(b, 3, 1, 0));
	}

	@Test
	void gableRoofStepsUpToARidgeAndFillsTheEnds() throws Exception {
		Blueprint b = compile(plan("[7,4,5]",
				"{\"type\":\"roof_gable\",\"x\":0,\"y\":0,\"z\":0,\"w\":7,\"d\":5,\"ridge\":\"x\",\"block\":\"R\",\"ridge_block\":\"S\",\"fill\":\"S\"}"));
		assertEquals(14, count(b, "facing=north"));
		assertEquals(14, count(b, "facing=south"));
		assertEquals("minecraft:stone", at(b, 3, 2, 2));          // the ridge, in the middle row
		assertTrue(at(b, 3, 1, 1).contains("oak_stairs"));
		assertEquals(7 + 8, count(b, "minecraft:stone"));          // 7 ridge blocks plus 4 fill blocks at each end
		assertEquals("minecraft:stone", at(b, 0, 1, 2));           // gable end fill under the ridge
		assertNull(at(b, 3, 1, 2));                                // the inside under the ridge stays open
	}

	@Test
	void pyramidRoofHasShrinkingRings() throws Exception {
		Blueprint b = compile(plan("[5,3,5]", "{\"type\":\"roof_pyramid\",\"x\":0,\"y\":0,\"z\":0,\"w\":5,\"d\":5,\"block\":\"R\",\"ridge_block\":\"S\"}"));
		assertEquals(16 + 8 + 1, b.cells().size());
		assertEquals("minecraft:stone", at(b, 2, 2, 2));
	}

	@Test
	void flatRoofIsOneLayer() throws Exception {
		assertEquals(12, compile(plan("[4,2,3]", "{\"type\":\"roof_flat\",\"x\":0,\"y\":1,\"z\":0,\"w\":4,\"d\":3,\"block\":\"S\"}")).cells().size());
	}

	@Test
	void doorPlacesLowerAndUpperHalvesFacingTheSameWay() throws Exception {
		Blueprint b = compile(plan("[5,3,3]", "{\"type\":\"door\",\"x\":2,\"y\":0,\"z\":0,\"facing\":\"north\",\"block\":\"D\"}"));
		assertTrue(at(b, 2, 0, 0).contains("half=lower") && at(b, 2, 0, 0).contains("facing=north"));
		assertTrue(at(b, 2, 1, 0).contains("half=upper") && at(b, 2, 1, 0).contains("facing=north"));
	}

	@Test
	void cylindersAreSolidOrHollow() throws Exception {
		assertEquals(21, compile(plan("[5,1,5]", "{\"type\":\"cylinder\",\"x\":0,\"y\":0,\"z\":0,\"radius\":2,\"h\":1,\"block\":\"S\"}")).cells().size());
		assertEquals(12, compile(plan("[5,1,5]", "{\"type\":\"cylinder\",\"x\":0,\"y\":0,\"z\":0,\"radius\":2,\"h\":1,\"hollow\":true,\"block\":\"S\"}")).cells().size());
	}

	@Test
	void linesWalkFromOneEndToTheOther() throws Exception {
		assertEquals(5, compile(plan("[5,1,1]", "{\"type\":\"line\",\"x1\":0,\"y1\":0,\"z1\":0,\"x2\":4,\"y2\":0,\"z2\":0,\"block\":\"S\"}")).cells().size());
		assertEquals(4, compile(plan("[4,4,4]", "{\"type\":\"line\",\"x1\":0,\"y1\":0,\"z1\":0,\"x2\":3,\"y2\":3,\"z2\":3,\"block\":\"S\"}")).cells().size());
	}

	@Test
	void extraStatePropertiesAreAddedToTheBlock() throws Exception {
		Blueprint b = compile(plan("[1,1,1]", "{\"type\":\"box\",\"x\":0,\"y\":0,\"z\":0,\"w\":1,\"h\":1,\"d\":1,\"block\":\"R\",\"state\":\"facing=east,half=top\"}"));
		assertEquals("minecraft:oak_stairs[facing=east,half=top]", at(b, 0, 0, 0));
	}

	@Test
	void preciseErrorsNameTheShapeNumber() {
		assertMessage("shape 2", () -> compile(plan("[3,3,3]", "{\"type\":\"box\",\"x\":0,\"y\":0,\"z\":0,\"w\":1,\"h\":1,\"d\":1,\"block\":\"S\"}", "{\"type\":\"sphere\"}")));
		assertMessage("sphere", () -> compile(plan("[3,3,3]", "{\"type\":\"sphere\"}")));
		assertMessage("shape 1", () -> compile(plan("[5,5,5]", "{\"type\":\"box\",\"x\":0,\"y\":0,\"z\":0,\"w\":60,\"h\":1,\"d\":1,\"block\":\"S\"}")));
		assertMessage("shape 1", () -> compile(plan("[5,5,5]", "{\"type\":\"box\",\"x\":0,\"y\":0,\"z\":0,\"w\":1,\"h\":1,\"d\":1,\"block\":\"Q\"}")));
		assertMessage("\"w\"", () -> compile(plan("[5,5,5]", "{\"type\":\"box\",\"x\":0,\"y\":0,\"z\":0,\"h\":1,\"d\":1,\"block\":\"S\"}")));
		assertMessage("shape 1", () -> compile(plan("[5,5,5]", "{\"type\":\"box\",\"x\":0,\"y\":0,\"z\":0,\"w\":\"big\",\"h\":1,\"d\":1,\"block\":\"S\"}")));
	}

	@Test
	void planProblemsAreReportedClearly() {
		assertMessage("JSON", () -> ShapePlan.compile("a", null, 100));
		assertMessage("48", () -> compile(plan("[49,5,5]")));
		assertMessage("size", () -> compile(json("{\"palette\":{},\"shapes\":[]}")));
		assertMessage("shapes", () -> compile(json("{\"size\":[2,2,2],\"palette\":{}}")));
		StringBuilder many = new StringBuilder();
		for (int i = 0; i < 61; i++) {
			many.append(i == 0 ? "" : ",").append("{\"type\":\"box\",\"x\":0,\"y\":0,\"z\":0,\"w\":1,\"h\":1,\"d\":1,\"block\":\"S\"}");
		}
		assertMessage("61", () -> compile(plan("[2,2,2]", many.toString())));
	}

	@Test
	void bannedOrBadPaletteEntriesAreRefusedByName() {
		assertMessage("tnt", () -> compile(json("{\"size\":[1,1,1],\"palette\":{\"T\":\"minecraft:tnt\"},\"shapes\":[{\"type\":\"box\",\"x\":0,\"y\":0,\"z\":0,\"w\":1,\"h\":1,\"d\":1,\"block\":\"T\"}]}")));
		assertMessage("palette", () -> compile(json("{\"size\":[1,1,1],\"palette\":{\"ab\":\"stone\"},\"shapes\":[]}")));
		assertMessage("palette", () -> compile(json("{\"size\":[1,1,1],\"palette\":{\".\":\"stone\"},\"shapes\":[]}")));
	}

	@Test
	void theBlockBudgetIsEnforcedBeforeAHugeListIsBuilt() {
		assertMessage("blocks", () -> ShapePlan.compile("a", plan("[47,47,47]", "{\"type\":\"box\",\"x\":0,\"y\":0,\"z\":0,\"w\":47,\"h\":47,\"d\":47,\"block\":\"S\"}"), 5000));
	}
}
