package com.villaging.villager;

import com.villaging.ai.ChatMessage;
import java.util.ArrayList;
import java.util.List;

/** Rules for turning a villager's stored memory lines into chat history and back. Pure logic. */
public final class Conversation {
	static final String KING_PREFIX = "king: ";
	static final String SELF_PREFIX = "me: ";

	private Conversation() {
	}

	public static List<ChatMessage> historyFrom(VillagerBrain brain) {
		List<ChatMessage> history = new ArrayList<>();
		for (String line : brain.memory()) {
			if (line.startsWith(KING_PREFIX)) {
				history.add(ChatMessage.user(line.substring(KING_PREFIX.length())));
			} else if (line.startsWith(SELF_PREFIX) && !history.isEmpty()) {
				history.add(ChatMessage.assistant(line.substring(SELF_PREFIX.length()), List.of()));
			}
		}
		return history;
	}

	/** Called the moment a command is accepted, so later commands always see it in the right order. */
	public static VillagerBrain withKingMessage(VillagerBrain brain, String message) {
		return brain.withMemory(KING_PREFIX + message);
	}

	/**
	 * Remembers a line the villager has just spoken to the king. This happens the moment it is said, so it is kept
	 * even if the run that produced it is later cancelled by a newer command or the chat is closed.
	 */
	public static VillagerBrain withVillagerLine(VillagerBrain brain, String text) {
		return text == null || text.isBlank() ? brain : brain.withMemory(SELF_PREFIX + text);
	}
}
