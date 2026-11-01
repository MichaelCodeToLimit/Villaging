package com.villaging.build;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BuildRegistryTest {
	private final UUID a = UUID.randomUUID();
	private final UUID b = UUID.randomUUID();
	private final UUID c = UUID.randomUUID();

	@AfterEach
	void reset() {
		BuildRegistry.clear();
	}

	@Test
	void onlyAsManyBuildsAsAllowedRunAtOnce() {
		assertTrue(BuildRegistry.tryStart(a, 2));
		assertTrue(BuildRegistry.tryStart(b, 2));
		assertFalse(BuildRegistry.tryStart(c, 2));
		assertTrue(BuildRegistry.tryStart(a, 2), "a build that is already counted may start again");
		BuildRegistry.finish(a);
		assertTrue(BuildRegistry.tryStart(c, 2));
	}

	@Test
	void theAiIsWokenOncePerDistinctShortageSoAPauseCannotLoopForever() {
		assertTrue(BuildRegistry.shouldWake(a, "I need 5 more cobblestone"));
		assertFalse(BuildRegistry.shouldWake(a, "I need 5 more cobblestone"), "the same shortage must not wake the AI again");
		assertTrue(BuildRegistry.shouldWake(a, "I need 2 more stone bricks"), "a different shortage is news");
		assertTrue(BuildRegistry.shouldWake(b, "I need 5 more cobblestone"), "another helper has its own record");
	}

	@Test
	void finishingABuildMarksASlotFreedExactlyOnce() {
		assertFalse(BuildRegistry.consumeSlotFreed());
		BuildRegistry.tryStart(a, 2);
		BuildRegistry.finish(b);
		assertFalse(BuildRegistry.consumeSlotFreed(), "finishing a build that was not running frees nothing");
		BuildRegistry.finish(a);
		assertTrue(BuildRegistry.consumeSlotFreed());
		assertFalse(BuildRegistry.consumeSlotFreed());
	}

	@Test
	void finishingABuildForgetsItsLastShortage() {
		assertTrue(BuildRegistry.shouldWake(a, "I need 5 more cobblestone"));
		BuildRegistry.forgetWake(a);
		assertTrue(BuildRegistry.shouldWake(a, "I need 5 more cobblestone"));
	}
}
