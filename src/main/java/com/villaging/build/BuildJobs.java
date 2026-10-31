package com.villaging.build;

import com.villaging.Villaging;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.PathfinderMob;

/** Registers and accesses the persisted {@link BuildJob} attached to a helper. */
public final class BuildJobs {
	public static final AttachmentType<BuildJob> ATTACHMENT = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "build_job"), BuildJob.CODEC);

	private BuildJobs() {
	}

	/** Forces class initialisation so the attachment is registered during mod init. */
	public static void init() {
	}

	public static Optional<BuildJob> get(PathfinderMob helper) {
		return Optional.ofNullable(helper.getAttached(ATTACHMENT));
	}

	public static void set(PathfinderMob helper, BuildJob job) {
		helper.setAttached(ATTACHMENT, job);
	}

	public static void clear(PathfinderMob helper) {
		helper.removeAttached(ATTACHMENT);
	}
}
