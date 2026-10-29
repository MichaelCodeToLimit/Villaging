package com.villaging.client;

import com.villaging.net.CloseChatPayload;
import com.villaging.net.CommandPayload;
import com.villaging.villager.ChatSelection;
import com.villaging.villager.ChatSelection.Pos;
import com.villaging.villager.ChatTranscript;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/**
 * A full conversation with a villager: the recent history above, a box below. Enter sends and the screen stays open
 * so the talk can go on; the villager's replies appear as they arrive. Escape closes it, and the villager then moves
 * again. PageUp/PageDown or the mouse wheel scroll. Drag over the conversation to select text (double-click a word,
 * triple-click a message), Ctrl+C copies it, and right-click copies the message under the cursor. Ctrl+V pastes into
 * the box.
 */
public class VillagerChatScreen extends Screen {
	private static final int MAX_LENGTH = 500;
	private static final int KING_COLOR = 0xFFFFD27F;
	private static final int VILLAGER_COLOR = 0xFFB8F0B8;
	private static final int SELECTION_COLOR = 0xA03C78D8;
	private static final long MULTI_CLICK_MILLIS = 450;
	private static final long COPIED_NOTICE_MILLIS = 1200;

	private record Entry(boolean fromKing, String text) {
	}

	private final int villagerId;
	private final String villagerName;
	private final List<Entry> entries = new ArrayList<>();
	private List<ChatSelection.Row> rows = List.of();
	private List<Integer> rowColors = List.of();
	private int scroll;
	private EditBox input;
	private Pos anchor;
	private Pos head;
	private boolean dragging;
	private long lastClickAt;
	private int clickRun;
	private long copiedAt;

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
		clearSelection();
		rebuildRows();
	}

	/** The highlighted text ("" when nothing is selected). */
	public String selectedText() {
		return anchor == null || head == null ? "" : ChatSelection.selectedText(rows, anchor, head);
	}

	/** Highlights text between two points of the wrapped conversation (used by the in-game checks). */
	public void select(int fromRow, int fromCol, int toRow, int toCol) {
		anchor = new Pos(fromRow, fromCol);
		head = new Pos(toRow, toCol);
	}

	/** What is typed in the box right now (used by the in-game checks). */
	public String typed() {
		return input == null ? "" : input.getValue();
	}

	/** Screen position of a character of a wrapped line that is on screen, for the in-game checks to click and drag on. */
	public double[] pointAt(int row, int col) {
		String text = rows.get(row).text();
		double x = left() + this.font.width(text.substring(0, Math.min(col, text.length()))) + 1;
		double y = firstRowY() + (row - firstShownRow()) * this.font.lineHeight + this.font.lineHeight / 2.0;
		return new double[] {x, y};
	}

	/** Number of wrapped lines currently in the conversation. */
	public int rowCount() {
		return rows.size();
	}

	/** Copies the highlighted text to the clipboard; false when nothing is selected. */
	public boolean copySelection() {
		String text = selectedText();
		if (text.isEmpty()) {
			return false;
		}
		if (this.minecraft != null) {
			this.minecraft.keyboardHandler.setClipboard(text);
		}
		copiedAt = System.currentTimeMillis();
		return true;
	}

	private void clearSelection() {
		anchor = null;
		head = null;
		dragging = false;
	}

	private int panelWidth() {
		return Math.min(this.width - 40, 360);
	}

	private int left() {
		return (this.width - panelWidth()) / 2;
	}

	private int bottom() {
		return this.height - 40;
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
		List<ChatSelection.Row> built = new ArrayList<>();
		List<Integer> colors = new ArrayList<>();
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			String prefix = e.fromKing() ? "You: " : villagerName + ": ";
			int color = e.fromKing() ? KING_COLOR : VILLAGER_COLOR;
			for (FormattedText line : this.font.getSplitter().splitLines(prefix + e.text(), textWidth, Style.EMPTY)) {
				built.add(new ChatSelection.Row(line.getString(), i));
				colors.add(color);
			}
		}
		rows = built;
		rowColors = colors;
	}

	private int visibleRows() {
		return Math.max(1, (this.height - 34 - 8 - 24) / this.font.lineHeight);
	}

	private int firstShownRow() {
		return Math.max(0, rows.size() - visibleRows() - scroll);
	}

	private int lastShownRow() {
		return Math.min(rows.size(), firstShownRow() + visibleRows());
	}

	private int firstRowY() {
		return bottom() - (lastShownRow() - firstShownRow()) * this.font.lineHeight;
	}

	private void scrollBy(int delta) {
		int max = Math.max(0, rows.size() - visibleRows());
		scroll = Math.max(0, Math.min(max, scroll + delta));
	}

	private boolean overConversation(double x, double y) {
		return !rows.isEmpty() && x >= left() - 4 && x <= left() + panelWidth() + 4 && y >= firstRowY() && y < bottom();
	}

	/** The row and character under the mouse, clamped to the lines that are on screen. */
	private Pos positionAt(double x, double y) {
		int shown = (int) Math.floor((y - firstRowY()) / this.font.lineHeight);
		int row = Math.max(firstShownRow(), Math.min(lastShownRow() - 1, firstShownRow() + shown));
		String text = rows.get(row).text();
		int col = this.font.getSplitter().plainIndexAtWidth(text, Math.max(0, (int) (x - left())), Style.EMPTY);
		return new Pos(row, Math.max(0, Math.min(text.length(), col)));
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
		clearSelection();
		rebuildRows();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (!overConversation(event.x(), event.y())) {
			clearSelection();
			return super.mouseClicked(event, doubleClick);
		}
		Pos at = positionAt(event.x(), event.y());
		if (event.button() == 1) { // right-click copies the whole message
			Pos[] message = ChatSelection.messageAround(rows, at.row());
			anchor = message[0];
			head = message[1];
			copySelection();
			return true;
		}
		if (event.button() != 0) {
			return true;
		}
		long now = System.currentTimeMillis();
		clickRun = now - lastClickAt <= MULTI_CLICK_MILLIS ? clickRun + 1 : 1;
		lastClickAt = now;
		if (clickRun >= 3) {
			Pos[] message = ChatSelection.messageAround(rows, at.row());
			anchor = message[0];
			head = message[1];
			dragging = false;
		} else if (clickRun == 2) {
			int[] word = ChatSelection.wordAt(rows.get(at.row()).text(), at.col());
			anchor = new Pos(at.row(), word[0]);
			head = new Pos(at.row(), word[1]);
			dragging = false;
		} else {
			anchor = at;
			head = at;
			dragging = true;
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging && event.button() == 0 && !rows.isEmpty()) {
			head = positionAt(event.x(), event.y());
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging) {
			dragging = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isConfirmation()) {
			send();
			return true;
		}
		if (event.isCopy() && copySelection()) {
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
	public boolean charTyped(CharacterEvent event) {
		clearSelection();
		return super.charTyped(event);
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
		int left = left();
		int top = 8;
		int bottom = bottom();
		graphics.fill(left - 4, top, left + width + 4, bottom + 4, 0xB0000000);
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		graphics.centeredText(this.font, Component.literal(villagerName), this.width / 2, top + 4, 0xFFFFFFFF);
		int first = firstShownRow();
		int last = lastShownRow();
		int y = firstRowY();
		boolean selecting = anchor != null && head != null && !anchor.equals(head);
		boolean headFirst = selecting && (head.row() < anchor.row() || (head.row() == anchor.row() && head.col() < anchor.col()));
		Pos from = headFirst ? head : anchor;
		Pos to = headFirst ? anchor : head;
		for (int i = first; i < last; i++) {
			String text = rows.get(i).text();
			if (selecting && i >= from.row() && i <= to.row()) {
				int start = i == from.row() ? Math.min(from.col(), text.length()) : 0;
				int end = i == to.row() ? Math.min(to.col(), text.length()) : text.length();
				if (end > start) {
					int x1 = left + this.font.width(text.substring(0, start));
					int x2 = left + this.font.width(text.substring(0, end));
					graphics.fill(x1, y, x2, y + this.font.lineHeight, SELECTION_COLOR);
				}
			}
			graphics.text(this.font, text, left, y, rowColors.get(i));
			y += this.font.lineHeight;
		}
		if (rows.isEmpty()) {
			graphics.centeredText(this.font, Component.literal("Say something to " + villagerName + "."), this.width / 2,
					bottom - 12, 0xFFAAAAAA);
		}
		if (System.currentTimeMillis() - copiedAt < COPIED_NOTICE_MILLIS) {
			graphics.centeredText(this.font, Component.literal("Copied"), this.width / 2, bottom - 12, 0xFF7CFC7C);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
