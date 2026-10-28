package com.villaging.net;

import com.villaging.Villaging;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: open the in-game guide (from /villaging guide). */
public record OpenGuidePayload() implements CustomPacketPayload {
	public static final Type<OpenGuidePayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "open_guide"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenGuidePayload> CODEC = StreamCodec.unit(new OpenGuidePayload());

	@Override
	public Type<OpenGuidePayload> type() {
		return TYPE;
	}
}
