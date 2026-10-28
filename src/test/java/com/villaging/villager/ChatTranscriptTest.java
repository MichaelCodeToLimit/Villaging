package com.villaging.villager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChatTranscriptTest {
	@Test
	void turnsSavedMemoryIntoSpeakerLines() {
		List<ChatTranscript.Line> lines = ChatTranscript.parse(List.of("king: hello", "me: Greetings, King.", "king: mine that"));
		assertEquals(3, lines.size());
		assertTrue(lines.get(0).fromKing());
		assertEquals("hello", lines.get(0).text());
		assertFalse(lines.get(1).fromKing());
		assertEquals("Greetings, King.", lines.get(1).text());
	}

	@Test
	void skipsLinesItDoesNotUnderstandAndBlankOnes() {
		List<ChatTranscript.Line> lines = ChatTranscript.parse(List.of("tool: ok", "king:   ", "nonsense", "me: Hi"));
		assertEquals(1, lines.size());
		assertEquals("Hi", lines.get(0).text());
	}

	@Test
	void theServerSendsOnlyTheLastLinesAndShortensLongOnes() {
		List<String> memory = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			memory.add("king: line " + i);
		}
		memory.add("me: " + "x".repeat(2000));
		List<String> sent = ChatTranscript.forClient(memory);
		assertEquals(ChatTranscript.MAX_LINES, sent.size());
		assertTrue(sent.get(sent.size() - 1).length() <= ChatTranscript.MAX_LINE_LENGTH);
		assertTrue(sent.get(sent.size() - 1).startsWith("me: "));
		assertEquals("king: line 11", sent.get(0));
	}
}
