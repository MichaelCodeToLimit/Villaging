package com.villaging.ai;

import com.villaging.Villaging;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/**
 * Sends the king's message to the model, runs the tool calls it asks for, feeds the results back,
 * and repeats until the model stops asking for tools, the step cap is reached, the villager is gone,
 * or the loop is cancelled.
 */
public final class AgentLoop {
	/** Said once when the AI is slow or busy and the villager keeps trying. Never a refusal. */
	public static final String WAITING = "One moment, my king, I'm thinking it over.";
	/** Said only after the villager has waited and retried for a long while. Still not a refusal. */
	public static final String STILL_THINKING = "I'm still gathering my thoughts, my king. Say it once more in a moment and I'll get right on it.";
	public static final String NO_ANSWER = "I'm not sure what to say, my king.";
	/** Tries per model call: the first, then four more after growing waits. */
	private static final int MAX_ATTEMPTS = 5;
	private static final long[] RETRY_WAIT_FACTORS = {1, 3, 8, 20};
	/** When the call limit is reached the villager waits for a slot this many times before giving up politely. */
	private static final int MAX_PERMIT_WAITS = 12;
	private static final long PERMIT_WAIT_FACTOR = 4;
	private static final long DEFAULT_TOOL_TIMEOUT_SECONDS = 120;
	private static final long RETRY_DELAY_MILLIS = 1500;

	public interface Handle {
		void cancel();
	}

	private final AiProvider provider;
	private final int maxSteps;
	private final BooleanSupplier callPermit;
	private final long retryDelayMillis;
	private final LongConsumer sleeper;
	private long toolTimeoutSeconds = DEFAULT_TOOL_TIMEOUT_SECONDS;

	public AgentLoop(AiProvider provider, int maxSteps) {
		this(provider, maxSteps, () -> true);
	}

	/** @param callPermit asked before every model call (including the retry); false stops the loop politely */
	public AgentLoop(AiProvider provider, int maxSteps, BooleanSupplier callPermit) {
		this(provider, maxSteps, callPermit, RETRY_DELAY_MILLIS, AgentLoop::sleep);
	}

	/** @param retryDelayMillis how long to wait before the one retry (overloaded services need a moment) */
	public AgentLoop(AiProvider provider, int maxSteps, BooleanSupplier callPermit, long retryDelayMillis,
			LongConsumer sleeper) {
		this.provider = provider;
		this.maxSteps = maxSteps;
		this.callPermit = callPermit;
		this.retryDelayMillis = retryDelayMillis;
		this.sleeper = sleeper;
	}

	/** How long one tool call may take before the loop gives up on it; long jobs need more than the default 120 s. */
	public AgentLoop withToolTimeout(long seconds) {
		this.toolTimeoutSeconds = seconds;
		return this;
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * @param onReply called with each non-blank assistant text (and a short apology on failure)
	 * @param onFinished called exactly once with the messages produced by this run (user message first)
	 * @param runOn executor the loop body runs on; never the main game thread
	 */
	public Handle start(String system, List<ChatMessage> history, String userMessage, ActionExecutor exec,
			Consumer<String> onReply, Consumer<List<ChatMessage>> onFinished, Executor runOn) {
		Run run = new Run(system, List.copyOf(history), userMessage, exec, onReply, onFinished);
		runOn.execute(run::loop);
		return run;
	}

	private final class Run implements Handle {
		private final String system;
		private final List<ChatMessage> history;
		private final String userMessage;
		private final ActionExecutor exec;
		private final Consumer<String> onReply;
		private final Consumer<List<ChatMessage>> onFinished;
		private volatile boolean cancelled;
		private volatile CompletableFuture<String> pending;
		private boolean waitingSaid;
		private boolean acted;

		Run(String system, List<ChatMessage> history, String userMessage, ActionExecutor exec,
				Consumer<String> onReply, Consumer<List<ChatMessage>> onFinished) {
			this.system = system;
			this.history = history;
			this.userMessage = userMessage;
			this.exec = exec;
			this.onReply = onReply;
			this.onFinished = onFinished;
		}

		@Override
		public void cancel() {
			cancelled = true;
			CompletableFuture<String> p = pending;
			if (p != null) {
				p.cancel(true);
			}
		}

		void loop() {
			List<ChatMessage> produced = new ArrayList<>();
			produced.add(ChatMessage.user(userMessage));
			try {
				for (int step = 0; step < maxSteps && !cancelled && exec.isAlive(); step++) {
					AiResponse response = callProvider(produced);
					if (cancelled) {
						break;
					}
					if (response == null) {
						reply(STILL_THINKING);
						break;
					}
					if (!response.text().isBlank()) {
						reply(response.text());
					}
					if (response.text().isBlank() && response.toolCalls().isEmpty()) {
						if (!acted) {
							reply(NO_ANSWER);
						}
						break;
					}
					produced.add(ChatMessage.assistant(response.text(), response.toolCalls()));
					if (response.toolCalls().isEmpty()) {
						break;
					}
					for (ToolCall call : response.toolCalls()) {
						String result = runTool(call);
						acted = true;
						Villaging.LOGGER.info("Villager tool {} {} -> {}", call.name(), call.args(), result);
						produced.add(ChatMessage.toolResult(call.id(), result));
					}
				}
			} catch (RuntimeException e) {
				Villaging.LOGGER.error("Villager agent loop crashed", e);
				if (!cancelled) {
					reply(STILL_THINKING);
				}
			} finally {
				onFinished.accept(List.copyOf(produced));
			}
		}

		private void reply(String text) {
			acted = true;
			onReply.accept(text);
		}

		/** Waits (a few times) for the call limit to open up; false when it never does or the run was cancelled. */
		private boolean awaitPermit() {
			for (int waits = 0; !callPermit.getAsBoolean(); waits++) {
				if (cancelled || waits >= MAX_PERMIT_WAITS) {
					return false;
				}
				if (waits == 0) {
					announceWaiting();
				}
				sleeper.accept(retryDelayMillis * PERMIT_WAIT_FACTOR);
			}
			return true;
		}

		private void announceWaiting() {
			if (!waitingSaid) {
				waitingSaid = true;
				reply(WAITING);
			}
		}

		/**
		 * Asks the model, patiently: a busy or failing AI is retried after growing waits instead of giving up, and the
		 * king is told once that the villager is thinking it over. Null only after all that, or when cancelled.
		 */
		private AiResponse callProvider(List<ChatMessage> produced) {
			List<ChatMessage> messages = new ArrayList<>(history);
			messages.addAll(produced);
			for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
				if (!awaitPermit()) {
					return null;
				}
				try {
					return provider.complete(system, messages, Tools.all());
				} catch (AiException | RuntimeException e) {
					Villaging.LOGGER.warn("AI call failed (attempt {}): {}", attempt + 1, e.getMessage());
				}
				if (cancelled || attempt == MAX_ATTEMPTS - 1) {
					return null;
				}
				if (attempt == 1) {
					announceWaiting();
				}
				sleeper.accept(retryDelayMillis * RETRY_WAIT_FACTORS[attempt]);
			}
			return null;
		}

		private String runTool(ToolCall call) {
			if (cancelled) {
				return "failed: cancelled";
			}
			Optional<String> error = exec.validate(call);
			if (error.isPresent()) {
				return "error: " + error.get();
			}
			CompletableFuture<String> future = exec.execute(call);
			pending = future;
			if (cancelled) {
				future.cancel(true);
			}
			try {
				return future.get(toolTimeoutSeconds, TimeUnit.SECONDS);
			} catch (CancellationException e) {
				return "failed: cancelled";
			} catch (ExecutionException e) {
				Throwable cause = e.getCause() == null ? e : e.getCause();
				return "failed: " + cause.getMessage();
			} catch (TimeoutException e) {
				future.cancel(true);
				return "failed: timed out";
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				cancelled = true;
				return "failed: interrupted";
			} finally {
				pending = null;
			}
		}
	}
}
