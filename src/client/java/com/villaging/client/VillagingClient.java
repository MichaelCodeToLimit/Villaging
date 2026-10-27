package com.villaging.client;

import com.villaging.net.AskPayload;
import com.villaging.net.OpenChatPayload;
import com.villaging.net.OpenKeyPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class VillagingClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(OpenChatPayload.TYPE, (payload, context) ->
				context.client().gui.setScreen(new VillagerChatScreen(payload.villagerId(), payload.villagerName())));
		ClientPlayNetworking.registerGlobalReceiver(OpenKeyPayload.TYPE, (payload, context) ->
				context.client().gui.setScreen(new KeyScreen(payload.provider())));
		ClientPlayNetworking.registerGlobalReceiver(AskPayload.TYPE, (payload, context) ->
				context.client().gui.setScreen(new AskScreen(payload)));
	}
}
