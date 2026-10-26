package com.villaging.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Sliding-window limiter: at most {@code callsPerMinute} acquisitions in any 60 seconds. */
public final class RateLimiter {
	private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(60);

	private final int callsPerMinute;
	private final LongSupplier nanoClock;
	private final Deque<Long> stamps = new ArrayDeque<>();

	public RateLimiter(int callsPerMinute, LongSupplier nanoClock) {
		this.callsPerMinute = callsPerMinute;
		this.nanoClock = nanoClock;
	}

	public synchronized boolean tryAcquire() {
		long now = nanoClock.getAsLong();
		while (!stamps.isEmpty() && now - stamps.peekFirst() >= WINDOW_NANOS) {
			stamps.removeFirst();
		}
		if (stamps.size() >= callsPerMinute) {
			return false;
		}
		stamps.addLast(now);
		return true;
	}
}
