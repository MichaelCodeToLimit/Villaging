package com.villaging.net;

import com.villaging.Villaging;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: open the chat box for this villager. */
public record OpenChatPayload(int villagerId, String villagerName) implements CustomPacketPayload {
	public static final Type<OpenChatPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "open_chat"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenChatPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, OpenChatPayload::villagerId,
			ByteBufCodecs.stringUtf8(100), OpenChatPayload::villagerName,
			OpenChatPayload::new);

	@Override
	public Type<OpenChatPayload> type() {
		return TYPE;
	}
}
