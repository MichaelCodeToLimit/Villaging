package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class AgentLoopTest {
	/** Returns scripted responses (or throws scripted exceptions) in order, then repeats the last. */
	static final class Scripted implements AiProvider {
		final List<Object> script;
		final List<List<ChatMessage>> seen = new ArrayList<>();

		Scripted(Object... script) {
			this.script = List.of(script);
		}

		@Override
		public synchronized AiResponse complete(String system, List<ChatMessage> messages, List<ToolDef> tools)
				throws AiException {
			seen.add(List.copyOf(messages));
			Object next = script.get(Math.min(seen.size() - 1, script.size() - 1));
			if (next instanceof AiException e) {
				throw e;
			}
			return (AiResponse) next;
		}

		synchronized int calls() {
			return seen.size();
		}
	}

	static final class FakeExecutor implements ActionExecutor {
		final List<ToolCall> executed = new ArrayList<>();
		Function<ToolCall, Optional<String>> validator = c -> Optional.empty();
		Function<ToolCall, CompletableFuture<String>> behavior = c -> CompletableFuture.completedFuture("ok");
		volatile boolean alive = true;

		@Override
		public Optional<String> validate(ToolCall call) {
			return validator.apply(call);
		}

		@Override
		public CompletableFuture<String> execute(ToolCall call) {
			executed.add(call);
			return behavior.apply(call);
		}

		@Override
		public boolean isAlive() {
			return alive;
		}
	}

	private static ToolCall call(String id, String name) {
		return new ToolCall(id, name, new JsonObject());
	}

	private static AiResponse tool(String id, String name) {
		return new AiResponse("", List.of(call(id, name)));
	}

	private static AiResponse text(String t) {
		return new AiResponse(t, List.of());
	}

	private final List<String> replies = new ArrayList<>();
	private final List<List<ChatMessage>> finished = new ArrayList<>();

	private AgentLoop.Handle run(AiProvider provider, int maxSteps, FakeExecutor exec) {
		return new AgentLoop(provider, maxSteps, () -> true, 0, millis -> {}).start("sys", List.of(), "hello", exec,
				replies::add, finished::add, Runnable::run);
	}

	@Test
	void plainTextReplyEndsLoop() {
		Scripted provider = new Scripted(text("Yes, my king."));
		run(provider, 20, new FakeExecutor());
		assertEquals(List.of("Yes, my king."), replies);
		assertEquals(1, provider.calls());
		assertEquals(1, finished.size());
	}

	@Test
	void toolCallIsExecutedAndResultFedBack() {
		Scripted provider = new Scripted(tool("t1", "stop"), text("Done."));
		FakeExecutor exec = new FakeExecutor();
		exec.behavior = c -> CompletableFuture.completedFuture("ok: stopped");
		run(provider, 20, exec);
		assertEquals(1, exec.executed.size());
		List<ChatMessage> second = provider.seen.get(1);
		ChatMessage last = second.get(second.size() - 1);
		assertEquals(ChatMessage.Role.TOOL_RESULT, last.role());
		assertEquals("t1", last.toolCallId());
		assertEquals("ok: stopped", last.text());
		assertEquals(List.of("Done."), replies);
	}

	@Test
	void stopsAtStepCap() {
		Scripted provider = new Scripted(tool("t", "stop"));
		FakeExecutor exec = new FakeExecutor();
		run(provider, 4, exec);
		assertEquals(4, provider.calls());
		assertEquals(4, exec.executed.size());
	}

	@Test
	void invalidToolCallReturnsErrorResultToModel() {
		Scripted provider = new Scripted(tool("t1", "fly"), text("Sorry."));
		FakeExecutor exec = new FakeExecutor();
		exec.validator = c -> Optional.of("Unknown tool");
		run(provider, 20, exec);
		assertTrue(exec.executed.isEmpty());
		List<ChatMessage> second = provider.seen.get(1);
		assertTrue(second.get(second.size() - 1).text().contains("Unknown tool"));
	}

	@Test
	void providerFailureKeepsRetryingPatientlyAndNeverSaysCant() {
		Scripted provider = new Scripted(new AiException("down"));
		List<Long> waits = new ArrayList<>();
		new AgentLoop(provider, 20, () -> true, 1500, waits::add).start("sys", List.of(), "hi", new FakeExecutor(),
				replies::add, finished::add, Runnable::run);
		assertEquals(5, provider.calls());
		assertEquals(List.of(1500L, 3000L, 6000L, 12000L), waits);
		assertEquals(List.of(AgentLoop.WAITING, AgentLoop.STILL_THINKING), replies);
		for (String reply : replies) {
			assertFalse(reply.toLowerCase().contains("can't") || reply.toLowerCase().contains("cannot"), reply);
		}
		assertEquals(1, finished.size());
	}

	@Test
	void aVillagerThatRecoversAfterWaitingStillAnswers() {
		Scripted provider = new Scripted(new AiException("a"), new AiException("b"), new AiException("c"), text("Here."));
		new AgentLoop(provider, 20, () -> true, 1500, w -> {
		}).start("sys", List.of(), "hi", new FakeExecutor(), replies::add, finished::add, Runnable::run);
		assertEquals(List.of(AgentLoop.WAITING, "Here."), replies);
	}

	@Test
	void retryWaitsBeforeTheSecondAttempt() {
		Scripted provider = new Scripted(new AiException("busy"), text("Back."));
		List<Long> waits = new ArrayList<>();
		new AgentLoop(provider, 20, () -> true, 1500, waits::add).start("sys", List.of(), "hi", new FakeExecutor(),
				replies::add, finished::add, Runnable::run);
		assertEquals(List.of(1500L), waits);
		assertEquals(List.of("Back."), replies);
	}

	@Test
	void noWaitWhenTheFirstAttemptSucceeds() {
		Scripted provider = new Scripted(text("Fine."));
		List<Long> waits = new ArrayList<>();
		new AgentLoop(provider, 20, () -> true, 1500, waits::add).start("sys", List.of(), "hi", new FakeExecutor(),
				replies::add, finished::add, Runnable::run);
		assertTrue(waits.isEmpty());
	}

	@Test
	void providerRecoversOnRetry() {
		Scripted provider = new Scripted(new AiException("blip"), text("Back."));
		run(provider, 20, new FakeExecutor());
		assertEquals(List.of("Back."), replies);
	}

	@Test
	void emptyFirstResponseTellsTheKingInsteadOfStayingSilent() {
		Scripted provider = new Scripted(text(""));
		run(provider, 20, new FakeExecutor());
		assertEquals(List.of(AgentLoop.NO_ANSWER), replies);
		assertEquals(1, provider.calls());
		assertEquals(1, finished.size());
	}

	@Test
	void emptyResponseAfterActionsStaysSilent() {
		Scripted provider = new Scripted(tool("t1", "stop"), text(""));
		run(provider, 20, new FakeExecutor());
		assertTrue(replies.isEmpty());
		assertEquals(2, provider.calls());
	}

	@Test
	void unexpectedProviderExceptionIsHandledPatientlyInsteadOfDyingSilently() {
		AiProvider broken = (system, messages, tools) -> {
			throw new IllegalStateException("parse blew up");
		};
		new AgentLoop(broken, 20, () -> true, 1500, w -> {
		}).start("sys", List.of(), "hi", new FakeExecutor(), replies::add, finished::add, Runnable::run);
		assertEquals(List.of(AgentLoop.WAITING, AgentLoop.STILL_THINKING), replies);
		assertEquals(1, finished.size());
	}

	@Test
	void aDeniedCallPermitMakesTheVillagerWaitThenGiveAPoliteMessage() {
		Scripted provider = new Scripted(tool("t", "stop"));
		FakeExecutor exec = new FakeExecutor();
		int[] permits = {2};
		List<Long> waits = new ArrayList<>();
		new AgentLoop(provider, 20, () -> permits[0]-- > 0, 1500, waits::add).start("sys", List.of(), "go", exec,
				replies::add, finished::add, Runnable::run);
		assertEquals(2, provider.calls());
		assertEquals(12, waits.size());
		assertEquals(List.of(AgentLoop.WAITING, AgentLoop.STILL_THINKING), replies);
		assertEquals(1, finished.size());
	}

	@Test
	void aCallPermitThatOpensUpAfterWaitingLetsTheVillagerCarryOn() {
		Scripted provider = new Scripted(text("Done."));
		int[] denials = {3};
		new AgentLoop(provider, 20, () -> denials[0]-- <= 0, 1500, w -> {
		}).start("sys", List.of(), "go", new FakeExecutor(), replies::add, finished::add, Runnable::run);
		assertEquals(List.of(AgentLoop.WAITING, "Done."), replies);
	}

	@Test
	void executorFailureIsReportedToModelNotThrown() {
		Scripted provider = new Scripted(tool("t1", "go_to"), text("Could not."));
		FakeExecutor exec = new FakeExecutor();
		exec.behavior = c -> CompletableFuture.failedFuture(new IllegalStateException("no path"));
		run(provider, 20, exec);
		List<ChatMessage> second = provider.seen.get(1);
		ChatMessage last = second.get(second.size() - 1);
		assertTrue(last.text().startsWith("failed:"));
		assertTrue(last.text().contains("no path"));
		assertEquals(List.of("Could not."), replies);
	}

	@Test
	void deadVillagerEndsLoop() {
		Scripted provider = new Scripted(tool("t", "stop"));
		FakeExecutor exec = new FakeExecutor();
		exec.behavior = c -> {
			exec.alive = false;
			return CompletableFuture.completedFuture("ok");
		};
		run(provider, 20, exec);
		assertEquals(1, provider.calls());
		assertEquals(1, finished.size());
	}

	@Test
	void cancelStopsFurtherProviderCalls() throws Exception {
		Scripted provider = new Scripted(tool("t", "follow_me"), text("never"));
		FakeExecutor exec = new FakeExecutor();
		CountDownLatch started = new CountDownLatch(1);
		exec.behavior = c -> {
			started.countDown();
			return new CompletableFuture<>(); // never completes on its own
		};
		ExecutorService thread = Executors.newSingleThreadExecutor();
		CountDownLatch done = new CountDownLatch(1);
		AtomicBoolean replied = new AtomicBoolean();
		AgentLoop.Handle handle = new AgentLoop(provider, 20).start("sys", List.of(), "hi", exec,
				r -> replied.set(true), f -> done.countDown(), thread);
		assertTrue(started.await(5, TimeUnit.SECONDS));
		handle.cancel();
		assertTrue(done.await(5, TimeUnit.SECONDS));
		thread.shutdownNow();
		assertEquals(1, provider.calls());
		assertFalse(replied.get());
	}

	@Test
	void historyIsNotMutatedAndFinishedReturnsNewMessages() {
		List<ChatMessage> history = new ArrayList<>(List.of(ChatMessage.user("earlier")));
		Scripted provider = new Scripted(text("ok"));
		new AgentLoop(provider, 20).start("sys", history, "now", new FakeExecutor(), replies::add, finished::add,
				Runnable::run);
		assertEquals(1, history.size());
		assertEquals(2, provider.seen.get(0).size());
		assertEquals(2, finished.get(0).size()); // user "now" + assistant "ok"
		assertEquals("now", finished.get(0).get(0).text());
	}

	@Test
	void theToolTimeoutIsConfigurable() {
		Scripted provider = new Scripted(tool("t", "go_to"), text("Gave up."));
		FakeExecutor exec = new FakeExecutor();
		exec.behavior = c -> new CompletableFuture<>(); // never finishes
		new AgentLoop(provider, 20, () -> true, 0, millis -> {}).withToolTimeout(1).start("sys", List.of(), "go", exec,
				replies::add, finished::add, Runnable::run);
		List<ChatMessage> second = provider.seen.get(1);
		assertEquals("failed: timed out", second.get(second.size() - 1).text());
	}

	private static ToolCall sayCall() {
		JsonObject args = new JsonObject();
		args.addProperty("text", "At your service!");
		return new ToolCall("s1", "say", args);
	}

	@Test
	void aTalkOnlyResponseEndsTheTurnWithoutAnotherSlowModelCall() {
		Scripted provider = new Scripted(new AiResponse("", List.of(sayCall())), text("never asked"));
		FakeExecutor exec = new FakeExecutor();
		run(provider, 20, exec);
		assertEquals(1, provider.calls());
		assertEquals(1, exec.executed.size());
		assertEquals(1, finished.size());
	}

	@Test
	void speakingAndRenamingTogetherAlsoEndsTheTurn() {
		Scripted provider = new Scripted(new AiResponse("", List.of(call("r1", "rename"), sayCall())), text("never asked"));
		run(provider, 20, new FakeExecutor());
		assertEquals(1, provider.calls());
	}

	@Test
	void speakingWhileDoingRealWorkStillGetsAFollowUpCall() {
		Scripted provider = new Scripted(new AiResponse("", List.of(sayCall(), call("m1", "mine_block"))), text("Done mining."));
		run(provider, 20, new FakeExecutor());
		assertEquals(2, provider.calls());
		assertTrue(replies.contains("Done mining."));
	}

	@Test
	void aSlowModelGetsAThinkingMessageLongBeforeItAnswers() throws Exception {
		CountDownLatch release = new CountDownLatch(1);
		AiProvider slow = (system, messages, tools) -> {
			try {
				release.await(10, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return text("Finally, my king.");
		};
		List<String> spoken = new java.util.concurrent.CopyOnWriteArrayList<>();
		CountDownLatch done = new CountDownLatch(1);
		ExecutorService thread = Executors.newSingleThreadExecutor();
		new AgentLoop(slow, 20, () -> true, 0, millis -> {
		}).withAckDelay(150).start("sys", List.of(), "hi", new FakeExecutor(), spoken::add, f -> done.countDown(), thread);
		long deadline = System.currentTimeMillis() + 3000;
		while (spoken.isEmpty() && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}
		assertEquals(List.of(AgentLoop.WAITING), spoken); // said while the model had still not answered
		release.countDown();
		assertTrue(done.await(5, TimeUnit.SECONDS));
		thread.shutdownNow();
		assertEquals(List.of(AgentLoop.WAITING, "Finally, my king."), spoken);
	}

	@Test
	void aFastModelNeverTriggersTheThinkingMessage() {
		run(new Scripted(text("Quick.")), 20, new FakeExecutor());
		assertEquals(List.of("Quick."), replies);
	}

	@Test
	void aUsedUpQuotaIsReportedAtOnceInsteadOfRetriedForAMinute() {
		Scripted provider = new Scripted(new AiException("AI service returned HTTP 429: quota", 429));
		List<Long> waits = new ArrayList<>();
		new AgentLoop(provider, 20, () -> true, 1000, waits::add).start("sys", List.of(), "build a castle", new FakeExecutor(),
				replies::add, finished::add, Runnable::run);
		assertEquals(2, provider.calls()); // one quick second try, then the truth
		assertEquals(List.of(1000L), waits);
		assertEquals(List.of(AgentLoop.QUOTA_USED_UP), replies);
		assertTrue(AgentLoop.QUOTA_USED_UP.contains("/villaging key"));
		assertEquals(1, finished.size());
	}

	@Test
	void aRateLimitThatClearsOnTheSecondTryStillGetsAnAnswer() {
		Scripted provider = new Scripted(new AiException("HTTP 429", 429), text("Here, my king."));
		run(provider, 20, new FakeExecutor());
		assertEquals(List.of("Here, my king."), replies);
	}

	@Test
	void aRejectedKeyIsReportedAtOnceWithoutRetrying() {
		Scripted provider = new Scripted(new AiException("AI service returned HTTP 403: bad key", 403));
		run(provider, 20, new FakeExecutor());
		assertEquals(1, provider.calls());
		assertEquals(List.of(AgentLoop.KEY_REJECTED), replies);
	}

	@Test
	void theQuotaAndKeyMessagesNeverSayTheVillagerCannot() {
		for (String reply : List.of(AgentLoop.QUOTA_USED_UP, AgentLoop.KEY_REJECTED)) {
			assertFalse(reply.toLowerCase().contains("can't") || reply.toLowerCase().contains("cannot"), reply);
		}
	}

	@Test
	void cancelStopsWaitingForAHungModelAtOnce() throws Exception {
		CountDownLatch inCall = new CountDownLatch(1);
		AiProvider hung = (system, messages, tools) -> {
			inCall.countDown();
			try {
				Thread.sleep(60_000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return text("late");
		};
		CountDownLatch done = new CountDownLatch(1);
		ExecutorService thread = Executors.newSingleThreadExecutor();
		AgentLoop.Handle handle = new AgentLoop(hung, 20).start("sys", List.of(), "hi", new FakeExecutor(), replies::add,
				f -> done.countDown(), thread);
		assertTrue(inCall.await(5, TimeUnit.SECONDS));
		handle.cancel();
		assertTrue(done.await(5, TimeUnit.SECONDS));
		thread.shutdownNow();
	}
}
