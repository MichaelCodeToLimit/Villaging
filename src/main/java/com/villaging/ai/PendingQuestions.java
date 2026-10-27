package com.villaging.ai;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Questions villagers have put to the king. Each can be answered once, only by the king it was asked of, and only
 * before it times out; otherwise it reads {@link #NO_ANSWER}. Pure logic with an injected clock.
 */
public final class PendingQuestions {
	public static final long TIMEOUT_NANOS = 120_000_000_000L;
	public static final String NO_ANSWER = "no answer";

	public static final PendingQuestions INSTANCE = new PendingQuestions();

	public record Question(long id, UUID king, int villagerId, String text, List<String> options,
			CompletableFuture<String> future, long expiresAtNanos) {
	}

	private final Map<Long, Question> open = new HashMap<>();
	private long nextId = new SecureRandom().nextLong() & 0x3fff_ffff_ffffL;

	public synchronized Question open(UUID king, int villagerId, String text, List<String> options, long nowNanos) {
		Question q = new Question(nextId++, king, villagerId, text, List.copyOf(options), new CompletableFuture<>(),
				nowNanos + TIMEOUT_NANOS);
		open.put(q.id(), q);
		return q;
	}

	/** True only for the first answer from the asked king before the timeout. An empty answer cancels. */
	public synchronized boolean answer(UUID player, long questionId, String text, long nowNanos) {
		Question q = open.get(questionId);
		if (q == null || !q.king().equals(player)) {
			return false;
		}
		if (nowNanos > q.expiresAtNanos()) {
			close(q);
			return false;
		}
		close(q, text == null || text.isBlank() ? NO_ANSWER : text);
		return true;
	}

	public synchronized void cancel(long questionId) {
		Question q = open.get(questionId);
		if (q != null) {
			close(q);
		}
	}

	public synchronized void expire(long nowNanos) {
		for (Question q : new ArrayList<>(open.values())) {
			if (nowNanos > q.expiresAtNanos()) {
				close(q);
			}
		}
	}

	public synchronized void cancelFor(int villagerId) {
		for (Question q : new ArrayList<>(open.values())) {
			if (q.villagerId() == villagerId) {
				close(q);
			}
		}
	}

	public synchronized void cancelAll() {
		for (Question q : new ArrayList<>(open.values())) {
			close(q);
		}
	}

	private void close(Question q) {
		close(q, NO_ANSWER);
	}

	private void close(Question q, String answer) {
		open.remove(q.id());
		q.future().complete(answer);
	}
}
