package com.villaging.net;

import com.villaging.Villaging;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: a villager said this; an open chat screen with it shows the line. */
public record ChatLinePayload(int villagerId, String text) implements CustomPacketPayload {
	public static final Type<ChatLinePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "chat_line"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ChatLinePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, ChatLinePayload::villagerId,
			ByteBufCodecs.stringUtf8(2000), ChatLinePayload::text,
			ChatLinePayload::new);

	@Override
	public Type<ChatLinePayload> type() {
		return TYPE;
	}
}
