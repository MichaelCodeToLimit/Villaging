package com.villaging.villager;

import java.util.List;

/** Which text of the on-screen conversation is highlighted, and what it copies to the clipboard. Pure logic. */
public final class ChatSelection {
	/** One wrapped line of the conversation; lines with the same {@code entry} belong to one message. */
	public record Row(String text, int entry) {
	}

	public record Pos(int row, int col) {
		boolean before(Pos other) {
			return row < other.row || (row == other.row && col < other.col);
		}
	}

	private ChatSelection() {
	}

	/** The text between two points in either order. Wrapped lines of one message join with a space, messages with a newline. */
	public static String selectedText(List<Row> rows, Pos a, Pos b) {
		if (rows.isEmpty() || a == null || b == null) {
			return "";
		}
		Pos from = b.before(a) ? b : a;
		Pos to = b.before(a) ? a : b;
		int firstRow = Math.max(0, from.row());
		int lastRow = Math.min(rows.size() - 1, to.row());
		StringBuilder out = new StringBuilder();
		for (int r = firstRow; r <= lastRow; r++) {
			String text = rows.get(r).text();
			int start = r == from.row() ? clamp(from.col(), text.length()) : 0;
			int end = r == to.row() ? clamp(to.col(), text.length()) : text.length();
			String piece = end > start ? text.substring(start, end) : "";
			if (r > firstRow) {
				boolean sameMessage = rows.get(r - 1).entry() == rows.get(r).entry();
				boolean spaceAlready = out.length() > 0 && out.charAt(out.length() - 1) == ' ' || piece.startsWith(" ");
				out.append(sameMessage ? (spaceAlready ? "" : " ") : "\n");
			}
			out.append(piece);
		}
		return out.toString();
	}

	/** The start and end columns of the word (run of non-space characters) at {@code col}; empty on a space. */
	public static int[] wordAt(String text, int col) {
		if (text.isEmpty()) {
			return new int[] {0, 0};
		}
		int at = Math.min(Math.max(col, 0), text.length() - 1);
		if (Character.isWhitespace(text.charAt(at))) {
			return new int[] {at, at};
		}
		int start = at;
		while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
			start--;
		}
		int end = at + 1;
		while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
			end++;
		}
		return new int[] {start, end};
	}

	/** Start and end points covering every line of the message that row {@code row} belongs to. */
	public static Pos[] messageAround(List<Row> rows, int row) {
		if (rows.isEmpty()) {
			return new Pos[] {new Pos(0, 0), new Pos(0, 0)};
		}
		int at = Math.min(Math.max(row, 0), rows.size() - 1);
		int entry = rows.get(at).entry();
		int first = at;
		while (first > 0 && rows.get(first - 1).entry() == entry) {
			first--;
		}
		int last = at;
		while (last < rows.size() - 1 && rows.get(last + 1).entry() == entry) {
			last++;
		}
		return new Pos[] {new Pos(first, 0), new Pos(last, rows.get(last).text().length())};
	}

	private static int clamp(int col, int length) {
		return Math.min(Math.max(col, 0), length);
	}
}
