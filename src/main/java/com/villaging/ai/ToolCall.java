package com.villaging.ai;

import com.google.gson.JsonObject;

/** One tool invocation requested by the model. */
public record ToolCall(String id, String name, JsonObject args) {
}
