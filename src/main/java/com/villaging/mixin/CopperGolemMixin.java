package com.villaging.mixin;

import com.villaging.villager.ActionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** While a copper golem is carrying out a king's order, its own brain (item sorting) must not steer it elsewhere. */
@Mixin(CopperGolem.class)
public abstract class CopperGolemMixin {
	@SuppressWarnings({"rawtypes", "unchecked"})
	@Redirect(method = "customServerAiStep", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V"))
	private void villaging$pauseBrainWhileObeying(Brain brain, ServerLevel level, LivingEntity entity) {
		if (!ActionManager.isControlled((CopperGolem) (Object) this)) {
			brain.tick(level, entity);
		}
	}
}
