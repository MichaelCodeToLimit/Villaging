package com.villaging.build;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Optional;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/** Turns blueprint state text into real block states. */
public final class Blocks {
	private Blocks() {
	}

	/** The block state for text like {@code minecraft:oak_stairs[facing=north]}, or empty when the game has no such block or property. */
	public static Optional<BlockState> parse(ServerLevel level, String state) {
		try {
			return Optional.of(BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK), state, false).blockState());
		} catch (CommandSyntaxException | RuntimeException e) {
			return Optional.empty();
		}
	}

	/** The state turned clockwise (looking down) by {@code quarterTurns}, matching {@code Blueprint.rotated}. */
	public static BlockState rotate(BlockState state, int quarterTurns) {
		Rotation rotation = switch (Math.floorMod(quarterTurns, 4)) {
			case 1 -> Rotation.CLOCKWISE_90;
			case 2 -> Rotation.CLOCKWISE_180;
			case 3 -> Rotation.COUNTERCLOCKWISE_90;
			default -> Rotation.NONE;
		};
		return state.rotate(rotation);
	}
}
