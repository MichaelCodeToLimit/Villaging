package com.villaging.net;

import com.villaging.Villaging;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: a villager asks the king a question; show the dialog. */
public record AskPayload(long questionId, int villagerId, String villagerName, String question, List<String> options)
		implements CustomPacketPayload {
	public static final Type<AskPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "ask"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AskPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, AskPayload::questionId,
			ByteBufCodecs.VAR_INT, AskPayload::villagerId,
			ByteBufCodecs.stringUtf8(100), AskPayload::villagerName,
			ByteBufCodecs.stringUtf8(300), AskPayload::question,
			ByteBufCodecs.stringUtf8(60).apply(ByteBufCodecs.list(4)), AskPayload::options,
			AskPayload::new);

	@Override
	public Type<AskPayload> type() {
		return TYPE;
	}
}
