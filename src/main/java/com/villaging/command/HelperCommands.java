package com.villaging.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.villaging.Villaging;
import com.villaging.command.CommandService.CommandResult;
import com.villaging.command.HelperRoster.Ambiguous;
import com.villaging.command.HelperRoster.Found;
import com.villaging.command.HelperRoster.Helper;
import com.villaging.king.Kings;
import com.villaging.villager.ActionManager;
import com.villaging.villager.Kinds;
import com.villaging.villager.ScriptManager;
import com.villaging.villager.VillagerBrains;
import com.villaging.villager.VillagerSpeech;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

/** The king's two group commands: /announcements (talk to every helper) and /villaging summon (call one by name). */
public final class HelperCommands {
	/** Helpers within this many blocks of the king hear an announcement. */
	public static final int ANNOUNCE_RANGE = 64;
	/** A helper is summoned from anywhere in loaded land within this many blocks. */
	private static final double SUMMON_SEARCH = 512;
	private static final String ANNOUNCEMENT_PREFIX = "(The king speaks to all helpers at once) ";

	private record Pending(UUID king, int helperId, int dueTick, String message) {
	}

	private static final List<Pending> QUEUE = new ArrayList<>();

	private HelperCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("announcements")
				.then(Commands.argument("message", StringArgumentType.greedyString()).executes(context -> announce(
						context.getSource().getPlayerOrException(), StringArgumentType.getString(context, "message")))));
		dispatcher.register(Commands.literal("villaging").then(Commands.literal("summon")
				.then(Commands.argument("name", StringArgumentType.greedyString())
						.suggests((context, builder) -> {
							ServerPlayer player = context.getSource().getPlayer();
							return SharedSuggestionProvider.suggest(
									player == null ? List.<String>of() : helperNames(player.level(), player), builder);
						})
						.executes(context -> summon(context.getSource().getPlayerOrException(),
								StringArgumentType.getString(context, "name"))))));
	}

	private static List<PathfinderMob> smartHelpers(ServerLevel level, ServerPlayer king, double range) {
		return level.getEntitiesOfClass(PathfinderMob.class, king.getBoundingBox().inflate(range), Kinds::isSmart);
	}

	private static List<String> helperNames(ServerLevel level, ServerPlayer king) {
		return smartHelpers(level, king, SUMMON_SEARCH).stream().map(m -> VillagerBrains.get(m).name())
				.filter(n -> !n.isBlank()).sorted().toList();
	}

	private static boolean onlyKing(ServerPlayer player) {
		if (Kings.isKing(player.level().getServer(), player.getUUID())) {
			return true;
		}
		player.sendSystemMessage(Component.literal("Only the king may command villagers. Right-click a villager and speak to it first."));
		return false;
	}

	/** Queues the message for every smart helper near the king, one every 1.5 seconds. */
	public static int announce(ServerPlayer king, String message) {
		MinecraftServer server = king.level().getServer();
		VillagingRuntime runtime = Villaging.runtime();
		if (!onlyKing(king) || runtime == null) {
			return 0;
		}
		String text = message == null ? "" : message.trim();
		if (text.isEmpty() || text.length() > CommandService.MAX_MESSAGE_LENGTH - ANNOUNCEMENT_PREFIX.length()) {
			king.sendSystemMessage(Component.literal("Say something between 1 and "
					+ (CommandService.MAX_MESSAGE_LENGTH - ANNOUNCEMENT_PREFIX.length()) + " characters."));
			return 0;
		}
		if (!runtime.problem().isEmpty()) {
			king.sendSystemMessage(Component.literal(VillagingRuntime.rejectionMessage(CommandResult.DISABLED_BAD_CONFIG, runtime.problem())));
			return 0;
		}
		List<PathfinderMob> near = new ArrayList<>(smartHelpers(king.level(), king, ANNOUNCE_RANGE));
		near.sort(Comparator.comparingDouble(m -> m.distanceToSqr(king)));
		List<Helper> chosen = HelperRoster.limit(near.stream().map(m -> new Helper(m.getId(), VillagerBrains.get(m).name())).toList(),
				HelperRoster.MAX_ANNOUNCED);
		if (chosen.isEmpty()) {
			king.sendSystemMessage(Component.literal("No smart villagers or golems within " + ANNOUNCE_RANGE + " blocks to hear you."));
			return 0;
		}
		List<Integer> delays = HelperRoster.staggerTicks(chosen.size(), HelperRoster.ANNOUNCE_GAP_TICKS);
		int now = server.getTickCount();
		synchronized (QUEUE) {
			for (int i = 0; i < chosen.size(); i++) {
				QUEUE.add(new Pending(king.getUUID(), chosen.get(i).id(), now + delays.get(i), ANNOUNCEMENT_PREFIX + text));
			}
		}
		king.sendSystemMessage(Component.literal("Announcing to " + chosen.size() + (chosen.size() == 1 ? " helper" : " helpers") + "..."));
		return chosen.size();
	}

	/** Called every server tick: asks the next helpers whose turn has come. */
	public static void tick(MinecraftServer server) {
		VillagingRuntime runtime = Villaging.runtime();
		List<Pending> due = new ArrayList<>();
		synchronized (QUEUE) {
			if (QUEUE.isEmpty()) {
				return;
			}
			QUEUE.removeIf(p -> {
				if (p.dueTick() <= server.getTickCount()) {
					due.add(p);
					return true;
				}
				return false;
			});
		}
		for (Pending p : due) {
			ServerPlayer king = server.getPlayerList().getPlayer(p.king());
			if (king == null || runtime == null) {
				continue;
			}
			CommandResult result = runtime.handleAnnouncement(king, p.helperId(), p.message());
			if (result != CommandResult.ACCEPTED) {
				Villaging.LOGGER.info("Announcement not delivered to helper {}: {}", p.helperId(), result);
			}
		}
	}

	public static void clear() {
		synchronized (QUEUE) {
			QUEUE.clear();
		}
	}

	/** Brings the helper called {@code name} to the king's side. Needs no AI. */
	public static int summon(ServerPlayer king, String name) {
		VillagingRuntime runtime = Villaging.runtime();
		if (!onlyKing(king)) {
			return 0;
		}
		ServerLevel level = king.level();
		List<Helper> helpers = smartHelpers(level, king, SUMMON_SEARCH).stream()
				.map(m -> new Helper(m.getId(), VillagerBrains.get(m).name())).toList();
		HelperRoster.Match match = HelperRoster.find(helpers, name);
		if (match instanceof Ambiguous ambiguous) {
			king.sendSystemMessage(Component.literal("Which one? " + String.join(", ", ambiguous.names()) + ". Type more of the name."));
			return 0;
		}
		if (!(match instanceof Found found) || !(level.getEntity(found.helper().id()) instanceof PathfinderMob helper)) {
			king.sendSystemMessage(Component.literal(helpers.isEmpty()
					? "You have no smart villagers or golems in this world yet."
					: "No helper called \"" + name.trim() + "\" is here. Helpers: " + String.join(", ", helperNames(level, king)) + "."));
			return 0;
		}
		// Whatever it was doing stops: its plan, its action, its script and its walk.
		if (runtime != null) {
			runtime.interrupt(helper.getId());
		}
		ActionManager.INSTANCE.stop(helper);
		ScriptManager.INSTANCE.cancel(helper);
		helper.getNavigation().stop();
		Vec3 look = king.getLookAngle();
		double x = king.getX() + look.x * 1.6;
		double z = king.getZ() + look.z * 1.6;
		float yaw = (float) (Mth.atan2(king.getZ() - z, king.getX() - x) * Mth.RAD_TO_DEG) - 90.0F;
		helper.snapTo(x, king.getY(), z, yaw, 0.0F);
		helper.fallDistance = 0;
		VillagerSpeech.say(level.getServer(), helper, king.getUUID(), "At your service, " + Kings.title(level.getServer()) + "!");
		return 1;
	}
}
