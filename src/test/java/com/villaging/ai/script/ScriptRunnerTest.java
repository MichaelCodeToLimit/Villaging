package com.villaging.ai.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villaging.ai.ActionExecutor;
import com.villaging.ai.ToolCall;
import com.villaging.ai.tools.AbilityTools;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ScriptRunnerTest {
	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static final class FakeExecutor implements ActionExecutor {
		final List<String> ran = new ArrayList<>();
		Function<ToolCall, Optional<String>> validator = c -> Optional.empty();
		Function<ToolCall, String> result = c -> "ok";
		boolean alive = true;

		@Override
		public Optional<String> validate(ToolCall call) {
			return validator.apply(call);
		}

		@Override
		public CompletableFuture<String> execute(ToolCall call) {
			ran.add(call.name());
			return CompletableFuture.completedFuture(result.apply(call));
		}

		@Override
		public boolean isAlive() {
			return alive;
		}
	}

	private static final class FakeFacts implements Facts {
		int logs;
		boolean night;
		boolean block;
		double health = 20;

		@Override
		public int count(String itemId) {
			return logs;
		}

		@Override
		public boolean hasBlock(int x, int y, int z) {
			return block;
		}

		@Override
		public double health() {
			return health;
		}

		@Override
		public boolean isNight() {
			return night;
		}
	}

	private static Script script(String text) {
		JsonObject json = JsonParser.parseString(text).getAsJsonObject();
		return ScriptParser.parse(json, 3);
	}

	private static String run(Script script, FakeExecutor exec, FakeFacts facts, int maxSteps) {
		return ScriptRunner.run(script, exec, facts, maxSteps, Long.MAX_VALUE, () -> 0L, () -> false);
	}

	@Test
	void stepsRunInOrder() {
		FakeExecutor exec = new FakeExecutor();
		String result = run(script("{\"steps\":[{\"tool\":\"follow_me\"},{\"tool\":\"stop\"}]}"), exec, new FakeFacts(), 50);
		assertEquals(List.of("follow_me", "stop"), exec.ran);
		assertTrue(result.startsWith("ok"), result);
		assertTrue(result.contains("2"), result);
	}

	@Test
	void repeatRunsTheBodyThatManyTimes() {
		FakeExecutor exec = new FakeExecutor();
		run(script("{\"steps\":[{\"repeat\":3,\"steps\":[{\"tool\":\"stop\"}]}]}"), exec, new FakeFacts(), 50);
		assertEquals(3, exec.ran.size());
	}

	@Test
	void untilStopsAsSoonAsTheFactIsTrueAndCountsAgainstTheStepLimit() {
		FakeExecutor exec = new FakeExecutor();
		FakeFacts facts = new FakeFacts();
		exec.result = c -> {
			facts.logs++;
			return "ok";
		};
		String body = "{\"steps\":[{\"until\":{\"have\":{\"item\":\"minecraft:oak_log\",\"count\":4}},\"steps\":[{\"tool\":\"stop\"}]}]}";
		String result = run(script(body), exec, facts, 50);
		assertEquals(4, exec.ran.size());
		assertTrue(result.startsWith("ok"), result);
		// A condition that never becomes true is stopped by the step limit.
		FakeExecutor never = new FakeExecutor();
		String limited = run(script(body), never, new FakeFacts(), 10);
		assertTrue(limited.startsWith("stopped") && limited.contains("10"), limited);
		assertTrue(never.ran.size() <= 10);
	}

	@Test
	void ifTakesTheMatchingBranch() {
		FakeFacts facts = new FakeFacts();
		String text = "{\"steps\":[{\"if\":{\"night\":true},\"then\":[{\"tool\":\"sit\"}],\"else\":[{\"tool\":\"follow_me\"}]}]}";
		FakeExecutor day = new FakeExecutor();
		run(script(text), day, facts, 50);
		assertEquals(List.of("follow_me"), day.ran);
		facts.night = true;
		FakeExecutor night = new FakeExecutor();
		run(script(text), night, facts, 50);
		assertEquals(List.of("sit"), night.ran);
	}

	@Test
	void theFirstFailureStopsTheRunAndNamesTheStep() {
		FakeExecutor exec = new FakeExecutor();
		exec.result = c -> c.name().equals("sit") ? "failed: no room" : "ok";
		String result = run(script("{\"steps\":[{\"tool\":\"follow_me\"},{\"tool\":\"sit\"},{\"tool\":\"stop\"}]}"), exec, new FakeFacts(), 50);
		assertEquals(List.of("follow_me", "sit"), exec.ran);
		assertTrue(result.startsWith("failed: step 2 sit"), result);
		assertTrue(result.contains("no room"), result);
	}

	@Test
	void aStepTheExecutorRejectsIsNeverRun() {
		FakeExecutor exec = new FakeExecutor();
		exec.validator = c -> c.name().equals("sit") ? Optional.of("not allowed") : Optional.empty();
		String result = run(script("{\"steps\":[{\"tool\":\"follow_me\"},{\"tool\":\"sit\"}]}"), exec, new FakeFacts(), 50);
		assertEquals(List.of("follow_me"), exec.ran);
		assertTrue(result.startsWith("failed: step 2 sit") && result.contains("not allowed"), result);
	}

	@Test
	void theTimeLimitStopsTheRun() {
		FakeExecutor exec = new FakeExecutor();
		long[] now = {0};
		exec.result = c -> {
			now[0] += 1_000_000_000L;
			return "ok";
		};
		String result = ScriptRunner.run(script("{\"steps\":[{\"repeat\":50,\"steps\":[{\"tool\":\"stop\"}]}]}"), exec, new FakeFacts(),
				500, 3_000_000_000L, () -> now[0], () -> false);
		assertTrue(result.startsWith("stopped") && result.contains("time"), result);
		assertTrue(exec.ran.size() <= 4, "ran " + exec.ran.size());
	}

	@Test
	void cancellingStopsAtOnce() {
		FakeExecutor exec = new FakeExecutor();
		boolean[] cancelled = {false};
		exec.result = c -> {
			cancelled[0] = true;
			return "ok";
		};
		String result = ScriptRunner.run(script("{\"steps\":[{\"repeat\":10,\"steps\":[{\"tool\":\"stop\"}]}]}"), exec, new FakeFacts(),
				500, Long.MAX_VALUE, () -> 0L, () -> cancelled[0]);
		assertEquals(1, exec.ran.size());
		assertTrue(result.startsWith("stopped"), result);
	}

	@Test
	void aDeadVillagerStopsTheRun() {
		FakeExecutor exec = new FakeExecutor();
		exec.alive = false;
		String result = run(script("{\"steps\":[{\"tool\":\"stop\"}]}"), exec, new FakeFacts(), 50);
		assertTrue(exec.ran.isEmpty());
		assertTrue(result.startsWith("stopped"), result);
	}
}
