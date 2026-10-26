package com.villaging.ai;

import com.google.gson.JsonObject;

/** A tool the model may call. {@code parametersSchema} is a JSON Schema object. */
public record ToolDef(String name, String description, JsonObject parametersSchema) {
}
