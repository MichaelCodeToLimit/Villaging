package com.villaging.ai.script;

/** What a script condition may look at. Facts only; nothing here changes the world. */
public interface Facts {
	/** How many of the item (an id such as minecraft:oak_log) the villager carries. */
	int count(String itemId);

	boolean hasBlock(int x, int y, int z);

	double health();

	boolean isNight();
}
