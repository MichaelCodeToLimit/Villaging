package com.villaging.team;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.UUID;

/** How a {@link Team} is saved with each member. */
public final class TeamData {
	private static final Codec<UUID> ID = Codec.STRING.xmap(UUID::fromString, UUID::toString);

	public static final Codec<Team> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("name").forGetter(Team::name),
			ID.fieldOf("leader").forGetter(Team::leader),
			ID.listOf().fieldOf("members").forGetter(Team::members)).apply(i, Team::new));

	private TeamData() {
	}
}
