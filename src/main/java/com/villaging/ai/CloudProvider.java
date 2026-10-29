package com.villaging.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Talks to the Anthropic Messages API. */
public final class CloudProvider implements AiProvider {
	private final String baseUrl;
	private final String apiKey;
	private final String model;
	private final Duration timeout;

	public CloudProvider(String baseUrl, String apiKey, String model) {
		this(baseUrl, apiKey, model, Duration.ofSeconds(25));
	}

	public CloudProvider(String baseUrl, String apiKey, String model, Duration timeout) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.apiKey = apiKey;
		this.model = model;
		this.timeout = timeout;
	}

	@Override
	public AiResponse complete(String system, List<ChatMessage> messages, List<ToolDef> tools) throws AiException {
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.addProperty("max_tokens", 1024);
		body.addProperty("system", system);
		body.add("messages", encodeMessages(messages));
		JsonArray toolArray = new JsonArray();
		for (ToolDef t : tools) {
			JsonObject o = new JsonObject();
			o.addProperty("name", t.name());
			o.addProperty("description", t.description());
			o.add("input_schema", t.parametersSchema());
			toolArray.add(o);
		}
		if (!toolArray.isEmpty()) {
			body.add("tools", toolArray);
		}
		JsonObject response = HttpJson.post(baseUrl + "/v1/messages",
				Map.of("x-api-key", apiKey, "anthropic-version", "2023-06-01"), body, timeout);
		try {
			return parse(response);
		} catch (RuntimeException e) {
			throw new AiException("AI service response had an unexpected shape.", e);
		}
	}

	private static JsonArray encodeMessages(List<ChatMessage> messages) {
		JsonArray out = new JsonArray();
		JsonArray pendingResults = null;
		for (ChatMessage m : messages) {
			if (m.role() == ChatMessage.Role.TOOL_RESULT) {
				if (pendingResults == null) {
					pendingResults = new JsonArray();
				}
				JsonObject block = new JsonObject();
				block.addProperty("type", "tool_result");
				block.addProperty("tool_use_id", m.toolCallId());
				block.addProperty("content", m.text());
				pendingResults.add(block);
				continue;
			}
			if (pendingResults != null) {
				out.add(userBlocks(pendingResults));
				pendingResults = null;
			}
			if (m.role() == ChatMessage.Role.USER) {
				JsonObject o = new JsonObject();
				o.addProperty("role", "user");
				o.addProperty("content", m.text());
				out.add(o);
			} else {
				JsonArray content = new JsonArray();
				if (!m.text().isBlank()) {
					JsonObject text = new JsonObject();
					text.addProperty("type", "text");
					text.addProperty("text", m.text());
					content.add(text);
				}
				for (ToolCall c : m.toolCalls()) {
					JsonObject use = new JsonObject();
					use.addProperty("type", "tool_use");
					use.addProperty("id", c.id());
					use.addProperty("name", c.name());
					use.add("input", c.args());
					content.add(use);
				}
				JsonObject o = new JsonObject();
				o.addProperty("role", "assistant");
				o.add("content", content);
				out.add(o);
			}
		}
		if (pendingResults != null) {
			out.add(userBlocks(pendingResults));
		}
		return out;
	}

	private static JsonObject userBlocks(JsonArray blocks) {
		JsonObject o = new JsonObject();
		o.addProperty("role", "user");
		o.add("content", blocks);
		return o;
	}

	private static AiResponse parse(JsonObject response) throws AiException {
		JsonElement content = response.get("content");
		if (content == null || !content.isJsonArray()) {
			throw new AiException("AI service response had no content array.");
		}
		StringBuilder text = new StringBuilder();
		List<ToolCall> calls = new ArrayList<>();
		for (JsonElement e : content.getAsJsonArray()) {
			if (!e.isJsonObject()) {
				continue;
			}
			JsonObject block = e.getAsJsonObject();
			String type = block.has("type") ? block.get("type").getAsString() : "";
			if (type.equals("text") && block.has("text")) {
				if (!text.isEmpty()) {
					text.append('\n');
				}
				text.append(block.get("text").getAsString());
			} else if (type.equals("tool_use")) {
				JsonElement input = block.get("input");
				calls.add(new ToolCall(block.get("id").getAsString(), block.get("name").getAsString(),
						input != null && input.isJsonObject() ? input.getAsJsonObject() : new JsonObject()));
			}
		}
		return new AiResponse(text.toString(), calls);
	}
}
