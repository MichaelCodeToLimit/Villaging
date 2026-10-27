package com.villaging.ai;

import java.util.Arrays;
import java.util.Optional;

/** The kinds of creature that can be made smart. Pure data, so the AI layer can talk about them. */
public enum SmartKind {
	VILLAGER("villager", "villager",
			"You are an ordinary village person: friendly, practical, and proud to serve the king."),
	IRON_GOLEM("iron_golem", "iron golem",
			"You are a big, strong iron golem: slow to anger, hard to hurt, and you hit very hard. You guard and protect."),
	COPPER_GOLEM("copper_golem", "copper golem",
			"You are a small, tidy copper golem: cheerful, a little fussy, and you love carrying and sorting things.");

	private final String key;
	private final String label;
	private final String personality;

	SmartKind(String key, String label, String personality) {
		this.key = key;
		this.label = label;
		this.personality = personality;
	}

	/** The name used in tool arguments. */
	public String key() {
		return key;
	}

	/** The name used in prose ("a smart iron golem"). */
	public String label() {
		return label;
	}

	public String personality() {
		return personality;
	}

	public static Optional<SmartKind> fromKey(String key) {
		return Arrays.stream(values()).filter(k -> k.key.equals(key)).findFirst();
	}
}
