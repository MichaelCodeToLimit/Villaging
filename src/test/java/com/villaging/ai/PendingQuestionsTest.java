package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingQuestionsTest {
	private static final UUID KING = UUID.randomUUID();
	private final PendingQuestions questions = new PendingQuestions();

	private PendingQuestions.Question open(long now) {
		return questions.open(KING, 7, "Burn it?", List.of("Yes", "No"), now);
	}

	@Test
	void theKingsFirstAnswerCompletesTheQuestion() {
		PendingQuestions.Question q = open(0);
		assertTrue(questions.answer(KING, q.id(), "Yes", 10));
		assertEquals("Yes", q.future().getNow("unset"));
	}

	@Test
	void aSecondAnswerIsRejectedAndChangesNothing() {
		PendingQuestions.Question q = open(0);
		assertTrue(questions.answer(KING, q.id(), "Yes", 10));
		assertFalse(questions.answer(KING, q.id(), "No", 20));
		assertEquals("Yes", q.future().getNow("unset"));
	}

	@Test
	void anAnswerFromAnotherPlayerIsRejected() {
		PendingQuestions.Question q = open(0);
		assertFalse(questions.answer(UUID.randomUUID(), q.id(), "Yes", 10));
		assertFalse(q.future().isDone());
	}

	@Test
	void anUnknownQuestionIdIsRejected() {
		PendingQuestions.Question q = open(0);
		assertFalse(questions.answer(KING, q.id() + 1, "Yes", 10));
		assertFalse(q.future().isDone());
	}

	@Test
	void anAnswerAfterTheTimeoutIsRejectedAndTheQuestionReadsNoAnswer() {
		PendingQuestions.Question q = open(0);
		assertFalse(questions.answer(KING, q.id(), "Yes", PendingQuestions.TIMEOUT_NANOS + 1));
		assertEquals(PendingQuestions.NO_ANSWER, q.future().getNow("unset"));
	}

	@Test
	void expireClosesOnlyOverdueQuestions() {
		PendingQuestions.Question old = open(0);
		PendingQuestions.Question fresh = open(PendingQuestions.TIMEOUT_NANOS);
		questions.expire(PendingQuestions.TIMEOUT_NANOS + 1);
		assertEquals(PendingQuestions.NO_ANSWER, old.future().getNow("unset"));
		assertFalse(fresh.future().isDone());
	}

	@Test
	void cancelForAVillagerClosesAllItsQuestions() {
		PendingQuestions.Question a = open(0);
		PendingQuestions.Question b = questions.open(KING, 8, "Other?", List.of(), 0);
		questions.cancelFor(7);
		assertEquals(PendingQuestions.NO_ANSWER, a.future().getNow("unset"));
		assertFalse(b.future().isDone());
		assertFalse(questions.answer(KING, a.id(), "Yes", 5));
	}

	@Test
	void anEmptyAnswerCountsAsCancel() {
		PendingQuestions.Question q = open(0);
		assertTrue(questions.answer(KING, q.id(), "", 10));
		assertEquals(PendingQuestions.NO_ANSWER, q.future().getNow("unset"));
	}

	@Test
	void idsAreDistinct() {
		assertTrue(open(0).id() != open(0).id());
	}
}
