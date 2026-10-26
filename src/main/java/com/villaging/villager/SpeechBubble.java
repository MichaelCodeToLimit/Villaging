package com.villaging.villager;

/** Formats the temporary text shown over a villager's head, in a way we can recognise and undo later. */
public final class SpeechBubble {
	public static final int MAX_TEXT = 60;
	private static final char OPEN = '“';
	private static final char CLOSE = '”';

	private SpeechBubble() {
	}

	public static String format(String text) {
		String shown = text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) + "…" : text;
		return OPEN + shown + CLOSE;
	}

	/** True only for text produced by {@link #format}, so a name the player chose is never mistaken for a bubble. */
	public static boolean isBubble(String name) {
		return name != null && name.length() >= 2 && name.charAt(0) == OPEN && name.charAt(name.length() - 1) == CLOSE;
	}
}
