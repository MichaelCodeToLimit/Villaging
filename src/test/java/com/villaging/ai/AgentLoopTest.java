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
		return new AgentLoop(provider, maxSteps).start("sys", List.of(), "hello", exec, replies::add, finished::add,
				Runnable::run);
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
	void providerFailureRetriesOnceThenSaysCantThink() {
		Scripted provider = new Scripted(new AiException("down"));
		run(provider, 20, new FakeExecutor());
		assertEquals(2, provider.calls());
		assertEquals(List.of(AgentLoop.CANT_THINK), replies);
		assertEquals(1, finished.size());
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
	void unexpectedProviderExceptionSaysCantThinkInsteadOfDyingSilently() {
		AiProvider broken = (system, messages, tools) -> {
			throw new IllegalStateException("parse blew up");
		};
		run(broken, 20, new FakeExecutor());
		assertEquals(List.of(AgentLoop.CANT_THINK), replies);
		assertEquals(1, finished.size());
	}

	@Test
	void callPermitDeniedStopsTheLoopWithAPoliteMessage() {
		Scripted provider = new Scripted(tool("t", "stop"));
		FakeExecutor exec = new FakeExecutor();
		int[] permits = {2};
		new AgentLoop(provider, 20, () -> permits[0]-- > 0).start("sys", List.of(), "go", exec, replies::add,
				finished::add, Runnable::run);
		assertEquals(2, provider.calls());
		assertEquals(List.of(AgentLoop.TOO_BUSY), replies);
		assertEquals(1, finished.size());
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
}
