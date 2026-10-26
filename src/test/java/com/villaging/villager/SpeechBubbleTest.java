package com.villaging.villager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpeechBubbleTest {
	@Test
	void formatWrapsTextInTypographicQuotes() {
		assertEquals("“Yes, my king.”", SpeechBubble.format("Yes, my king."));
	}

	@Test
	void formatTruncatesLongTextWithAnEllipsis() {
		String shown = SpeechBubble.format("a".repeat(200));
		assertTrue(shown.length() <= SpeechBubble.MAX_TEXT + 3);
		assertTrue(shown.contains("…"));
		assertTrue(SpeechBubble.isBubble(shown));
	}

	@Test
	void isBubbleRecognisesOnlyOurOwnFormat() {
		assertTrue(SpeechBubble.isBubble("“Hello”"));
		assertFalse(SpeechBubble.isBubble("Alma"));
		assertFalse(SpeechBubble.isBubble("\"Quoted\""));
		assertFalse(SpeechBubble.isBubble(""));
		assertFalse(SpeechBubble.isBubble(null));
	}
}
