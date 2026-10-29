package com.villaging.villager;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.villaging.villager.ChatSelection.Pos;
import com.villaging.villager.ChatSelection.Row;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatSelectionTest {
	// message 0 wraps over two lines, message 1 is one line
	private static final List<Row> ROWS = List.of(
			new Row("You: please walk to the", 0),
			new Row("spot six blocks east", 0),
			new Row("Alma: Right away, my king!", 1));

	@Test
	void selectionInsideOneLineCopiesJustThoseCharacters() {
		assertEquals("walk", ChatSelection.selectedText(ROWS, new Pos(0, 12), new Pos(0, 16)));
	}

	@Test
	void selectionWorksWhenDraggedBackwards() {
		assertEquals("walk", ChatSelection.selectedText(ROWS, new Pos(0, 16), new Pos(0, 12)));
	}

	@Test
	void wrappedLinesOfOneMessageJoinWithASpace() {
		assertEquals("the spot six", ChatSelection.selectedText(ROWS, new Pos(0, 20), new Pos(1, 8)));
	}

	@Test
	void differentMessagesJoinWithANewline() {
		assertEquals("east\nAlma", ChatSelection.selectedText(ROWS, new Pos(1, 16), new Pos(2, 4)));
	}

	@Test
	void aSelectionThatIsJustAPointCopiesNothing() {
		assertEquals("", ChatSelection.selectedText(ROWS, new Pos(0, 5), new Pos(0, 5)));
	}

	@Test
	void outOfRangeColumnsAreClampedInsteadOfCrashing() {
		assertEquals("Alma: Right away, my king!", ChatSelection.selectedText(ROWS, new Pos(2, -5), new Pos(2, 999)));
		assertEquals("", ChatSelection.selectedText(List.of(), new Pos(0, 0), new Pos(3, 3)));
	}

	@Test
	void doubleClickPicksTheWordUnderTheCursor() {
		assertArrayEquals(new int[] {12, 16}, ChatSelection.wordAt("You: please walk to the", 14));
		assertArrayEquals(new int[] {0, 4}, ChatSelection.wordAt("You: please", 0));
		assertArrayEquals(new int[] {5, 11}, ChatSelection.wordAt("You: please", 7));
	}

	@Test
	void clickingASpacePicksNothing() {
		assertArrayEquals(new int[] {4, 4}, ChatSelection.wordAt("You: please", 4));
	}

	@Test
	void tripleClickPicksTheWholeMessageIncludingItsWrappedLines() {
		Pos[] bounds = ChatSelection.messageAround(ROWS, 1);
		assertEquals(new Pos(0, 0), bounds[0]);
		assertEquals(new Pos(1, 20), bounds[1]);
		assertEquals("You: please walk to the spot six blocks east", ChatSelection.selectedText(ROWS, bounds[0], bounds[1]));
	}
}
