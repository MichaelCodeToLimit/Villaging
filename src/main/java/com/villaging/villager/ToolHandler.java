package com.villaging.villager;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;

/** Runs one tool call on the server main thread. The result is {@code ok...} or {@code failed: reason}. */
@FunctionalInterface
public interface ToolHandler {
	CompletableFuture<String> run(ToolContext ctx, JsonObject args);
}
