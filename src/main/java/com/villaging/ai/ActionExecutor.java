package com.villaging.ai;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Runs tool calls in the game world. Implemented by the Minecraft layer. */
public interface ActionExecutor {
	/** Returns an error message for the model, or empty when the call is acceptable. */
	Optional<String> validate(ToolCall call);

	/** Runs the call; the result string ("ok" or "failed: reason") is fed back to the model. */
	CompletableFuture<String> execute(ToolCall call);

	/** False once the villager is dead, removed or unloaded; the loop then stops. */
	boolean isAlive();
}
