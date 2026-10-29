package com.villaging.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.villaging.command.HelperRoster.Ambiguous;
import com.villaging.command.HelperRoster.Found;
import com.villaging.command.HelperRoster.Helper;
import com.villaging.command.HelperRoster.NotFound;
import java.util.List;
import org.junit.jupiter.api.Test;

class HelperRosterTest {
	private static final List<Helper> HELPERS = List.of(new Helper(1, "Alma"), new Helper(2, "Bob"), new Helper(3, "Bobbi"),
			new Helper(4, "Odo Baggins"));

	@Test
	void anExactNameIgnoringCapitalsIsFound() {
		assertEquals(new Found(new Helper(2, "Bob")), HelperRoster.find(HELPERS, "bob"));
	}

	@Test
	void anExactNameWinsOverALongerNameThatStartsTheSame() {
		assertEquals(new Found(new Helper(2, "Bob")), HelperRoster.find(HELPERS, "BOB"));
	}

	@Test
	void aUniquePrefixIsEnough() {
		assertEquals(new Found(new Helper(1, "Alma")), HelperRoster.find(HELPERS, "al"));
	}

	@Test
	void namesWithSpacesWorkAndSurroundingSpacesAreIgnored() {
		assertEquals(new Found(new Helper(4, "Odo Baggins")), HelperRoster.find(HELPERS, "  odo baggins "));
	}

	@Test
	void aPrefixSharedByTwoHelpersListsBothInsteadOfGuessing() {
		Object result = HelperRoster.find(HELPERS, "bo");
		Ambiguous ambiguous = assertInstanceOf(Ambiguous.class, result);
		assertEquals(List.of("Bob", "Bobbi"), ambiguous.names());
	}

	@Test
	void anUnknownOrBlankNameIsNotFound() {
		assertInstanceOf(NotFound.class, HelperRoster.find(HELPERS, "Zed"));
		assertInstanceOf(NotFound.class, HelperRoster.find(HELPERS, "   "));
		assertInstanceOf(NotFound.class, HelperRoster.find(List.of(), "Bob"));
	}

	@Test
	void twoHelpersWithTheSameNameAreAmbiguousToo() {
		List<Helper> twins = List.of(new Helper(1, "Bob"), new Helper(2, "Bob"));
		assertInstanceOf(Ambiguous.class, HelperRoster.find(twins, "bob"));
	}

	@Test
	void announcementsAreCappedAtTheLimitKeepingTheFirstGiven() {
		List<Helper> many = new java.util.ArrayList<>();
		for (int i = 0; i < 20; i++) {
			many.add(new Helper(i, "H" + i));
		}
		List<Helper> capped = HelperRoster.limit(many, 12);
		assertEquals(12, capped.size());
		assertEquals(0, capped.get(0).id());
		assertEquals(11, capped.get(11).id());
	}

	@Test
	void announcementsAreStaggeredSoTheAiIsNotFlooded() {
		assertEquals(List.of(0, 30, 60, 90), HelperRoster.staggerTicks(4, 30));
		assertTrue(HelperRoster.staggerTicks(0, 30).isEmpty());
	}
}
