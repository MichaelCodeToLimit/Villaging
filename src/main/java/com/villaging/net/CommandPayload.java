package com.villaging.net;

import com.villaging.Villaging;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: the king's typed message for a villager. */
public record CommandPayload(int villagerId, String message) implements CustomPacketPayload {
	public static final Type<CommandPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "command"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CommandPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CommandPayload::villagerId,
			ByteBufCodecs.stringUtf8(1000), CommandPayload::message,
			CommandPayload::new);

	@Override
	public Type<CommandPayload> type() {
		return TYPE;
	}
}
