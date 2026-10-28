package com.villaging.net;

import com.villaging.Villaging;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: open the chat screen for this villager, showing the recent conversation ("king: ..." / "me: ..."). */
public record OpenChatPayload(int villagerId, String villagerName, List<String> history) implements CustomPacketPayload {
	public static final Type<OpenChatPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Villaging.MOD_ID, "open_chat"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenChatPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, OpenChatPayload::villagerId,
			ByteBufCodecs.stringUtf8(100), OpenChatPayload::villagerName,
			ByteBufCodecs.stringUtf8(500).apply(ByteBufCodecs.list(40)), OpenChatPayload::history,
			OpenChatPayload::new);

	@Override
	public Type<OpenChatPayload> type() {
		return TYPE;
	}
}
