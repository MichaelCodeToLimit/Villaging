package com.villaging.king;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;

/** Who the king of this world is, if anyone. Immutable; stored as a persistent world attachment. */
public record KingData(Optional<UUID> king) {
	public static final KingData EMPTY = new KingData(Optional.empty());

	public static final Codec<KingData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.xmap(UUID::fromString, UUID::toString).optionalFieldOf("king").forGetter(KingData::king))
			.apply(i, KingData::new));

	public boolean isKing(UUID player) {
		return king.isPresent() && king.get().equals(player);
	}

	/** The new state if {@code player} is (or becomes) king; empty when someone else already is. */
	public Optional<KingData> claim(UUID player) {
		if (king.isEmpty()) {
			return Optional.of(new KingData(Optional.of(player)));
		}
		return isKing(player) ? Optional.of(this) : Optional.empty();
	}

	public KingData abdicate() {
		return EMPTY;
	}
}
