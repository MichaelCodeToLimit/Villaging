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

	/** A cancelled run was superseded by a newer command, so its replies are dropped. */
	public static VillagerBrain withReplies(VillagerBrain brain, List<ChatMessage> produced, boolean cancelled) {
		if (cancelled) {
			return brain;
		}
		VillagerBrain updated = brain;
		for (ChatMessage m : produced) {
			if (m.role() == ChatMessage.Role.ASSISTANT && !m.text().isBlank()) {
				updated = updated.withMemory(SELF_PREFIX + m.text());
			}
		}
		return updated;
	}
}
