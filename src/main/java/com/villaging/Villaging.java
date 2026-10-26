package com.villaging;

import com.villaging.king.Kings;
import com.villaging.villager.VillagerBrains;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Villaging implements ModInitializer {
	public static final String MOD_ID = "villaging";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.init();
		VillagerBrains.init();
		Kings.init();
		LOGGER.info("Villaging loaded");
	}
}
