package com.villaging.client;

import com.villaging.net.CommandPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** The text box the king types orders into. Enter sends and closes; Escape just closes. */
public class VillagerChatScreen extends Screen {
	private static final int MAX_LENGTH = 500;

	private final int villagerId;
	private final String villagerName;
	private EditBox input;

	public VillagerChatScreen(int villagerId, String villagerName) {
		super(Component.literal("Speak to " + villagerName));
		this.villagerId = villagerId;
		this.villagerName = villagerName;
	}

	@Override
	protected void init() {
		int width = Math.min(this.width - 40, 300);
		input = new EditBox(this.font, (this.width - width) / 2, this.height / 2 - 10, width, 20,
				Component.literal("Speak to " + villagerName));
		input.setMaxLength(MAX_LENGTH);
		input.setHint(Component.literal("Tell " + villagerName + " anything..."));
		addRenderableWidget(input);
	}

	@Override
	protected void setInitialFocus() {
		setInitialFocus(input);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation()) {
			String text = input.getValue().trim();
			if (!text.isEmpty()) {
				ClientPlayNetworking.send(new CommandPayload(villagerId, text));
			}
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
