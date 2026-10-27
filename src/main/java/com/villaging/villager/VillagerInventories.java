package com.villaging.villager;

import com.villaging.Villaging;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.PathfinderMob;

/** Registers and accesses the persisted {@link VillagerInventory} attached to villagers. */
public final class VillagerInventories {
	public static final AttachmentType<VillagerInventory> ATTACHMENT = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "inventory"), VillagerInventory.CODEC);

	private VillagerInventories() {
	}

	/** Forces class initialisation so the attachment is registered during mod init. */
	public static void init() {
	}

	/** The villager's inventory; created empty (and attached) on first use so changes are saved. */
	public static VillagerInventory get(PathfinderMob villager) {
		VillagerInventory inv = villager.getAttached(ATTACHMENT);
		if (inv == null) {
			inv = new VillagerInventory();
			villager.setAttached(ATTACHMENT, inv);
		}
		return inv;
	}
}
