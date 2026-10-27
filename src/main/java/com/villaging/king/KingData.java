package com.villaging.king;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.UUID;

/**
 * Who the king of this world is, if anyone, and what villagers call them by default.
 * Immutable; stored as a persistent world attachment.
 */
public record KingData(Optional<UUID> king, String title) {
	public static final String DEFAULT_TITLE = "King";

	public static final KingData EMPTY = new KingData(Optional.empty(), DEFAULT_TITLE);

	public static final Codec<KingData> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.xmap(UUID::fromString, UUID::toString).optionalFieldOf("king").forGetter(KingData::king),
			Codec.STRING.optionalFieldOf("title", DEFAULT_TITLE).forGetter(KingData::title))
			.apply(i, KingData::new));

	public boolean isKing(UUID player) {
		return king.isPresent() && king.get().equals(player);
	}

	/** The new state if {@code player} is (or becomes) king; empty when someone else already is. */
	public Optional<KingData> claim(UUID player) {
		if (king.isEmpty()) {
			return Optional.of(new KingData(Optional.of(player), title));
		}
		return isKing(player) ? Optional.of(this) : Optional.empty();
	}

	public KingData withTitle(String newTitle) {
		return new KingData(king, newTitle);
	}

	public KingData abdicate() {
		return EMPTY;
	}
}
