package com.villaging.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Talks to Google's Gemini API (generateContent with function calling). */
public final class GeminiProvider implements AiProvider {
	private static final int MAX_REMEMBERED_SIGNATURES = 1000;

	private final String baseUrl;
	private final String apiKey;
	private final String model;
	private final Duration timeout;
	private final AtomicLong nextCallId = new AtomicLong();

	/**
	 * Gemini 3 models attach a "thoughtSignature" to function calls and reject the next turn unless it is sent
	 * back. ToolCall has no field for it, so it is remembered here by call id (bounded, oldest dropped first).
	 */
	private final Map<String, String> signatures = new LinkedHashMap<>() {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
			return size() > MAX_REMEMBERED_SIGNATURES;
		}
	};

	public GeminiProvider(String baseUrl, String apiKey, String model) {
		this(baseUrl, apiKey, model, Duration.ofSeconds(25));
	}

	public GeminiProvider(String baseUrl, String apiKey, String model, Duration timeout) {
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.apiKey = apiKey;
		this.model = model;
		this.timeout = timeout;
	}

	@Override
	public AiResponse complete(String system, List<ChatMessage> messages, List<ToolDef> tools) throws AiException {
		JsonObject body = new JsonObject();
		body.add("systemInstruction", content(null, textPart(system)));
		body.add("contents", encodeMessages(messages));
		JsonArray declarations = new JsonArray();
		for (ToolDef t : tools) {
			JsonObject d = new JsonObject();
			d.addProperty("name", t.name());
			d.addProperty("description", t.description());
			JsonObject properties = t.parametersSchema().getAsJsonObject("properties");
			if (properties != null && !properties.isEmpty()) {
				d.add("parameters", t.parametersSchema());
			}
			declarations.add(d);
		}
		if (!declarations.isEmpty()) {
			JsonObject toolObject = new JsonObject();
			toolObject.add("functionDeclarations", declarations);
			JsonArray toolArray = new JsonArray();
			toolArray.add(toolObject);
			body.add("tools", toolArray);
		}
		JsonObject response = HttpJson.post(baseUrl + "/v1beta/models/" + model + ":generateContent",
				Map.of("x-goog-api-key", apiKey), body, timeout);
		try {
			return parse(response);
		} catch (RuntimeException e) {
			throw new AiException("AI service response had an unexpected shape.", e);
		}
	}

	private JsonArray encodeMessages(List<ChatMessage> messages) {
		Map<String, String> callNames = new HashMap<>();
		for (ChatMessage m : messages) {
			for (ToolCall c : m.toolCalls()) {
				callNames.put(c.id(), c.name());
			}
		}
		JsonArray out = new JsonArray();
		JsonArray pendingResults = null;
		for (ChatMessage m : messages) {
			if (m.role() == ChatMessage.Role.TOOL_RESULT) {
				if (pendingResults == null) {
					pendingResults = new JsonArray();
				}
				JsonObject response = new JsonObject();
				response.addProperty("result", m.text());
				JsonObject fr = new JsonObject();
				fr.addProperty("name", callNames.getOrDefault(m.toolCallId(), "unknown"));
				fr.addProperty("id", m.toolCallId());
				fr.add("response", response);
				JsonObject part = new JsonObject();
				part.add("functionResponse", fr);
				pendingResults.add(part);
				continue;
			}
			if (pendingResults != null) {
				out.add(content("user", pendingResults));
				pendingResults = null;
			}
			if (m.role() == ChatMessage.Role.USER) {
				out.add(content("user", textPart(m.text())));
			} else {
				JsonArray parts = new JsonArray();
				if (!m.text().isBlank()) {
					parts.add(textPart(m.text()));
				}
				for (ToolCall c : m.toolCalls()) {
					JsonObject fc = new JsonObject();
					fc.addProperty("name", c.name());
					fc.add("args", c.args());
					fc.addProperty("id", c.id());
					JsonObject part = new JsonObject();
					part.add("functionCall", fc);
					String signature;
					synchronized (signatures) {
						signature = signatures.get(c.id());
					}
					if (signature != null) {
						part.addProperty("thoughtSignature", signature);
					}
					parts.add(part);
				}
				out.add(content("model", parts));
			}
		}
		if (pendingResults != null) {
			out.add(content("user", pendingResults));
		}
		return out;
	}

	private static JsonObject textPart(String text) {
		JsonObject part = new JsonObject();
		part.addProperty("text", text);
		return part;
	}

	private static JsonObject content(String role, JsonObject singlePart) {
		JsonArray parts = new JsonArray();
		parts.add(singlePart);
		return content(role, parts);
	}

	private static JsonObject content(String role, JsonArray parts) {
		JsonObject o = new JsonObject();
		if (role != null) {
			o.addProperty("role", role);
		}
		o.add("parts", parts);
		return o;
	}

	private AiResponse parse(JsonObject response) {
		JsonElement candidates = response.get("candidates");
		if (candidates == null || !candidates.isJsonArray() || candidates.getAsJsonArray().isEmpty()) {
			return new AiResponse("", List.of());
		}
		JsonElement content = candidates.getAsJsonArray().get(0).getAsJsonObject().get("content");
		if (content == null || !content.isJsonObject() || !content.getAsJsonObject().has("parts")) {
			return new AiResponse("", List.of());
		}
		StringBuilder text = new StringBuilder();
		List<ToolCall> calls = new ArrayList<>();
		for (JsonElement e : content.getAsJsonObject().getAsJsonArray("parts")) {
			JsonObject part = e.getAsJsonObject();
			if (part.has("thought") && part.get("thought").getAsBoolean()) {
				continue;
			}
			if (part.has("functionCall")) {
				JsonObject fc = part.getAsJsonObject("functionCall");
				String name = fc.get("name").getAsString();
				String id = fc.has("id") ? fc.get("id").getAsString() : "gemini_call_" + nextCallId.getAndIncrement();
				JsonElement args = fc.get("args");
				calls.add(new ToolCall(id, name, args != null && args.isJsonObject() ? args.getAsJsonObject() : new JsonObject()));
				if (part.has("thoughtSignature")) {
					synchronized (signatures) {
						signatures.put(id, part.get("thoughtSignature").getAsString());
					}
				}
			} else if (part.has("text")) {
				if (!text.isEmpty()) {
					text.append('\n');
				}
				text.append(part.get("text").getAsString());
			}
		}
		return new AiResponse(text.toString().strip(), calls);
	}
}
