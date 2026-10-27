package com.villaging.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Per-villager persisted data: whether it is smart, its name, its recent conversation, which risky-act
 * categories the king has told it not to ask about, and whether it is a commander.
 */
public record VillagerBrain(boolean smart, String name, List<String> memory, Set<String> askOff, boolean commander) {
	public static final int MEMORY_LINES = 20;

	public static final VillagerBrain DEFAULT = new VillagerBrain(false, "", List.of());

	public static final Codec<VillagerBrain> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("smart", false).forGetter(VillagerBrain::smart),
			Codec.STRING.optionalFieldOf("name", "").forGetter(VillagerBrain::name),
			Codec.STRING.listOf().optionalFieldOf("memory", List.of()).forGetter(VillagerBrain::memory),
			Codec.STRING.listOf().optionalFieldOf("askOff", List.of()).forGetter(b -> List.copyOf(b.askOff())),
			Codec.BOOL.optionalFieldOf("commander", false).forGetter(VillagerBrain::commander))
			.apply(i, (smart, name, memory, askOff, commander) ->
					new VillagerBrain(smart, name, memory, new LinkedHashSet<>(askOff), commander)));

	public VillagerBrain {
		memory = List.copyOf(memory);
		askOff = Set.copyOf(askOff);
	}

	public VillagerBrain(boolean smart, String name, List<String> memory) {
		this(smart, name, memory, Set.of(), false);
	}

	public VillagerBrain makeSmart(String newName) {
		return new VillagerBrain(true, newName, memory, askOff, commander);
	}

	public VillagerBrain withMemory(String line) {
		List<String> lines = new ArrayList<>(memory);
		lines.add(line);
		while (lines.size() > MEMORY_LINES) {
			lines.remove(0);
		}
		return new VillagerBrain(smart, name, lines, askOff, commander);
	}

	public VillagerBrain withAskOff(String category, boolean off) {
		Set<String> updated = new LinkedHashSet<>(askOff);
		if (off) {
			updated.add(category);
		} else {
			updated.remove(category);
		}
		return new VillagerBrain(smart, name, memory, updated, commander);
	}

	public VillagerBrain withCommander(boolean value) {
		return new VillagerBrain(smart, name, memory, askOff, value);
	}
}
