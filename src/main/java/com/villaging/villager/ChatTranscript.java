package com.villaging.villager;

import java.util.ArrayList;
import java.util.List;

/** Turns a villager's saved memory ("king: ..." / "me: ...") into lines for the chat screen. Pure. */
public final class ChatTranscript {
	public static final int MAX_LINES = 20;
	public static final int MAX_LINE_LENGTH = 400;

	public record Line(boolean fromKing, String text) {
	}

	private ChatTranscript() {
	}

	/** The last {@link #MAX_LINES} memory lines, each shortened so it fits in a network packet. */
	public static List<String> forClient(List<String> memory) {
		List<String> tail = memory.subList(Math.max(0, memory.size() - MAX_LINES), memory.size());
		List<String> out = new ArrayList<>();
		for (String line : tail) {
			out.add(line.length() > MAX_LINE_LENGTH ? line.substring(0, MAX_LINE_LENGTH - 1) + "…" : line);
		}
		return out;
	}

	/** Speaker lines for the screen; lines that are neither the king's nor the villager's own are skipped. */
	public static List<Line> parse(List<String> memory) {
		List<Line> lines = new ArrayList<>();
		for (String raw : memory) {
			boolean king = raw.startsWith("king:");
			if (!king && !raw.startsWith("me:")) {
				continue;
			}
			String text = raw.substring(king ? 5 : 3).trim();
			if (!text.isEmpty()) {
				lines.add(new Line(king, text));
			}
		}
		return lines;
	}
}
