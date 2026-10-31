package com.villaging.ai;

import java.util.List;
import java.util.function.LongSupplier;

/**
 * Asks the main AI and, when it fails (quota used up, overloaded, unreachable, key rejected), asks the backup AI
 * instead. A main AI that failed rests for a minute so every message is not delayed by a call that is bound to fail.
 * If the backup fails too, the main AI's problem is reported, because that is the one the king can act on.
 */
public final class FallbackProvider implements AiProvider {
	private static final long REST_NANOS = 60_000_000_000L;

	private final AiProvider main;
	private final AiProvider backup;
	private final LongSupplier nanoClock;
	private volatile long restingUntil;
	private volatile AiException lastMainFailure;

	public FallbackProvider(AiProvider main, AiProvider backup, LongSupplier nanoClock) {
		this.main = main;
		this.backup = backup;
		this.nanoClock = nanoClock;
	}

	@Override
	public AiResponse complete(String system, List<ChatMessage> messages, List<ToolDef> tools) throws AiException {
		AiException mainFailure = lastMainFailure;
		if (mainFailure != null && nanoClock.getAsLong() - restingUntil < 0) {
			return askBackup(system, messages, tools, mainFailure);
		}
		try {
			return main.complete(system, messages, tools);
		} catch (AiException e) {
			if (restsAfter(e)) {
				restingUntil = nanoClock.getAsLong() + REST_NANOS;
				lastMainFailure = e;
			}
			return askBackup(system, messages, tools, e);
		}
	}

	private AiResponse askBackup(String system, List<ChatMessage> messages, List<ToolDef> tools, AiException mainFailure)
			throws AiException {
		try {
			return backup.complete(system, messages, tools);
		} catch (AiException | RuntimeException e) {
			throw mainFailure;
		}
	}

	/** A request the service called malformed says nothing about whether the service is healthy. */
	private static boolean restsAfter(AiException e) {
		int status = e.status();
		return status != 400 && status != 404 && status != 422;
	}
}
