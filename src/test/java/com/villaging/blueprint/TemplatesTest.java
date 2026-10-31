package com.villaging.blueprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.villaging.blueprint.Templates.Limits;
import com.villaging.blueprint.Templates.Params;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TemplatesTest {
	private static final List<String> STYLES = List.of("stone", "wood", "brick", "sandstone", "dark");
	private static final int MAX = 20_000;

	private static int[] spread(int min, int def, int max) {
		return new int[] {min, def, max};
	}

	private static String at(Blueprint b, int x, int y, int z) {
		for (Cell c : b.cells()) {
			if (c.x() == x && c.y() == y && c.z() == z) {
				return b.stateAt(c);
			}
		}
		return null;
	}

	private static boolean anyState(Blueprint b, String fragment) {
		return b.cells().stream().anyMatch(c -> b.stateAt(c).contains(fragment));
	}

	@Test
	void everyTemplateCompilesWithinLimitsAcrossItsWholeParameterRange() throws Exception {
		for (String id : Templates.ids()) {
			Limits l = Templates.limits(id);
			Params d = Templates.defaults(id);
			for (String style : STYLES) {
				for (String roof : List.of("gable", "flat", "pyramid")) {
					for (int w : spread(l.minWidth(), d.width(), l.maxWidth())) {
						for (int dp : spread(l.minDepth(), d.depth(), l.maxDepth())) {
							for (int h : spread(l.minHeight(), d.height(), l.maxHeight())) {
								Blueprint b = Templates.build(id, "t", new Params(w, dp, h, style, roof), MAX);
								assertTrue(b.cells().size() > 0, id);
								assertTrue(b.cells().size() <= MAX, id + " " + w + "x" + dp + "x" + h + " has " + b.cells().size());
							}
						}
					}
				}
			}
		}
	}

	/** Air positions reachable from outside the box (with a one-block margin); cells in {@code passable} count as open. */
	private static Set<Long> reachable(Blueprint b, boolean doorsOpen) {
		Set<Long> solid = new HashSet<>();
		for (Cell c : b.cells()) {
			String state = b.stateAt(c);
			boolean passable = doorsOpen && (state.contains("_door") || state.contains("trapdoor"));
			boolean notAnObstacle = state.contains("torch") || state.contains("ladder");
			if (!passable && !notAnObstacle) {
				solid.add(key(c.x(), c.y(), c.z()));
			}
		}
		Set<Long> seen = new HashSet<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		queue.add(new int[] {-1, -1, -1});
		seen.add(key(-1, -1, -1));
		int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			for (int[] s : steps) {
				int x = p[0] + s[0];
				int y = p[1] + s[1];
				int z = p[2] + s[2];
				if (x < -1 || y < -1 || z < -1 || x > b.sizeX() || y > b.sizeY() || z > b.sizeZ()) {
					continue;
				}
				long k = key(x, y, z);
				if (solid.contains(k) || !seen.add(k)) {
					continue;
				}
				queue.add(new int[] {x, y, z});
			}
		}
		return seen;
	}

	private static long key(int x, int y, int z) {
		return ((long) (y + 2) * 200 + (x + 2)) * 200 + (z + 2);
	}

	@Test
	void cottagesAndTowersKeepTheWeatherOutExceptThroughTheDoor() throws Exception {
		for (String id : List.of("cottage", "tower")) {
			Limits l = Templates.limits(id);
			Params d = Templates.defaults(id);
			for (String roof : List.of("gable", "flat", "pyramid")) {
				for (int w : spread(l.minWidth(), d.width(), l.maxWidth())) {
					for (int dp : spread(l.minDepth(), d.depth(), l.maxDepth())) {
						Blueprint b = Templates.build(id, "t", new Params(w, dp, d.height(), "stone", roof), MAX);
						int px = b.sizeX() / 2;
						int pz = b.sizeZ() / 2;
						assertNull(at(b, px, 2, pz), id + " probe cell must be empty air inside");
						String label = id + " " + roof + " " + w + "x" + dp;
						assertFalse(reachable(b, false).contains(key(px, 2, pz)), label + " leaks even with the door shut");
						assertTrue(reachable(b, true).contains(key(px, 2, pz)), label + " cannot be entered through its door");
					}
				}
			}
		}
	}

	@Test
	void cottagesAndTowersHaveADoorAtGroundLevelAndALight() throws Exception {
		for (String id : List.of("cottage", "tower", "castle")) {
			Blueprint b = Templates.build(id, "t", Templates.defaults(id), MAX);
			assertTrue(b.cells().stream().anyMatch(c -> c.y() == 1 && b.stateAt(c).contains("_door") && b.stateAt(c).contains("half=lower")),
					id + " needs a door at ground level");
			assertTrue(anyState(b, "torch") || anyState(b, "lantern") || anyState(b, "glowstone"), id + " needs a light");
		}
	}

	@Test
	void outOfBoundsParametersAreCorrectedNotRejected() throws Exception {
		assertEquals(16, Templates.build("cottage", "c", new Params(99, 5, 4, "stone", "gable"), MAX).sizeX());
		assertEquals(5, Templates.build("cottage", "c", new Params(1, 5, 4, "stone", "gable"), MAX).sizeX());
		// an unknown roof falls back to the gable roof
		assertEquals(Templates.build("cottage", "c", new Params(7, 7, 4, "stone", "gable"), MAX).cells().size(),
				Templates.build("cottage", "c", new Params(7, 7, 4, "stone", "nonsense"), MAX).cells().size());
	}

	@Test
	void unknownTemplateOrStyleSaysWhatExists() {
		BlueprintException noTemplate = assertThrows(BlueprintException.class, () -> Templates.build("igloo", "c", Templates.defaults("cottage"), MAX));
		assertTrue(noTemplate.getMessage().contains("cottage") && noTemplate.getMessage().contains("castle"));
		BlueprintException noStyle = assertThrows(BlueprintException.class, () -> Templates.build("cottage", "c", new Params(7, 7, 4, "pink", "gable"), MAX));
		assertTrue(noStyle.getMessage().contains("stone") && noStyle.getMessage().contains("dark"));
	}

	@Test
	void castleHasAGateOpeningAndStaysUnderTheDefaultCellLimit() throws Exception {
		Params max = new Params(48, 48, 20, "stone", "gable");
		Blueprint big = Templates.build("castle", "k", max, MAX);
		assertTrue(big.cells().size() < MAX);
		Blueprint b = Templates.build("castle", "k", Templates.defaults("castle"), MAX);
		int gateX = b.sizeX() / 2;
		for (int y = 1; y <= Templates.defaults("castle").height(); y++) {
			assertNull(at(b, gateX, y, b.sizeZ() - 1), "the gate must be open at height " + y);
		}
	}

	@Test
	void wallsHaveAWalkwayAndBridgesHaveRailings() throws Exception {
		Blueprint wall = Templates.build("wall", "w", Templates.defaults("wall"), MAX);
		assertTrue(wall.cells().stream().anyMatch(c -> c.y() == Templates.defaults("wall").height()));
		Blueprint bridge = Templates.build("bridge", "b", Templates.defaults("bridge"), MAX);
		assertTrue(anyState(bridge, "fence") || anyState(bridge, "wall"));
	}

	@Test
	void idsAndBoundsAreDescribed() {
		assertEquals(List.of("cottage", "tower", "wall", "bridge", "castle"), Templates.ids());
		assertTrue(Templates.bounds("cottage").contains("width"));
	}
}
