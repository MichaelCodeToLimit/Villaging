package com.villaging.mixin;

import com.villaging.villager.ActionManager;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * While a golem (or any creature) is carrying out a king's order, its own goals (wandering, patrolling, attacking)
 * must not steer it elsewhere. Villagers are also covered by {@link VillagerMixin}, which pauses their brain.
 */
@Mixin(Mob.class)
public abstract class MobMixin {
	@Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tick()V"))
	private void villaging$pauseGoalsWhileObeying(GoalSelector selector) {
		if (!villaging$obeying()) {
			selector.tick();
		}
	}

	@Redirect(method = "serverAiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/goal/GoalSelector;tickRunningGoals(Z)V"))
	private void villaging$pauseRunningGoalsWhileObeying(GoalSelector selector, boolean force) {
		if (!villaging$obeying()) {
			selector.tickRunningGoals(force);
		}
	}

	private boolean villaging$obeying() {
		return (Object) this instanceof PathfinderMob mob && ActionManager.isControlled(mob);
	}
}
