package com.villaging.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.villaging.build.BuildJob.Pos;
import com.villaging.build.BuildJob.Status;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BuildJobTest {
	private static BuildJob job(Optional<Pos> stockpile, Status status) {
		return new BuildJob("stone_cottage", new Pos(10, 64, -5), 1, stockpile, 17, 312, status, "needs 12 stone");
	}

	private static BuildJob roundTrip(BuildJob job) {
		var json = BuildJob.CODEC.encodeStart(JsonOps.INSTANCE, job).result().orElseThrow();
		return BuildJob.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
	}

	@Test
	void jobCodecRoundTripsEveryFieldWithAndWithoutAStockpile() {
		BuildJob withChest = job(Optional.of(new Pos(12, 64, -5)), Status.PAUSED_MISSING_MATERIAL);
		assertEquals(withChest, roundTrip(withChest));
		BuildJob noChest = job(Optional.empty(), Status.PLANNED);
		assertEquals(noChest, roundTrip(noChest));
	}

	@Test
	void withDoneAndWithStatusReturnUpdatedCopies() {
		BuildJob j = job(Optional.empty(), Status.RUNNING);
		assertEquals(40, j.withDone(40).done());
		assertEquals(17, j.done());
		BuildJob paused = j.withStatus(Status.PAUSED_BLOCKED, "3 cells blocked");
		assertEquals(Status.PAUSED_BLOCKED, paused.status());
		assertEquals("3 cells blocked", paused.note());
		assertEquals(Status.RUNNING, j.status());
	}

	@Test
	void aJobSavedAsRunningIsPausedWhenReloadedAndOthersAreUntouched() {
		BuildJob reloaded = BuildJob.afterReload(job(Optional.empty(), Status.RUNNING));
		assertEquals(Status.PAUSED_BY_KING, reloaded.status());
		assertTrue(reloaded.note().toLowerCase().contains("start_build"));
		assertEquals(17, reloaded.done());
		BuildJob planned = job(Optional.empty(), Status.PLANNED);
		assertEquals(planned, BuildJob.afterReload(planned));
		BuildJob missing = job(Optional.empty(), Status.PAUSED_MISSING_MATERIAL);
		assertEquals(missing, BuildJob.afterReload(missing));
	}

	@Test
	void startableMeansPlannedOrPaused() {
		assertTrue(job(Optional.empty(), Status.PLANNED).canStart());
		assertTrue(job(Optional.empty(), Status.PAUSED_BY_KING).canStart());
		assertTrue(job(Optional.empty(), Status.PAUSED_MISSING_MATERIAL).canStart());
		assertTrue(!job(Optional.empty(), Status.RUNNING).canStart());
	}
}
