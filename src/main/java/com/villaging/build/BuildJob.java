package com.villaging.build;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;

/**
 * One helper's build, saved with the helper: which blueprint, where, how far it got and why it stopped. Plain numbers and
 * text only, so it can be tested without the game. {@code rotation} is in quarter turns (0..3, clockwise from above).
 */
public record BuildJob(String blueprint, Pos anchor, int rotation, Optional<Pos> stockpile, int done, int total, Status status,
		String note) {
	public enum Status { PLANNED, RUNNING, PAUSED_MISSING_MATERIAL, PAUSED_BLOCKED, PAUSED_BY_KING }

	public record Pos(int x, int y, int z) {
		public static final Codec<Pos> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("x").forGetter(Pos::x),
				Codec.INT.fieldOf("y").forGetter(Pos::y),
				Codec.INT.fieldOf("z").forGetter(Pos::z)).apply(i, Pos::new));
	}

	public static final Codec<BuildJob> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("blueprint").forGetter(BuildJob::blueprint),
			Pos.CODEC.fieldOf("anchor").forGetter(BuildJob::anchor),
			Codec.INT.fieldOf("rotation").forGetter(BuildJob::rotation),
			Pos.CODEC.optionalFieldOf("stockpile").forGetter(BuildJob::stockpile),
			Codec.INT.fieldOf("done").forGetter(BuildJob::done),
			Codec.INT.fieldOf("total").forGetter(BuildJob::total),
			Codec.STRING.xmap(Status::valueOf, Status::name).fieldOf("status").forGetter(BuildJob::status),
			Codec.STRING.optionalFieldOf("note", "").forGetter(BuildJob::note)).apply(i, BuildJob::new));

	public BuildJob withDone(int newDone) {
		return new BuildJob(blueprint, anchor, rotation, stockpile, newDone, total, status, note);
	}

	public BuildJob withStatus(Status newStatus, String newNote) {
		return new BuildJob(blueprint, anchor, rotation, stockpile, done, total, newStatus, newNote);
	}

	/** True when {@code start_build} may begin or carry on with this job. */
	public boolean canStart() {
		return status != Status.RUNNING;
	}

	/** A job that was running when the world was saved never resumes by itself; it waits for the king. */
	public static BuildJob afterReload(BuildJob job) {
		if (job.status != Status.RUNNING) {
			return job;
		}
		return job.withStatus(Status.PAUSED_BY_KING, "The world was reloaded; say start_build to carry on.");
	}
}
