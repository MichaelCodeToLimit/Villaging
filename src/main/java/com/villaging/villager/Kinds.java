package com.villaging.villager;

import com.villaging.ai.SmartKind;
import java.util.Optional;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;

/** Which creatures can be made smart, and what kind each one is. */
public final class Kinds {
	private Kinds() {
	}

	public static Optional<SmartKind> of(Entity entity) {
		if (entity instanceof Villager) {
			return Optional.of(SmartKind.VILLAGER);
		}
		if (entity instanceof IronGolem) {
			return Optional.of(SmartKind.IRON_GOLEM);
		}
		if (entity instanceof CopperGolem) {
			return Optional.of(SmartKind.COPPER_GOLEM);
		}
		return Optional.empty();
	}

	public static SmartKind kindOf(PathfinderMob mob) {
		return of(mob).orElse(SmartKind.VILLAGER);
	}

	public static boolean canBeSmart(Entity entity) {
		return entity instanceof PathfinderMob && of(entity).isPresent();
	}

	/** A creature that is already smart. */
	public static boolean isSmart(Entity entity) {
		return entity instanceof PathfinderMob mob && canBeSmart(mob) && VillagerBrains.get(mob).smart();
	}
}
