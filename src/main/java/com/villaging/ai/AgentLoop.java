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

/**
 * Sends the king's message to the model, runs the tool calls it asks for, feeds the results back,
 * and repeats until the model stops asking for tools, the step cap is reached, the villager is gone,
 * or the loop is cancelled.
 */
public final class AgentLoop {
	public static final String CANT_THINK = "I can't think right now.";
	public static final String NO_ANSWER = "I'm not sure what to say, my king.";
	public static final String TOO_BUSY = "Give me a moment to catch my breath.";
	private static final long ACTION_TIMEOUT_SECONDS = 120;

	public interface Handle {
		void cancel();
	}

	private final AiProvider provider;
	private final int maxSteps;
	private final BooleanSupplier callPermit;

	public AgentLoop(AiProvider provider, int maxSteps) {
		this(provider, maxSteps, () -> true);
	}

	/** @param callPermit asked before every model call (including the retry); false stops the loop politely */
	public AgentLoop(AiProvider provider, int maxSteps, BooleanSupplier callPermit) {
		this.provider = provider;
		this.maxSteps = maxSteps;
		this.callPermit = callPermit;
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
		private boolean denied;
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
						reply(denied ? TOO_BUSY : CANT_THINK);
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
					reply(CANT_THINK);
				}
			} finally {
				onFinished.accept(List.copyOf(produced));
			}
		}

		private void reply(String text) {
			acted = true;
			onReply.accept(text);
		}

		private AiResponse callProvider(List<ChatMessage> produced) {
			List<ChatMessage> messages = new ArrayList<>(history);
			messages.addAll(produced);
			for (int attempt = 0; attempt < 2; attempt++) {
				if (!callPermit.getAsBoolean()) {
					denied = true;
					return null;
				}
				try {
					return provider.complete(system, messages, Tools.all());
				} catch (AiException | RuntimeException e) {
					Villaging.LOGGER.warn("AI call failed (attempt {}): {}", attempt + 1, e.getMessage());
				}
				if (cancelled) {
					return null;
				}
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
				return future.get(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
