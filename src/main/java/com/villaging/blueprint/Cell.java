package com.villaging.blueprint;

/** One block of a blueprint: its position relative to the blueprint's minimum corner and its palette key. */
public record Cell(int x, int y, int z, char key) {
}
