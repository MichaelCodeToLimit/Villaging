package com.villaging.client;

import com.villaging.net.CloseChatPayload;
import com.villaging.net.CommandPayload;
import com.villaging.villager.ChatTranscript;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * A full conversation with a villager: the recent history above, a box below. Enter sends and the screen stays open
 * so the talk can go on; the villager's replies appear as they arrive. Escape closes it, and the villager then moves
 * again. PageUp/PageDown or the mouse wheel scroll.
 */
public class VillagerChatScreen extends Screen {
	private static final int MAX_LENGTH = 500;
	private static final int KING_COLOR = 0xFFFFD27F;
	private static final int VILLAGER_COLOR = 0xFFB8F0B8;

	private record Entry(boolean fromKing, String text) {
	}

	private record Row(FormattedCharSequence text, int color) {
	}

	private final int villagerId;
	private final String villagerName;
	private final List<Entry> entries = new ArrayList<>();
	private List<Row> rows = List.of();
	private int scroll;
	private EditBox input;

	public VillagerChatScreen(int villagerId, String villagerName) {
		this(villagerId, villagerName, List.of());
	}

	/** @param history the saved conversation as "king: ..." / "me: ..." lines */
	public VillagerChatScreen(int villagerId, String villagerName, List<String> history) {
		super(Component.literal("Speak to " + villagerName));
		this.villagerId = villagerId;
		this.villagerName = villagerName;
		for (ChatTranscript.Line line : ChatTranscript.parse(history)) {
			entries.add(new Entry(line.fromKing(), line.text()));
		}
	}

	public int villagerId() {
		return villagerId;
	}

	/** The conversation as shown, one "You: ..." or "Name: ..." string per message (used by the in-game checks). */
	public List<String> transcript() {
		List<String> out = new ArrayList<>();
		for (Entry e : entries) {
			out.add((e.fromKing() ? "You: " : villagerName + ": ") + e.text());
		}
		return out;
	}

	public void addVillagerLine(String text) {
		entries.add(new Entry(false, text));
		scroll = 0;
		rebuildRows();
	}

	private int panelWidth() {
		return Math.min(this.width - 40, 360);
	}

	@Override
	protected void init() {
		int width = panelWidth();
		input = new EditBox(this.font, (this.width - width) / 2, this.height - 34, width, 20,
				Component.literal("Speak to " + villagerName));
		input.setMaxLength(MAX_LENGTH);
		input.setHint(Component.literal("Tell " + villagerName + " anything... (Enter sends, Esc closes)"));
		addRenderableWidget(input);
		rebuildRows();
	}

	@Override
	protected void setInitialFocus() {
		setInitialFocus(input);
	}

	private void rebuildRows() {
		if (this.font == null) {
			return;
		}
		int textWidth = panelWidth() - 8;
		List<Row> built = new ArrayList<>();
		for (Entry e : entries) {
			String prefix = e.fromKing() ? "You: " : villagerName + ": ";
			int color = e.fromKing() ? KING_COLOR : VILLAGER_COLOR;
			for (FormattedCharSequence line : this.font.split(FormattedText.of(prefix + e.text()), textWidth)) {
				built.add(new Row(line, color));
			}
		}
		rows = built;
	}

	private int visibleRows() {
		return Math.max(1, (this.height - 34 - 8 - 24) / this.font.lineHeight);
	}

	private void scrollBy(int delta) {
		int max = Math.max(0, rows.size() - visibleRows());
		scroll = Math.max(0, Math.min(max, scroll + delta));
	}

	private void send() {
		String text = input.getValue().trim();
		if (text.isEmpty()) {
			return;
		}
		ClientPlayNetworking.send(new CommandPayload(villagerId, text));
		entries.add(new Entry(true, text));
		input.setValue("");
		scroll = 0;
		rebuildRows();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation()) {
			send();
			return true;
		}
		if (event.key() == 266) { // Page Up
			scrollBy(visibleRows() - 1);
			return true;
		}
		if (event.key() == 267) { // Page Down
			scrollBy(-(visibleRows() - 1));
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		scrollBy((int) Math.signum(scrollY) * 3);
		return true;
	}

	/** Called whenever the screen goes away (Escape, another screen, disconnect): the villager may move again. */
	@Override
	public void removed() {
		ClientPlayNetworking.send(new CloseChatPayload(villagerId));
		super.removed();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		int width = panelWidth();
		int left = (this.width - width) / 2;
		int top = 8;
		int bottom = this.height - 40;
		graphics.fill(left - 4, top, left + width + 4, bottom + 4, 0xB0000000);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, Component.literal(villagerName), this.width / 2, top + 4, 0xFFFFFFFF);
		int visible = visibleRows();
		int first = Math.max(0, rows.size() - visible - scroll);
		int last = Math.min(rows.size(), first + visible);
		int y = bottom - (last - first) * this.font.lineHeight;
		for (int i = first; i < last; i++) {
			Row row = rows.get(i);
			graphics.text(this.font, row.text(), left, y, row.color());
			y += this.font.lineHeight;
		}
		if (rows.isEmpty()) {
			graphics.centeredText(this.font, Component.literal("Say something to " + villagerName + "."), this.width / 2,
					bottom - 12, 0xFFAAAAAA);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
