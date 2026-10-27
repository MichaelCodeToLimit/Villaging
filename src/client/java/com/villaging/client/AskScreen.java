package com.villaging.client;

import com.villaging.net.AnswerPayload;
import com.villaging.net.AskPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

/** A villager's question: a few option buttons and a box for the king's own answer. Escape cancels. */
public class AskScreen extends Screen {
	private final AskPayload ask;
	private EditBox custom;
	private boolean answered;

	public AskScreen(AskPayload ask) {
		super(Component.literal(ask.villagerName() + " asks"));
		this.ask = ask;
	}

	@Override
	protected void init() {
		int width = Math.min(this.width - 40, 260);
		int left = (this.width - width) / 2;
		int y = this.height / 2 - 20;
		for (String option : ask.options()) {
			addRenderableWidget(Button.builder(Component.literal(option), button -> choose(option))
					.bounds(left, y, width, 20).build());
			y += 24;
		}
		custom = new EditBox(this.font, left, y + 4, width, 20, Component.literal("Your answer"));
		custom.setMaxLength(300);
		custom.setHint(Component.literal("Or answer in your own words..."));
		addRenderableWidget(custom);
	}

	@Override
	protected void setInitialFocus() {
		setInitialFocus(custom);
	}

	/** Sends the chosen answer once and closes. Buttons and Enter both come here. */
	public void choose(String answer) {
		if (!answered) {
			answered = true;
			ClientPlayNetworking.send(new AnswerPayload(ask.questionId(), answer));
		}
		onClose();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation()) {
			String text = custom.getValue().trim();
			if (!text.isEmpty()) {
				choose(text);
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		if (!answered) {
			answered = true;
			ClientPlayNetworking.send(new AnswerPayload(ask.questionId(), ""));
		}
		super.onClose();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, Component.literal(ask.question()), this.width / 2,
				this.height / 2 - 40, 0xFFFFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
