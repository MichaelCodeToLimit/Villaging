package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimiterTest {
	@Test
	void allowsUpToLimitThenBlocks() {
		AtomicLong clock = new AtomicLong();
		RateLimiter limiter = new RateLimiter(3, clock::get);
		assertTrue(limiter.tryAcquire());
		assertTrue(limiter.tryAcquire());
		assertTrue(limiter.tryAcquire());
		assertFalse(limiter.tryAcquire());
	}

	@Test
	void windowSlidesAfterSixtySeconds() {
		AtomicLong clock = new AtomicLong();
		RateLimiter limiter = new RateLimiter(1, clock::get);
		assertTrue(limiter.tryAcquire());
		clock.set(TimeUnit.SECONDS.toNanos(59));
		assertFalse(limiter.tryAcquire());
		clock.set(TimeUnit.SECONDS.toNanos(61));
		assertTrue(limiter.tryAcquire());
	}
}
