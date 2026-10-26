package com.villaging.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;

/** Per-villager persisted data: whether it is smart, its name, and its recent conversation. */
public record VillagerBrain(boolean smart, String name, List<String> memory) {
	public static final int MEMORY_LINES = 20;

	public static final VillagerBrain DEFAULT = new VillagerBrain(false, "", List.of());

	public static final Codec<VillagerBrain> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("smart", false).forGetter(VillagerBrain::smart),
			Codec.STRING.optionalFieldOf("name", "").forGetter(VillagerBrain::name),
			Codec.STRING.listOf().optionalFieldOf("memory", List.of()).forGetter(VillagerBrain::memory))
			.apply(i, VillagerBrain::new));

	public VillagerBrain {
		memory = List.copyOf(memory);
	}

	public VillagerBrain makeSmart(String newName) {
		return new VillagerBrain(true, newName, memory);
	}

	public VillagerBrain withMemory(String line) {
		List<String> lines = new ArrayList<>(memory);
		lines.add(line);
		while (lines.size() > MEMORY_LINES) {
			lines.remove(0);
		}
		return new VillagerBrain(smart, name, lines);
	}
}
