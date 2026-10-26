package com.villaging;

import com.villaging.command.CommandService.CommandResult;
import com.villaging.command.VillagingRuntime;
import com.villaging.config.VillagingConfig;
import com.villaging.king.Kings;
import com.villaging.net.CommandPayload;
import com.villaging.net.OpenChatPayload;
import com.villaging.villager.ActionManager;
import com.villaging.villager.VillagerBrain;
import com.villaging.villager.VillagerBrains;
import com.villaging.villager.VillagerSpeech;
import net.fabricmc.api.ModInitializer;
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
import net.minecraft.world.entity.npc.villager.Villager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Villaging implements ModInitializer {
	public static final String MOD_ID = "villaging";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static volatile VillagingRuntime runtime;

	@Override
	public void onInitialize() {
		ModItems.init();
		VillagerBrains.init();
		Kings.init();

		PayloadTypeRegistry.serverboundPlay().register(CommandPayload.TYPE, CommandPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenChatPayload.TYPE, OpenChatPayload.CODEC);

		ServerLifecycleEvents.SERVER_STARTED.register(server -> runtime = new VillagingRuntime(server,
				VillagingConfig.load(FabricLoader.getInstance().getConfigDir().resolve("villaging.json"))));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			VillagingRuntime current = runtime;
			runtime = null;
			if (current != null) {
				current.shutdown();
			}
			ActionManager.INSTANCE.clear();
			VillagerSpeech.clear();
		});
		ServerTickEvents.END_LEVEL_TICK.register(ActionManager.INSTANCE::tick);
		ServerTickEvents.END_SERVER_TICK.register(server -> VillagerSpeech.tick());

		ServerPlayNetworking.registerGlobalReceiver(CommandPayload.TYPE, (payload, context) -> {
			VillagingRuntime current = runtime;
			ServerPlayer player = context.player();
			if (current == null) {
				return;
			}
			CommandResult result = current.handle(player, payload.villagerId(), payload.message());
			if (result != CommandResult.ACCEPTED && result != CommandResult.REJECTED_NOT_KING) {
				player.sendSystemMessage(Component.literal(VillagingRuntime.rejectionMessage(result, current.problem())));
			}
		});

		// Sneak + right-click on a smart villager opens the chat box instead of trading, for the king only.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown()
					|| !(entity instanceof Villager villager) || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			VillagerBrain brain = VillagerBrains.get(villager);
			if (!brain.smart() || !Kings.isKing(level.getServer(), sp.getUUID())) {
				return InteractionResult.PASS;
			}
			ServerPlayNetworking.send(sp, new OpenChatPayload(villager.getId(), brain.name()));
			return InteractionResult.SUCCESS_SERVER;
		});

		LOGGER.info("Villaging loaded");
	}
}
