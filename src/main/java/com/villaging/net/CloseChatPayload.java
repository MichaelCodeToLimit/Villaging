package com.villaging.net;

import com.villaging.Villaging;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: the chat screen with this villager was closed, so it may move again. */
public record CloseChatPayload(int villagerId) implements CustomPacketPayload {
	public static final Type<CloseChatPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "close_chat"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CloseChatPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CloseChatPayload::villagerId,
			CloseChatPayload::new);

	@Override
	public Type<CloseChatPayload> type() {
		return TYPE;
	}
}
