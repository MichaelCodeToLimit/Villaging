package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RoundTimerTest {
	@AfterEach
	void clearProperty() {
		System.clearProperty("villaging.debug");
	}

	@Test
	void lineHasSecondsCharsAndCalls() {
		assertEquals("AI round: 4.2 s, 31,200 chars, 2 tool calls", RoundTimer.line(31_200, 4_200, 2));
		assertEquals("AI round: 0.9 s, 900 chars, 1 tool call", RoundTimer.line(900, 900, 1));
	}

	@Test
	void lineNeverContainsAnyText() {
		assertTrue(RoundTimer.line(123_456, 12_345, 0).matches("AI round: [0-9.]+ s, [0-9,]+ chars, [0-9]+ tool calls?"));
	}

	@Test
	void logsSlowRoundsOnly() {
		assertFalse(RoundTimer.shouldLog(1_499));
		assertTrue(RoundTimer.shouldLog(1_500));
	}

	@Test
	void debugPropertyLogsEverything() {
		System.setProperty("villaging.debug", "true");
		assertTrue(RoundTimer.shouldLog(5));
	}
}
