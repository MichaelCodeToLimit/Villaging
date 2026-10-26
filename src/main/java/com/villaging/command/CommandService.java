package com.villaging.command;

import com.villaging.ai.AgentLoop;
import com.villaging.ai.RateLimiter;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Validates a chat command from a player and, if it is acceptable, cancels the villager's previous
 * plan and starts a new one. Knows nothing about Minecraft; the game layer supplies a {@link Backend}.
 */
public final class CommandService {
	public static final int MAX_MESSAGE_LENGTH = 500;
	public static final int MAX_DISTANCE = 16;

	public enum CommandResult {
		ACCEPTED, REJECTED_NOT_KING, REJECTED_NOT_SMART, REJECTED_TOO_FAR, REJECTED_BAD_MESSAGE,
		REJECTED_RATE_LIMITED, DISABLED_BAD_CONFIG
	}

	public enum VillagerStatus { OK, NOT_SMART, TOO_FAR }

	public interface Backend {
		boolean isKing(UUID player);

		VillagerStatus villagerStatus(UUID player, int villagerId, int maxDistance);

		/** Empty string when the AI config is usable, otherwise a fix hint for the king. */
		String configProblem();

		AgentLoop.Handle startLoop(UUID player, int villagerId, String message);
	}

	private final Backend backend;
	private final int callsPerMinute;
	private final LongSupplier nanoClock;
	private final Map<Integer, AgentLoop.Handle> running = new ConcurrentHashMap<>();
	private final Map<Integer, RateLimiter> limiters = new ConcurrentHashMap<>();

	public CommandService(Backend backend, int callsPerMinute, LongSupplier nanoClock) {
		this.backend = backend;
		this.callsPerMinute = callsPerMinute;
		this.nanoClock = nanoClock;
	}

	public CommandResult handle(UUID player, int villagerId, String message) {
		if (!backend.isKing(player)) {
			return CommandResult.REJECTED_NOT_KING;
		}
		switch (backend.villagerStatus(player, villagerId, MAX_DISTANCE)) {
			case NOT_SMART -> {
				return CommandResult.REJECTED_NOT_SMART;
			}
			case TOO_FAR -> {
				return CommandResult.REJECTED_TOO_FAR;
			}
			case OK -> {
			}
		}
		if (message == null || message.isBlank() || message.length() > MAX_MESSAGE_LENGTH) {
			return CommandResult.REJECTED_BAD_MESSAGE;
		}
		if (!backend.configProblem().isEmpty()) {
			return CommandResult.DISABLED_BAD_CONFIG;
		}
		RateLimiter limiter = limiters.computeIfAbsent(villagerId, id -> new RateLimiter(callsPerMinute, nanoClock));
		if (!limiter.tryAcquire()) {
			return CommandResult.REJECTED_RATE_LIMITED;
		}
		AgentLoop.Handle previous = running.put(villagerId, backend.startLoop(player, villagerId, message));
		if (previous != null) {
			previous.cancel();
		}
		return CommandResult.ACCEPTED;
	}

	public void cancelAll() {
		for (AgentLoop.Handle handle : new ArrayList<>(running.values())) {
			handle.cancel();
		}
		running.clear();
	}
}
