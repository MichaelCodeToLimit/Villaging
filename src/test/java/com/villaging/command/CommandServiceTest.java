package com.villaging.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.villaging.ai.AgentLoop;
import com.villaging.command.CommandService.CommandResult;
import com.villaging.command.CommandService.VillagerStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CommandServiceTest {
	private static final class FakeBackend implements CommandService.Backend {
		UUID king = UUID.randomUUID();
		VillagerStatus status = VillagerStatus.OK;
		String configProblem = "";
		int lastMaxDistance;
		final List<String> started = new ArrayList<>();
		final List<AtomicInteger> cancelCounts = new ArrayList<>();

		@Override
		public boolean isKing(UUID player) {
			return player.equals(king);
		}

		@Override
		public VillagerStatus villagerStatus(UUID player, int villagerId, int maxDistance) {
			lastMaxDistance = maxDistance;
			return status;
		}

		@Override
		public String configProblem() {
			return configProblem;
		}

		@Override
		public AgentLoop.Handle startLoop(UUID player, int villagerId, String message) {
			started.add(villagerId + ":" + message);
			AtomicInteger cancels = new AtomicInteger();
			cancelCounts.add(cancels);
			return cancels::incrementAndGet;
		}
	}

	private final AtomicLong clock = new AtomicLong();
	private final FakeBackend backend = new FakeBackend();
	private final CommandService service = new CommandService(backend, 10, clock::get);

	@Test
	void kingCommandIsAccepted() {
		assertEquals(CommandResult.ACCEPTED, service.handle(backend.king, 7, "follow me"));
		assertEquals(List.of("7:follow me"), backend.started);
	}

	@Test
	void anAnnouncementCanReachFartherThanASpokenOrder() {
		assertEquals(CommandResult.ACCEPTED, service.handle(backend.king, 7, "everyone gather", 64));
		assertEquals(64, backend.lastMaxDistance);
		service.handle(backend.king, 8, "follow me");
		assertEquals(CommandService.MAX_DISTANCE, backend.lastMaxDistance);
	}

	@Test
	void cancellingAHelperStopsItsRunningLoopOnly() {
		service.handle(backend.king, 7, "mine");
		service.handle(backend.king, 8, "chop");
		service.cancel(7);
		assertEquals(1, backend.cancelCounts.get(0).get());
		assertEquals(0, backend.cancelCounts.get(1).get());
	}

	@Test
	void nonKingRejectedAndNothingStarted() {
		assertEquals(CommandResult.REJECTED_NOT_KING, service.handle(UUID.randomUUID(), 7, "follow me"));
		assertTrue(backend.started.isEmpty());
	}

	@Test
	void nonSmartVillagerRejected() {
		backend.status = VillagerStatus.NOT_SMART;
		assertEquals(CommandResult.REJECTED_NOT_SMART, service.handle(backend.king, 7, "hi"));
		assertTrue(backend.started.isEmpty());
	}

	@Test
	void tooFarRejected() {
		backend.status = VillagerStatus.TOO_FAR;
		assertEquals(CommandResult.REJECTED_TOO_FAR, service.handle(backend.king, 7, "hi"));
	}

	@Test
	void blankAndOverlongMessagesRejected() {
		assertEquals(CommandResult.REJECTED_BAD_MESSAGE, service.handle(backend.king, 7, ""));
		assertEquals(CommandResult.REJECTED_BAD_MESSAGE, service.handle(backend.king, 7, "   "));
		assertEquals(CommandResult.REJECTED_BAD_MESSAGE, service.handle(backend.king, 7, null));
		assertEquals(CommandResult.REJECTED_BAD_MESSAGE, service.handle(backend.king, 7, "a".repeat(501)));
		assertEquals(CommandResult.ACCEPTED, service.handle(backend.king, 7, "a".repeat(500)));
	}

	@Test
	void secondCommandCancelsFirstForSameVillager() {
		service.handle(backend.king, 7, "one");
		service.handle(backend.king, 7, "two");
		assertEquals(1, backend.cancelCounts.get(0).get());
		assertEquals(0, backend.cancelCounts.get(1).get());
	}

	@Test
	void commandsToDifferentVillagersDoNotCancelEachOther() {
		service.handle(backend.king, 7, "one");
		service.handle(backend.king, 8, "two");
		assertEquals(0, backend.cancelCounts.get(0).get());
	}

	@Test
	void badConfigReturnsDisabledAndStartsNothing() {
		backend.configProblem = "Set \"apiKey\"";
		assertEquals(CommandResult.DISABLED_BAD_CONFIG, service.handle(backend.king, 7, "hi"));
		assertTrue(backend.started.isEmpty());
	}

	@Test
	void rateLimitedRejectedPerVillager() {
		for (int i = 0; i < 10; i++) {
			assertEquals(CommandResult.ACCEPTED, service.handle(backend.king, 7, "m" + i));
		}
		assertEquals(CommandResult.REJECTED_RATE_LIMITED, service.handle(backend.king, 7, "too many"));
		assertEquals(CommandResult.ACCEPTED, service.handle(backend.king, 8, "other villager is fine"));
	}

	@Test
	void cancelAllCancelsRunningLoops() {
		service.handle(backend.king, 7, "one");
		service.cancelAll();
		assertEquals(1, backend.cancelCounts.get(0).get());
	}
}
