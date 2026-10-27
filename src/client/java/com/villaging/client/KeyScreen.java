package com.villaging.client;

import com.villaging.net.SetKeyPayload;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * The box where the player types their AI key (hidden as dots) and picks the AI. Enter or Save sends it to the
 * server, which stores it; Escape closes without changing anything.
 */
public class KeyScreen extends Screen {
	private static final List<String> PROVIDERS = List.of("cloud", "gemini", "ollama");
	private static final List<String> LABELS = List.of("Claude", "Gemini", "Ollama (local, no key)");

	private int providerIndex;
	private EditBox key;
	private Button providerButton;

	public KeyScreen(String provider) {
		super(Component.literal("Smart villagers: AI key"));
		this.providerIndex = Math.max(0, PROVIDERS.indexOf(provider));
	}

	@Override
	protected void init() {
		int width = Math.min(this.width - 40, 280);
		int left = (this.width - width) / 2;
		int y = this.height / 2 - 30;
		providerButton = Button.builder(providerLabel(), button -> {
			providerIndex = (providerIndex + 1) % PROVIDERS.size();
			button.setMessage(providerLabel());
		}).bounds(left, y, width, 20).build();
		addRenderableWidget(providerButton);

		key = new EditBox(this.font, left, y + 28, width, 20, Component.literal("API key"));
		key.setMaxLength(SetKeyPayload.MAX_KEY);
		key.setHint(Component.literal("Paste your API key here..."));
		key.addFormatter((text, offset) -> FormattedCharSequence.forward("•".repeat(text.length()), Style.EMPTY));
		addRenderableWidget(key);

		addRenderableWidget(Button.builder(Component.literal("Save"), button -> save())
				.bounds(left, y + 56, width / 2 - 2, 20).build());
		addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(left + width / 2 + 2, y + 56, width / 2 - 2, 20).build());
	}

	/** Picks the AI by its name ("cloud", "gemini" or "ollama"). */
	public void chooseProvider(String provider) {
		providerIndex = Math.max(0, PROVIDERS.indexOf(provider));
		providerButton.setMessage(providerLabel());
	}

	private Component providerLabel() {
		return Component.literal("AI: " + LABELS.get(providerIndex) + " (click to change)");
	}

	@Override
	protected void setInitialFocus() {
		setInitialFocus(key);
	}

	private void save() {
		ClientPlayNetworking.send(new SetKeyPayload(PROVIDERS.get(providerIndex), key.getValue().trim()));
		key.setValue("");
		onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation()) {
			save();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int top = this.height / 2 - 60;
		graphics.centeredText(this.font, this.title, this.width / 2, top, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.literal("Your key stays on this world's computer."), this.width / 2,
				top + 12, 0xFFAAAAAA);
		if (!Minecraft.getInstance().isLocalServer()) {
			graphics.centeredText(this.font, Component.literal("This is a multiplayer server: the key is sent to it."),
					this.width / 2, this.height / 2 + 56, 0xFFFFAA00);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
