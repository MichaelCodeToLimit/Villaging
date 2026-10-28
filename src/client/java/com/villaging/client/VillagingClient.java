package com.villaging.client;

import com.villaging.net.AskPayload;
import com.villaging.net.ChatLinePayload;
import com.villaging.net.OpenChatPayload;
import com.villaging.net.OpenGuidePayload;
import com.villaging.net.OpenKeyPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class VillagingClient implements ClientModInitializer {
	/** A key box the server asked for while the guide was open; it opens when the guide closes. */
	private static volatile String pendingKeyProvider;
	/** The guide was already shown during this stay in a world. */
	private static volatile boolean guideOffered;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(OpenChatPayload.TYPE, (payload, context) ->
				context.client().gui.setScreen(new VillagerChatScreen(payload.villagerId(), payload.villagerName(), payload.history())));
		ClientPlayNetworking.registerGlobalReceiver(ChatLinePayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof VillagerChatScreen chat && chat.villagerId() == payload.villagerId()) {
				chat.addVillagerLine(payload.text());
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(OpenKeyPayload.TYPE, (payload, context) -> {
			if (context.client().gui.screen() instanceof GuideScreen) {
				pendingKeyProvider = payload.provider();
			} else {
				context.client().gui.setScreen(new KeyScreen(payload.provider()));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(OpenGuidePayload.TYPE, (payload, context) ->
				context.client().gui.setScreen(new GuideScreen(0)));
		ClientPlayNetworking.registerGlobalReceiver(AskPayload.TYPE, (payload, context) ->
				context.client().gui.setScreen(new AskScreen(payload)));

		// The first time on this computer, the guide opens once the player is in the world and no other screen is up
		// (joining shows a loading screen first, so it cannot be opened at the moment of joining).
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> guideOffered = false);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!guideOffered && client.player != null && client.level != null && client.gui.screen() == null && !GuideSeen.seen()) {
				guideOffered = true;
				client.gui.setScreen(new GuideScreen(0));
			}
		});
	}

	/** Opens the guide if it was never shown and nothing else (such as the key box) is open. */
	static void maybeShowGuide(Minecraft client) {
		if (!GuideSeen.seen() && client.gui.screen() == null) {
			client.gui.setScreen(new GuideScreen(0));
		}
	}

	/** The guide closed: show the key box that was waiting, if any. */
	static void afterGuide(Minecraft client) {
		String provider = pendingKeyProvider;
		pendingKeyProvider = null;
		if (provider != null && client != null) {
			client.gui.setScreen(new KeyScreen(provider));
		}
	}
}
