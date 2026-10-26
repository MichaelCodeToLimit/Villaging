package com.villaging.villager;

import com.villaging.Villaging;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.npc.villager.Villager;

/** Registers and accesses the persisted {@link VillagerBrain} attached to villagers. */
public final class VillagerBrains {
	public static final AttachmentType<VillagerBrain> ATTACHMENT = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "brain"), VillagerBrain.CODEC);

	private VillagerBrains() {
	}

	/** Forces class initialisation so the attachment is registered during mod init. */
	public static void init() {
	}

	public static VillagerBrain get(Villager villager) {
		return villager.getAttachedOrElse(ATTACHMENT, VillagerBrain.DEFAULT);
	}

	public static void set(Villager villager, VillagerBrain brain) {
		villager.setAttached(ATTACHMENT, brain);
	}
}
