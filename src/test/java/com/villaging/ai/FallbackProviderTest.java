package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class FallbackProviderTest {
	private static final class Fake implements AiProvider {
		final String name;
		final List<Object> script;
		int calls;

		Fake(String name, Object... script) {
			this.name = name;
			this.script = List.of(script);
		}

		@Override
		public AiResponse complete(String system, List<ChatMessage> messages, List<ToolDef> tools) throws AiException {
			Object next = script.get(Math.min(calls++, script.size() - 1));
			if (next instanceof AiException e) {
				throw e;
			}
			return (AiResponse) next;
		}
	}

	private static final long SECOND = 1_000_000_000L;
	private final AtomicLong clock = new AtomicLong(1000 * SECOND);

	private static AiResponse said(String text) {
		return new AiResponse(text, List.of());
	}

	private static AiException http(int status) {
		return new AiException("AI service returned HTTP " + status, status);
	}

	private String ask(AiProvider provider) throws AiException {
		return provider.complete("sys", List.of(ChatMessage.user("hi")), List.of()).text();
	}

	@Test
	void aWorkingMainAiIsUsedAndTheBackupIsLeftAlone() throws Exception {
		Fake main = new Fake("main", said("main"));
		Fake backup = new Fake("backup", said("backup"));
		assertEquals("main", ask(new FallbackProvider(main, backup, clock::get)));
		assertEquals(0, backup.calls);
	}

	@Test
	void whenTheMainAiRunsOutOfQuotaTheBackupAnswers() throws Exception {
		Fake main = new Fake("main", http(429));
		Fake backup = new Fake("backup", said("backup"));
		assertEquals("backup", ask(new FallbackProvider(main, backup, clock::get)));
	}

	@Test
	void afterAQuotaFailureTheMainAiIsNotAskedAgainForAMinute() throws Exception {
		Fake main = new Fake("main", http(429), said("main again"));
		Fake backup = new Fake("backup", said("backup"));
		FallbackProvider provider = new FallbackProvider(main, backup, clock::get);
		ask(provider);
		clock.addAndGet(30 * SECOND);
		assertEquals("backup", ask(provider));
		assertEquals(1, main.calls); // not retried inside the minute
		clock.addAndGet(31 * SECOND);
		assertEquals("main again", ask(provider)); // tried again once the minute is over
		assertEquals(2, main.calls);
	}

	@Test
	void ifBothFailTheMainAisProblemIsReportedSoTheKingGetsTheRealReason() {
		Fake main = new Fake("main", http(429));
		Fake backup = new Fake("backup", new AiException("Could not reach the AI service"));
		AiException thrown = assertThrows(AiException.class, () -> ask(new FallbackProvider(main, backup, clock::get)));
		assertEquals(429, thrown.status());
	}

	@Test
	void theMainAisProblemIsStillReportedWhileItIsResting() throws Exception {
		Fake main = new Fake("main", http(503));
		Fake backup = new Fake("backup", said("ok"), new AiException("backup down"));
		FallbackProvider provider = new FallbackProvider(main, backup, clock::get);
		ask(provider);
		AiException thrown = assertThrows(AiException.class, () -> ask(provider));
		assertEquals(503, thrown.status());
	}

	@Test
	void aBadRequestFallsBackButDoesNotMakeTheMainAiRest() throws Exception {
		Fake main = new Fake("main", http(400), said("main"));
		Fake backup = new Fake("backup", said("backup"));
		FallbackProvider provider = new FallbackProvider(main, backup, clock::get);
		assertEquals("backup", ask(provider));
		assertEquals("main", ask(provider));
		assertEquals(2, main.calls);
	}

	@Test
	void aRejectedKeyAlsoFallsBackAndRests() throws Exception {
		Fake main = new Fake("main", http(403));
		Fake backup = new Fake("backup", said("backup"));
		FallbackProvider provider = new FallbackProvider(main, backup, clock::get);
		ask(provider);
		ask(provider);
		assertEquals(1, main.calls);
		assertEquals(2, backup.calls);
	}
}
