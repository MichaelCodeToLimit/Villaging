package com.villaging.net;

import com.villaging.Villaging;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: use this provider and API key. The key must never be logged or echoed. */
public record SetKeyPayload(String provider, String key) implements CustomPacketPayload {
	public static final int MAX_KEY = 500;

	public static final Type<SetKeyPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "set_key"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SetKeyPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(20), SetKeyPayload::provider,
			ByteBufCodecs.stringUtf8(MAX_KEY), SetKeyPayload::key,
			SetKeyPayload::new);

	@Override
	public Type<SetKeyPayload> type() {
		return TYPE;
	}

	@Override
	public String toString() {
		return "SetKeyPayload[provider=" + provider + ", key=<hidden>]";
	}
}
