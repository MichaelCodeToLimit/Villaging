package com.villaging.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Talks to a local Ollama server through its /api/chat endpoint. */
public final class OllamaProvider implements AiProvider {
	private final String baseUrl;
	private final String model;
	private final Duration timeout;
	private final AtomicLong nextCallId = new AtomicLong();

	public OllamaProvider(String baseUrl, String model) {
		this(baseUrl, model, Duration.ofSeconds(120));
	}

	public OllamaProvider(String baseUrl, String model, Duration timeout) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.model = model;
		this.timeout = timeout;
	}

	@Override
	public AiResponse complete(String system, List<ChatMessage> messages, List<ToolDef> tools) throws AiException {
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.addProperty("stream", false);
		JsonArray encoded = new JsonArray();
		encoded.add(message("system", system));
		for (ChatMessage m : messages) {
			encoded.add(switch (m.role()) {
				case USER -> message("user", m.text());
				case TOOL_RESULT -> message("tool", m.text());
				case ASSISTANT -> assistant(m);
			});
		}
		body.add("messages", encoded);
		JsonArray toolArray = new JsonArray();
		for (ToolDef t : tools) {
			JsonObject function = new JsonObject();
			function.addProperty("name", t.name());
			function.addProperty("description", t.description());
			function.add("parameters", t.parametersSchema());
			JsonObject o = new JsonObject();
			o.addProperty("type", "function");
			o.add("function", function);
			toolArray.add(o);
		}
		if (!toolArray.isEmpty()) {
			body.add("tools", toolArray);
		}
		JsonObject response = HttpJson.post(baseUrl + "/api/chat", Map.of(), body, timeout);
		return parse(response);
	}

	private static JsonObject message(String role, String content) {
		JsonObject o = new JsonObject();
		o.addProperty("role", role);
		o.addProperty("content", content);
		return o;
	}

	private static JsonObject assistant(ChatMessage m) {
		JsonObject o = message("assistant", m.text());
		if (!m.toolCalls().isEmpty()) {
			JsonArray calls = new JsonArray();
			for (ToolCall c : m.toolCalls()) {
				JsonObject function = new JsonObject();
				function.addProperty("name", c.name());
				function.add("arguments", c.args());
				JsonObject call = new JsonObject();
				call.add("function", function);
				calls.add(call);
			}
			o.add("tool_calls", calls);
		}
		return o;
	}

	private AiResponse parse(JsonObject response) {
		JsonElement message = response.get("message");
		if (message == null || !message.isJsonObject()) {
			return new AiResponse("", List.of());
		}
		JsonObject m = message.getAsJsonObject();
		String text = m.has("content") && !m.get("content").isJsonNull() ? m.get("content").getAsString() : "";
		List<ToolCall> calls = new ArrayList<>();
		JsonElement toolCalls = m.get("tool_calls");
		if (toolCalls != null && toolCalls.isJsonArray()) {
			for (JsonElement e : toolCalls.getAsJsonArray()) {
				if (!e.isJsonObject() || !e.getAsJsonObject().has("function")) {
					continue;
				}
				JsonObject function = e.getAsJsonObject().getAsJsonObject("function");
				JsonElement args = function.get("arguments");
				calls.add(new ToolCall("call_" + nextCallId.getAndIncrement(), function.get("name").getAsString(),
						args != null && args.isJsonObject() ? args.getAsJsonObject() : new JsonObject()));
			}
		}
		return new AiResponse(text, calls);
	}
}
