package com.villaging.net;

import com.villaging.Villaging;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: open the API key box, starting on this provider ("cloud", "gemini" or "ollama"). */
public record OpenKeyPayload(String provider) implements CustomPacketPayload {
	public static final Type<OpenKeyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "open_key"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenKeyPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(20), OpenKeyPayload::provider,
			OpenKeyPayload::new);

	@Override
	public Type<OpenKeyPayload> type() {
		return TYPE;
	}
}
