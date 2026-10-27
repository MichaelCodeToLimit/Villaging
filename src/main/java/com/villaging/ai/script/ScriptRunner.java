package com.villaging.ai.script;

import com.google.gson.JsonObject;
import com.villaging.ai.ActionExecutor;
import com.villaging.ai.ToolCall;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * Runs a {@link Script} with no further model calls. Every tool call is validated and executed through the same
 * {@link ActionExecutor} the model's own calls use, so every safety rule (protection, risky-act questions) still holds.
 * The run ends at the first failure, at the step or time limit, when cancelled, or when the villager is gone.
 */
public final class ScriptRunner {
	private static final long POLL_MILLIS = 50;

	private final ActionExecutor executor;
	private final Facts facts;
	private final int maxWork;
	private final long maxNanos;
	private final LongSupplier clock;
	private final BooleanSupplier cancelled;
	private final long start;
	private int calls;
	private int work;

	/** Thrown inside the runner to end the run with a message. */
	private static final class Stop extends RuntimeException {
		Stop(String message) {
			super(message, null, false, false);
		}
	}

	private ScriptRunner(ActionExecutor executor, Facts facts, int maxWork, long maxNanos, LongSupplier clock,
			BooleanSupplier cancelled) {
		this.executor = executor;
		this.facts = facts;
		this.maxWork = maxWork;
		this.maxNanos = maxNanos;
		this.clock = clock;
		this.cancelled = cancelled;
		this.start = clock.getAsLong();
	}

	/**
	 * @param maxSteps limit on tool calls plus loop rounds, so even a loop that does nothing ends
	 * @return {@code ok: ran N steps}, {@code failed: step N tool: reason} or {@code stopped: reason}
	 */
	public static String run(Script script, ActionExecutor executor, Facts facts, int maxSteps, long maxNanos, LongSupplier clock,
			BooleanSupplier cancelled) {
		ScriptRunner runner = new ScriptRunner(executor, facts, maxSteps, maxNanos, clock, cancelled);
		try {
			runner.steps(script.steps());
			return "ok: ran " + runner.calls + " step" + (runner.calls == 1 ? "" : "s");
		} catch (Stop stop) {
			return stop.getMessage();
		}
	}

	private void steps(List<Step> steps) {
		for (Step step : steps) {
			switch (step) {
				case Step.Call call -> call(call);
				case Step.Repeat repeat -> {
					for (int i = 0; i < repeat.times(); i++) {
						round();
						steps(repeat.body());
					}
				}
				case Step.Until until -> {
					while (!until.condition().test(facts)) {
						round();
						steps(until.body());
					}
				}
				case Step.If branch -> steps(branch.condition().test(facts) ? branch.then() : branch.orElse());
			}
		}
	}

	/** One round of a loop counts as work, so a loop with an empty body cannot spin forever. */
	private void round() {
		checkLimits();
		work++;
	}

	private void checkLimits() {
		if (cancelled.getAsBoolean()) {
			throw new Stop("stopped: the script was cancelled after " + calls + " step" + (calls == 1 ? "" : "s"));
		}
		if (!executor.isAlive()) {
			throw new Stop("stopped: I can't continue the script");
		}
		if (work >= maxWork) {
			throw new Stop("stopped: reached the limit of " + maxWork + " steps");
		}
		if (clock.getAsLong() - start > maxNanos) {
			throw new Stop("stopped: ran out of time after " + calls + " step" + (calls == 1 ? "" : "s"));
		}
	}

	private void call(Step.Call step) {
		checkLimits();
		work++;
		calls++;
		JsonObject args = step.args().deepCopy();
		ToolCall call = new ToolCall("script-" + calls, step.tool(), args);
		Optional<String> problem = executor.validate(call);
		if (problem.isPresent()) {
			throw new Stop("failed: step " + calls + " " + step.tool() + ": invalid: " + problem.get());
		}
		CompletableFuture<String> future = executor.execute(call);
		String result;
		while (true) {
			try {
				result = future.get(POLL_MILLIS, TimeUnit.MILLISECONDS);
				break;
			} catch (TimeoutException waiting) {
				if (cancelled.getAsBoolean() || clock.getAsLong() - start > maxNanos || !executor.isAlive()) {
					future.cancel(true);
					checkLimits();
					throw new Stop("stopped: the script was interrupted during step " + calls);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				future.cancel(true);
				throw new Stop("stopped: the script was interrupted");
			} catch (ExecutionException e) {
				throw new Stop("failed: step " + calls + " " + step.tool() + ": " + (e.getCause() == null ? e : e.getCause()).getMessage());
			} catch (java.util.concurrent.CancellationException e) {
				throw new Stop("stopped: step " + calls + " was cancelled");
			}
		}
		if (result.startsWith("failed")) {
			String reason = result.startsWith("failed:") ? result.substring("failed:".length()).trim() : result;
			throw new Stop("failed: step " + calls + " " + step.tool() + ": " + reason);
		}
	}
}
