package com.villaging;

import com.villaging.command.CommandService.CommandResult;
import com.villaging.build.BuildJob;
import com.villaging.build.BuildJobs;
import com.villaging.build.BuildRegistry;
import com.villaging.command.HelperCommands;
import com.villaging.command.VillagingRuntime;
import com.villaging.config.VillagingConfig;
import com.villaging.king.ClickRule;
import com.villaging.king.CrownItem;
import com.villaging.king.Kings;
import com.villaging.command.KeySettings;
import com.villaging.net.OpenGuidePayload;
import com.villaging.net.OpenKeyPayload;
import com.villaging.net.SetKeyPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import com.villaging.net.AnswerPayload;
import com.villaging.net.AskPayload;
import com.villaging.net.CommandPayload;
import com.villaging.net.ChatLinePayload;
import com.villaging.net.CloseChatPayload;
import com.villaging.net.OpenChatPayload;
import com.villaging.villager.ChatTranscript;
import com.villaging.villager.TalkHold;
import com.villaging.ai.tools.AbilityTools;
import com.villaging.ai.PendingQuestions;
import com.villaging.villager.ActionManager;
import com.villaging.villager.HandsManager;
import com.villaging.villager.ScriptManager;
import com.villaging.villager.VillagerBrain;
import com.villaging.villager.VillagerBrains;
import com.villaging.villager.VillagerInventories;
import com.villaging.villager.ToolHandlers;
import com.villaging.villager.VillagerSpeech;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import com.villaging.villager.Kinds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Villaging implements ModInitializer {
	public static final String MOD_ID = "villaging";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static volatile VillagingRuntime runtime;

	/** The running server's runtime, or null when no server is running. */
	public static VillagingRuntime runtime() {
		return runtime;
	}

	public static java.nio.file.Path configFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("villaging.json");
	}

	/** Swaps in a runtime built from new settings (the key box) and stops the old one. */
	public static void replaceRuntime(VillagingRuntime replacement) {
		VillagingRuntime old = runtime;
		runtime = replacement;
		if (old != null) {
			old.shutdown();
		}
	}

	@Override
	public void onInitialize() {
		ModItems.init();
		VillagerBrains.init();
		VillagerInventories.init();
		BuildJobs.init();
		com.villaging.team.Teams.init();
		ToolHandlers.registerBase();
		ToolHandlers.registerAbilities();
		AbilityTools.registerAll();
		Kings.init();

		PayloadTypeRegistry.serverboundPlay().register(CommandPayload.TYPE, CommandPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenChatPayload.TYPE, OpenChatPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ChatLinePayload.TYPE, ChatLinePayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CloseChatPayload.TYPE, CloseChatPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AskPayload.TYPE, AskPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(AnswerPayload.TYPE, AnswerPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenKeyPayload.TYPE, OpenKeyPayload.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SetKeyPayload.TYPE, SetKeyPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenGuidePayload.TYPE, OpenGuidePayload.CODEC);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> runtime = new VillagingRuntime(server,
				VillagingConfig.load(configFile())));

		// The AI key is typed into a box in game. It opens by itself when smart villagers have no working AI yet,
		// and any time with /villaging key.
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			VillagingRuntime current = runtime;
			if (current != null && !current.problem().isEmpty() && KeySettings.mayChange(server, handler.getPlayer())) {
				ServerPlayNetworking.send(handler.getPlayer(), new OpenKeyPayload(current.config().provider()));
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> HelperCommands.register(dispatcher));
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) ->
				dispatcher.register(Commands.literal("villaging").then(Commands.literal("key").executes(context -> {
					ServerPlayer player = context.getSource().getPlayerOrException();
					if (!KeySettings.mayChange(context.getSource().getServer(), player)) {
						player.sendSystemMessage(Component.literal("Only the world owner or an admin can change the AI key."));
						return 0;
					}
					VillagingRuntime current = runtime;
					ServerPlayNetworking.send(player,
							new OpenKeyPayload(current != null ? current.config().provider() : "cloud"));
					return 1;
				}))
						.then(Commands.literal("guide").executes(context -> {
							ServerPlayNetworking.send(context.getSource().getPlayerOrException(), new OpenGuidePayload());
							return 1;
						}))));
		ServerPlayNetworking.registerGlobalReceiver(SetKeyPayload.TYPE, (payload, context) -> {
			// The key is never logged or echoed back.
			ServerPlayer player = context.player();
			player.sendSystemMessage(Component.literal(
					KeySettings.apply(context.server(), player, payload.provider(), payload.key())));
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			VillagingRuntime current = runtime;
			runtime = null;
			if (current != null) {
				current.shutdown();
			}
			ActionManager.INSTANCE.clear();
			HandsManager.clear();
			PendingQuestions.INSTANCE.cancelAll();
			ScriptManager.INSTANCE.cancelAll();
			VillagerSpeech.clear();
			TalkHold.clear();
			HelperCommands.clear();
			BuildRegistry.clear();
		});
		ServerTickEvents.END_LEVEL_TICK.register(ActionManager.INSTANCE::tick);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			VillagerSpeech.tick();
			TalkHold.tick(server);
			HelperCommands.tick(server);
			PendingQuestions.INSTANCE.expire(System.nanoTime());
		});

		ServerPlayNetworking.registerGlobalReceiver(AnswerPayload.TYPE, (payload, context) ->
				PendingQuestions.INSTANCE.answer(context.player().getUUID(), payload.questionId(), payload.answer(), System.nanoTime()));

		ServerPlayNetworking.registerGlobalReceiver(CloseChatPayload.TYPE, (payload, context) ->
				TalkHold.release(payload.villagerId(), context.player().getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				TalkHold.releaseKing(handler.getPlayer().getUUID()));

		ServerPlayNetworking.registerGlobalReceiver(CommandPayload.TYPE, (payload, context) -> {
			VillagingRuntime current = runtime;
			ServerPlayer player = context.player();
			if (current == null) {
				return;
			}
			if (player.level().getEntity(payload.villagerId()) instanceof PathfinderMob talking) {
				TalkHold.renew(talking, player.getUUID());
			}
			CommandResult result = current.handle(player, payload.villagerId(), payload.message());
			if (result != CommandResult.ACCEPTED && result != CommandResult.REJECTED_NOT_KING) {
				player.sendSystemMessage(Component.literal(VillagingRuntime.rejectionMessage(result, current.problem())));
			}
		});

		// Runs before vanilla villager interaction (which would otherwise open trading and swallow the click):
		// the crown makes a villager smart, and sneak + right-click on a smart villager opens the chat box.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(entity instanceof PathfinderMob villager) || !Kinds.canBeSmart(entity)
					|| !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			VillagerBrain brain = VillagerBrains.get(villager);
			// Sneak + right-click on any villager opens the box; speaking to an ordinary one makes it smart.
			boolean mayCommand = Kings.isKing(level.getServer(), sp.getUUID()) || Kings.get(level.getServer()).king().isEmpty();
			switch (ClickRule.decide(mayCommand, brain.smart(), villager instanceof Villager, player.isShiftKeyDown(),
					sp.getItemInHand(hand).is(ModItems.CROWN))) {
				case OPEN_CHAT -> {
					ServerPlayNetworking.send(sp, new OpenChatPayload(villager.getId(),
							brain.smart() ? brain.name() : "this villager", ChatTranscript.forClient(brain.memory())));
					TalkHold.start(villager, sp.getUUID());
					return InteractionResult.SUCCESS_SERVER;
				}
				case CROWN -> {
					return CrownItem.applyToVillager(sp, villager);
				}
				default -> {
					return InteractionResult.PASS;
				}
			}
		});

		// The villager's hidden fake player goes with it.
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob villager && Kinds.canBeSmart(entity)) {
				HandsManager.release(villager);
				PendingQuestions.INSTANCE.cancelFor(villager.getId());
				ScriptManager.INSTANCE.cancel(villager);
				TalkHold.release(villager);
				BuildRegistry.finish(villager.getUUID());
			}
		});

		// A smart villager that was showing a speech bubble when its chunk was saved gets its real name back.
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob villager && Kinds.canBeSmart(entity)) {
				VillagerSpeech.heal(villager);
				// A build that was running when the chunk or world was saved never carries on by itself.
				BuildJobs.get(villager).ifPresent(job -> BuildJobs.set(villager, BuildJob.afterReload(job)));
			}
		});

		LOGGER.info("Villaging loaded");
	}
}
