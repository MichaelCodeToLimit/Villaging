package com.villaging.king;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Reads the live game state of the king into the text described by {@link KingContext}. */
public final class KingContextReader {
	private static final double LOOK_RANGE = 20.0;

	private KingContextReader() {
	}

	public static String read(ServerPlayer king) {
		KingContext.LookedAt looked = null;
		HitResult hit = king.pick(LOOK_RANGE, 1.0f, false);
		if (hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK) {
			BlockPos pos = blockHit.getBlockPos();
			String name = king.level().getBlockState(pos).getBlock().getName().getString();
			looked = new KingContext.LookedAt(name, pos.getX(), pos.getY(), pos.getZ());
		}
		String held = king.getMainHandItem().isEmpty() ? "nothing" : king.getMainHandItem().getHoverName().getString();
		return KingContext.describe(king.getName().getString(), king.getX(), king.getY(), king.getZ(),
				king.getDirection().getName(), held, looked);
	}
}
