package com.villaging.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-villager persisted data: whether it is smart, its name, its recent conversation, which risky-act
 * categories the king has told it not to ask about, whether it is a commander, and what it calls the king
 * ({@code kingTitle}; empty means the world's title).
 */
public record VillagerBrain(boolean smart, String name, List<String> memory, Set<String> askOff, boolean commander,
		String kingTitle) {
	public static final int MEMORY_LINES = 20;

	public static final VillagerBrain DEFAULT = new VillagerBrain(false, "", List.of());

	public static final Codec<VillagerBrain> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("smart", false).forGetter(VillagerBrain::smart),
			Codec.STRING.optionalFieldOf("name", "").forGetter(VillagerBrain::name),
			Codec.STRING.listOf().optionalFieldOf("memory", List.of()).forGetter(VillagerBrain::memory),
			Codec.STRING.listOf().optionalFieldOf("askOff", List.of()).forGetter(b -> List.copyOf(b.askOff())),
			Codec.BOOL.optionalFieldOf("commander", false).forGetter(VillagerBrain::commander),
			Codec.STRING.optionalFieldOf("kingTitle", "").forGetter(VillagerBrain::kingTitle))
			.apply(i, (smart, name, memory, askOff, commander, kingTitle) ->
					new VillagerBrain(smart, name, memory, new LinkedHashSet<>(askOff), commander, kingTitle)));

	public VillagerBrain {
		memory = List.copyOf(memory);
		askOff = Set.copyOf(askOff);
	}

	public VillagerBrain(boolean smart, String name, List<String> memory) {
		this(smart, name, memory, Set.of(), false, "");
	}

	public VillagerBrain makeSmart(String newName) {
		return new VillagerBrain(true, newName, memory, askOff, commander, kingTitle);
	}

	public VillagerBrain withName(String newName) {
		return new VillagerBrain(smart, newName, memory, askOff, commander, kingTitle);
	}

	public VillagerBrain withKingTitle(String title) {
		return new VillagerBrain(smart, name, memory, askOff, commander, title);
	}

	public VillagerBrain withMemory(String line) {
		List<String> lines = new ArrayList<>(memory);
		lines.add(line);
		while (lines.size() > MEMORY_LINES) {
			lines.remove(0);
		}
		return new VillagerBrain(smart, name, lines, askOff, commander, kingTitle);
	}

	public VillagerBrain withAskOff(String category, boolean off) {
		Set<String> updated = new LinkedHashSet<>(askOff);
		if (off) {
			updated.add(category);
		} else {
			updated.remove(category);
		}
		return new VillagerBrain(smart, name, memory, updated, commander, kingTitle);
	}

	public VillagerBrain withCommander(boolean value) {
		return new VillagerBrain(smart, name, memory, askOff, value, kingTitle);
	}
}
