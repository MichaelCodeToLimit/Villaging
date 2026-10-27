package com.villaging.villager;

import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** Melee, ranged, guarding and fleeing. Players and pets are never targets, whatever the order. */
final class CombatActions {
	private static final double MELEE_DISTANCE = 2.5;
	private static final int SWING_INTERVAL_TICKS = 12;

	private CombatActions() {
	}

	/** The weapon a villager fights with: what is in its hand, else the first sword or axe it carries. */
	private static ItemStack weaponFor(PathfinderMob villager) {
		if (!villager.getMainHandItem().isEmpty()) {
			return villager.getMainHandItem();
		}
		VillagerInventory inventory = VillagerInventories.get(villager);
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)) {
				return stack;
			}
		}
		return ItemStack.EMPTY;
	}

	/** One full-strength swing at {@code target} through the villager's hands. */
	private static void strike(PathfinderMob villager, LivingEntity target) {
		VillagerHands hands = HandsManager.get(villager);
		hands.syncFrom();
		VillagerPlayer player = hands.player();
		player.chargeAttack();
		player.setItemInHand(InteractionHand.MAIN_HAND, weaponFor(villager).copy());
		player.attack(target);
		villager.swing(InteractionHand.MAIN_HAND);
	}

	private static String name(Entity e) {
		return e.getType().getDescription().getString();
	}

	/** Fight one creature until it is dead. */
	static final class Attack implements VillagerAction {
		private static final int TIMEOUT_TICKS = 1200;

		private final int entityId;
		private int ticks;

		Attack(int entityId) {
			this.entityId = entityId;
		}

		@Override
		public Status tick(PathfinderMob villager) {
			if (!(villager.level() instanceof ServerLevel level)) {
				return Status.failed("not in a server world");
			}
			Entity found = level.getEntity(entityId);
			if (!(found instanceof LivingEntity target)) {
				return ticks == 0 ? Status.failed("there is nothing to attack with that id") : Status.done("it is gone");
			}
			String problem = Protection.entityProblem(target);
			if (problem != null) {
				return Status.failed(problem);
			}
			if (!target.isAlive()) {
				return Status.done("killed " + name(target));
			}
			if (ticks++ > TIMEOUT_TICKS) {
				return Status.failed("I could not finish off the " + name(target));
			}
			villager.getLookControl().setLookAt(target);
			if (villager.distanceTo(target) > MELEE_DISTANCE) {
				if (villager.getNavigation().isDone() || ticks % 10 == 1) {
					boolean moving = villager.getNavigation().moveTo(target, 0.8);
					if (!moving && ticks > Approach.START_GRACE_TICKS) {
						return Status.failed("I can't reach the " + name(target));
					}
				}
				return Status.RUNNING;
			}
			villager.getNavigation().stop();
			if (ticks % SWING_INTERVAL_TICKS == 0) {
				strike(villager, target);
			}
			return Status.RUNNING;
		}
	}

	/** Loose one arrow at a creature. */
	static final class Shoot implements VillagerAction {
		private static final double MAX_DISTANCE = 24.0;
		private static final int FULL_DRAW_TICKS = 20;

		private final int entityId;

		Shoot(int entityId) {
			this.entityId = entityId;
		}

		@Override
		public Status tick(PathfinderMob villager) {
			if (!(villager.level() instanceof ServerLevel level)) {
				return Status.failed("not in a server world");
			}
			if (!(level.getEntity(entityId) instanceof LivingEntity target) || !target.isAlive()) {
				return Status.failed("there is nothing to shoot with that id");
			}
			String problem = Protection.entityProblem(target);
			if (problem != null) {
				return Status.failed(problem);
			}
			VillagerInventory inventory = VillagerInventories.get(villager);
			if (inventory.count(Items.BOW) < 1) {
				return Status.failed("I need a bow");
			}
			if (inventory.count(Items.ARROW) < 1) {
				return Status.failed("I have no arrows");
			}
			if (villager.distanceTo(target) > MAX_DISTANCE) {
				return Status.failed("the " + name(target) + " is too far away to shoot");
			}
			if (!villager.hasLineOfSight(target)) {
				return Status.failed("I can't see the " + name(target) + " clearly enough to shoot");
			}
			Vec3 eye = villager.getEyePosition();
			Vec3 aimAt = target.getBoundingBox().getCenter();
			for (Entity inLine : level.getEntities(villager, new net.minecraft.world.phys.AABB(eye, aimAt).inflate(1.0),
					e -> e != target && e.isAlive())) {
				if (Protection.entityProblem(inLine) != null
						&& inLine.getBoundingBox().inflate(0.4).clip(eye, aimAt).isPresent()) {
					return Status.failed("the " + name(inLine) + " is in the line of fire, so I won't shoot");
				}
			}
			VillagerHands hands = HandsManager.get(villager);
			hands.syncFrom();
			VillagerPlayer player = hands.player();
			hands.hold(Items.BOW);
			Vec3 from = player.getEyePosition();
			Vec3 to = target.getBoundingBox().getCenter();
			double dx = to.x - from.x;
			double dy = to.y - from.y;
			double dz = to.z - from.z;
			float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0f;
			float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
			player.setYRot(yaw);
			player.setXRot(pitch);
			player.setYHeadRot(yaw);
			villager.getLookControl().setLookAt(target);
			ItemStack bow = player.getItemInHand(InteractionHand.MAIN_HAND);
			int arrowsBefore = inventory.count(Items.ARROW);
			bow.releaseUsing(level, player, bow.getUseDuration(player) - FULL_DRAW_TICKS);
			hands.syncTo();
			villager.swing(InteractionHand.MAIN_HAND);
			return inventory.count(Items.ARROW) < arrowsBefore ? Status.done("shot an arrow at the " + name(target))
					: Status.failed("the shot did not go off");
		}
	}

	/** Stay near the king and fight hostile monsters that come close, until replaced or stopped. */
	static final class Guard implements VillagerAction {
		private static final double GUARD_RADIUS = 12.0;
		private static final double COMFORT_DISTANCE = 4.0;

		private final UUID kingId;
		private LivingEntity target;
		private int ticks;

		Guard(UUID kingId) {
			this.kingId = kingId;
		}

		@Override
		public boolean isContinuous() {
			return true;
		}

		@Override
		public Status tick(PathfinderMob villager) {
			if (!(villager.level() instanceof ServerLevel level)) {
				return Status.RUNNING;
			}
			ticks++;
			ServerPlayer king = level.getServer().getPlayerList().getPlayer(kingId);
			Entity centre = king != null && king.level() == level ? king : villager;
			if (target == null || !target.isAlive() || target.isRemoved() || target.distanceTo(centre) > GUARD_RADIUS * 1.5) {
				target = null;
				if (ticks % 10 == 0) {
					target = nearestThreat(level, villager, centre);
				}
			}
			if (target != null) {
				villager.getLookControl().setLookAt(target);
				if (villager.distanceTo(target) > MELEE_DISTANCE) {
					if (villager.getNavigation().isDone() || ticks % 10 == 0) {
						villager.getNavigation().moveTo(target, 0.8);
					}
				} else {
					villager.getNavigation().stop();
					if (ticks % SWING_INTERVAL_TICKS == 0) {
						strike(villager, target);
					}
				}
			} else if (king != null && villager.distanceTo(king) > COMFORT_DISTANCE) {
				if (villager.getNavigation().isDone() || ticks % 10 == 0) {
					villager.getNavigation().moveTo(king, 0.7);
				}
			} else {
				villager.getNavigation().stop();
			}
			return Status.RUNNING;
		}

		private LivingEntity nearestThreat(ServerLevel level, PathfinderMob villager, Entity centre) {
			LivingEntity best = null;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, centre.getBoundingBox().inflate(GUARD_RADIUS),
					entity -> entity instanceof Enemy && entity.isAlive() && Protection.entityProblem(entity) == null)) {
				if (best == null || e.distanceToSqr(villager) < best.distanceToSqr(villager)) {
					best = e;
				}
			}
			return best;
		}
	}

	/** Run away from the nearest hostile creature for a while. */
	static final class Flee implements VillagerAction {
		private static final double THREAT_RADIUS = 16.0;

		private final int totalTicks;
		private int ticks;

		Flee(int seconds) {
			this.totalTicks = seconds * 20;
		}

		@Override
		public Status tick(PathfinderMob villager) {
			if (!(villager.level() instanceof ServerLevel level)) {
				return Status.failed("not in a server world");
			}
			if (ticks++ >= totalTicks) {
				return Status.done("ran away");
			}
			if (ticks % 15 == 1) {
				LivingEntity threat = null;
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(THREAT_RADIUS),
						entity -> entity instanceof Enemy && entity.isAlive())) {
					if (threat == null || e.distanceToSqr(villager) < threat.distanceToSqr(villager)) {
						threat = e;
					}
				}
				if (threat == null) {
					return ticks == 1 ? Status.done("there was nothing threatening nearby, so I stayed put") : Status.done("ran away");
				}
				Vec3 away = DefaultRandomPos.getPosAway(villager, 16, 7, threat.position());
				if (away == null || !villager.getNavigation().moveTo(away.x, away.y, away.z, 1.0)) {
					// No random spot worked out: run straight away from the threat instead.
					Vec3 direction = villager.position().subtract(threat.position());
					direction = direction.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : direction.normalize();
					for (double distance : new double[] {12, 8, 5}) {
						Vec3 spot = villager.position().add(direction.scale(distance));
						if (villager.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0)) {
							break;
						}
					}
				}
			}
			return Status.RUNNING;
		}
	}
}
