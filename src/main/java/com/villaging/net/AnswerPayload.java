package com.villaging.net;

import com.villaging.Villaging;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: the king's answer to a question. An empty answer cancels it. */
public record AnswerPayload(long questionId, String answer) implements CustomPacketPayload {
	public static final Type<AnswerPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "answer"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AnswerPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, AnswerPayload::questionId,
			ByteBufCodecs.stringUtf8(300), AnswerPayload::answer,
			AnswerPayload::new);

	@Override
	public Type<AnswerPayload> type() {
		return TYPE;
	}
}
