package com.villaging.client;

import com.villaging.guide.GuideContent;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;

/** The in-game guide: one page at a time with Back and Next. Closing it counts as having seen it. */
public class GuideScreen extends Screen {
	private static final List<GuideContent.Page> PAGES = GuideContent.pages();

	private int page;
	private Button back;
	private Button next;

	public GuideScreen(int page) {
		super(Component.literal("Villaging guide"));
		this.page = Math.max(0, Math.min(page, PAGES.size() - 1));
	}

	@Override
	protected void init() {
		int width = Math.min(this.width - 40, 320);
		int left = (this.width - width) / 2;
		int y = this.height - 28;
		back = Button.builder(Component.literal("Back"), button -> turn(-1)).bounds(left, y, 70, 20).build();
		next = Button.builder(Component.literal("Next"), button -> turn(1)).bounds(left + width - 70, y, 70, 20).build();
		addRenderableWidget(back);
		addRenderableWidget(next);
		addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
				.bounds(left + width / 2 - 35, y, 70, 20).build());
		refresh();
	}

	/** Jumps to a page (clamped to the guide's length). */
	public void goToPage(int target) {
		page = Math.max(0, Math.min(target, PAGES.size() - 1));
		if (back != null) {
			refresh();
		}
	}

	private void turn(int delta) {
		int target = page + delta;
		if (target >= PAGES.size()) {
			onClose();
			return;
		}
		page = Math.max(0, target);
		refresh();
	}

	private void refresh() {
		back.active = page > 0;
		next.setMessage(Component.literal(page == PAGES.size() - 1 ? "Done" : "Next"));
	}

	@Override
	public void onClose() {
		GuideSeen.markSeen();
		super.onClose();
		VillagingClient.afterGuide(this.minecraft);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		int width = Math.min(this.width - 40, 320);
		int left = (this.width - width) / 2;
		GuideContent.Page current = PAGES.get(page);
		graphics.centeredText(this.font, Component.literal(current.title()), this.width / 2, 16, 0xFFFFD700);
		graphics.textWithWordWrap(this.font, FormattedText.of(current.body()), left, 36, width, 0xFFFFFFFF);
		graphics.centeredText(this.font, Component.literal((page + 1) + " / " + PAGES.size()), this.width / 2,
				this.height - 40, 0xFFAAAAAA);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
