package com.villaging.ai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Minimal JSON POST helper shared by the providers. */
final class HttpJson {
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

	private HttpJson() {
	}

	static JsonObject post(String url, Map<String, String> headers, JsonObject body, Duration timeout)
			throws AiException {
		HttpRequest request;
		try {
			HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout)
					.header("content-type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(body.toString()));
			headers.forEach(builder::header);
			request = builder.build();
		} catch (IllegalArgumentException e) {
			// Deliberately no cause or detail: the message could echo the API key.
			throw new AiException("The AI service URL or API key in config/villaging.json is not valid.");
		}
		HttpResponse<String> response;
		try {
			response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			throw new AiException("Could not reach the AI service: " + e, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AiException("Interrupted while waiting for the AI service", e);
		}
		if (response.statusCode() / 100 != 2) {
			throw new AiException("AI service returned HTTP " + response.statusCode() + ": " + abbreviate(response.body()));
		}
		try {
			JsonElement parsed = JsonParser.parseString(response.body());
			if (!parsed.isJsonObject()) {
				throw new AiException("AI service returned unexpected JSON: " + abbreviate(response.body()));
			}
			return parsed.getAsJsonObject();
		} catch (JsonParseException e) {
			throw new AiException("AI service returned invalid JSON: " + abbreviate(response.body()), e);
		}
	}

	private static String abbreviate(String s) {
		return s.length() > 300 ? s.substring(0, 300) + "..." : s;
	}
}
