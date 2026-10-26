package com.villaging.ai;

import com.google.gson.JsonObject;
import java.util.Optional;

@FunctionalInterface
public interface ArgValidator {
	/** Returns an error message for the model, or empty when the arguments are acceptable. */
	Optional<String> check(JsonObject args, int range, double originX, double originY, double originZ);
}
