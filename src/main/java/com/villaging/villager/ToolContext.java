package com.villaging.villager;

import com.villaging.config.VillagingConfig;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.PathfinderMob;

/** Everything a tool handler needs to act for one villager. Handlers run on the server main thread. */
public record ToolContext(MinecraftServer server, PathfinderMob villager, UUID kingId, VillagingConfig config) {
	/** The villager's hands, created on first use. */
	public VillagerHands hands() {
		return HandsManager.get(villager);
	}
}
