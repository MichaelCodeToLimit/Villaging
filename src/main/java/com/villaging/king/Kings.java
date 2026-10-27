package com.villaging.king;

import com.villaging.Villaging;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;

/** Registers and accesses the world's {@link KingData}, stored on the overworld. */
public final class Kings {
	public static final AttachmentType<KingData> ATTACHMENT = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "king"), KingData.CODEC);

	private Kings() {
	}

	public static void init() {
	}

	public static KingData get(MinecraftServer server) {
		return server.overworld().getAttachedOrElse(ATTACHMENT, KingData.EMPTY);
	}

	public static boolean isKing(MinecraftServer server, UUID player) {
		return get(server).isKing(player);
	}

	public static String title(MinecraftServer server) {
		return get(server).title();
	}

	public static void setTitle(MinecraftServer server, String title) {
		server.overworld().setAttached(ATTACHMENT, get(server).withTitle(title));
	}

	/** True if the player is now king (newly claimed or already king). */
	public static boolean claim(MinecraftServer server, UUID player) {
		Optional<KingData> claimed = get(server).claim(player);
		claimed.ifPresent(data -> server.overworld().setAttached(ATTACHMENT, data));
		return claimed.isPresent();
	}
}
