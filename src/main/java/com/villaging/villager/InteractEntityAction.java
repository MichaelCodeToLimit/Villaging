package com.villaging.villager;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.AbstractCow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Walk to an animal and use an item on it as a player would: shear, milk, feed to breed, or try to tame. */
public final class InteractEntityAction implements VillagerAction {
	public enum Kind {
		SHEAR("sheep that is ready to shear"), MILK("cow to milk"), BREED("animal to breed"), TAME("wild animal to tame");

		final String what;

		Kind(String what) {
			this.what = what;
		}
	}

	private static final double SEARCH_RADIUS = 16.0;
	private static final double TOUCH_DISTANCE = 2.5;
	private static final int TIMEOUT_TICKS = 900;
	private static final int TAME_ATTEMPTS = 20;
	private static final int TAME_DELAY_TICKS = 15;

	private final Kind kind;
	private final Integer entityId;
	private int ticks;
	private Entity target;
	private int fed;
	private int attempts;
	private int nextAttempt;

	/** @param entityId a specific animal, or null for the nearest suitable one */
	public InteractEntityAction(Kind kind, Integer entityId) {
		this.kind = kind;
		this.entityId = entityId;
	}

	@Override
	public Status tick(PathfinderMob villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return Status.failed("not in a server world");
		}
		if (ticks++ > TIMEOUT_TICKS) {
			return Status.failed("took too long");
		}
		if (target == null || target.isRemoved()) {
			target = findTarget(level, villager, null);
			if (target == null) {
				return fed > 0 ? Status.done(summary()) : Status.failed("I don't see a " + kind.what);
			}
		}
		if (villager.distanceTo(target) > TOUCH_DISTANCE) {
			if (villager.getNavigation().isDone() || ticks % 20 == 1) {
				boolean moving = villager.getNavigation().moveTo(target, Approach.SPEED);
				if (!moving && ticks > Approach.START_GRACE_TICKS) {
					return Status.failed("I can't reach it");
				}
			}
			return Status.RUNNING;
		}
		villager.getLookControl().setLookAt(target);
		if (kind == Kind.TAME && ticks < nextAttempt) {
			return Status.RUNNING;
		}
		Item item = chooseItem(villager);
		if (item == null) {
			return fed > 0 ? Status.done(summary()) : Status.failed(missingItem());
		}
		VillagerHands hands = HandsManager.get(villager);
		hands.syncFrom();
		hands.hold(item);
		InteractionResult result = hands.player().interactOn(target, InteractionHand.MAIN_HAND, target.position());
		hands.syncTo();
		villager.swing(InteractionHand.MAIN_HAND);
		return afterInteraction(villager, level, result);
	}

	private Status afterInteraction(PathfinderMob villager, ServerLevel level, InteractionResult result) {
		switch (kind) {
			case SHEAR, MILK -> {
				return result.consumesAction() ? Status.done(kind == Kind.SHEAR ? "sheared the sheep" : "milked the cow")
						: Status.failed("that did not work");
			}
			case BREED -> {
				if (!result.consumesAction()) {
					return Status.failed("it would not eat that");
				}
				fed++;
				if (fed >= 2) {
					return Status.done(summary());
				}
				Entity partner = findTarget(level, villager, target);
				if (partner == null) {
					return Status.done("fed one animal, but found no partner of the same kind");
				}
				target = partner;
				return Status.RUNNING;
			}
			case TAME -> {
				if (target instanceof TamableAnimal t && t.isTame()) {
					return Status.done("tamed the " + target.getType().getDescription().getString().toLowerCase());
				}
				if (++attempts >= TAME_ATTEMPTS) {
					return Status.failed("it would not be tamed");
				}
				nextAttempt = ticks + TAME_DELAY_TICKS;
				return Status.RUNNING;
			}
		}
		return Status.failed("unsupported");
	}

	private String summary() {
		return "fed " + fed + " animal" + (fed == 1 ? "" : "s") + " so they can breed";
	}

	private String missingItem() {
		return switch (kind) {
			case SHEAR -> "I need shears";
			case MILK -> "I need an empty bucket";
			case BREED -> "I have nothing that animal will eat";
			case TAME -> "I have nothing to tame it with";
		};
	}

	private Item chooseItem(PathfinderMob villager) {
		VillagerInventory inventory = VillagerInventories.get(villager);
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			boolean usable = switch (kind) {
				case SHEAR -> stack.is(Items.SHEARS);
				case MILK -> stack.is(Items.BUCKET);
				case BREED -> target instanceof Animal a && a.isFood(stack);
				case TAME -> !stack.is(Items.SHEARS) && !stack.is(Items.BUCKET) && target instanceof Animal a && a.isFood(stack)
						|| stack.is(Items.BONE);
			};
			if (usable) {
				return stack.getItem();
			}
		}
		return null;
	}

	/** The chosen animal, or the nearest suitable one; {@code partnerOf} restricts to the same type and not itself. */
	private Entity findTarget(ServerLevel level, PathfinderMob villager, Entity partnerOf) {
		if (partnerOf == null && entityId != null) {
			Entity e = level.getEntity(entityId);
			return e != null && suitable(e) && e.distanceTo(villager) <= SEARCH_RADIUS ? e : null;
		}
		List<Entity> candidates = new ArrayList<>(level.getEntities((Entity) null,
				villager.getBoundingBox().inflate(SEARCH_RADIUS), this::suitable));
		Entity best = null;
		for (Entity e : candidates) {
			if (partnerOf != null && (e == partnerOf || e.getType() != partnerOf.getType())) {
				continue;
			}
			if (best == null || e.distanceToSqr(villager) < best.distanceToSqr(villager)) {
				best = e;
			}
		}
		return best;
	}

	private boolean suitable(Entity e) {
		if (e.isRemoved() || !e.isAlive()) {
			return false;
		}
		return switch (kind) {
			case SHEAR -> e instanceof Sheep s && s.readyForShearing();
			case MILK -> e instanceof AbstractCow c && !c.isBaby();
			case BREED -> e instanceof Animal a && !a.isBaby() && a.canFallInLove();
			case TAME -> e instanceof TamableAnimal t && !t.isTame();
		};
	}
}
