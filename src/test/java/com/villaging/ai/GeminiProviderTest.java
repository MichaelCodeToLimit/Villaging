package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiProviderTest {
	private static final String TEXT_ONLY =
			"{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"Yes, my king.\"}]}}]}";
	private static final String WITH_CALLS = "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":["
			+ "{\"functionCall\":{\"name\":\"follow_me\",\"args\":{},\"id\":\"call_1\"},\"thoughtSignature\":\"SIG-ABC\"},"
			+ "{\"functionCall\":{\"name\":\"say\",\"args\":{\"text\":\"hello\"},\"id\":\"call_2\"}}]}}]}";

	private static GeminiProvider provider(FakeHttpServer server) {
		return new GeminiProvider(server.url(), "secret-key", "test-model");
	}

	@Test
	void sendsKeyHeaderPathSystemAndTools() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, TEXT_ONLY)) {
			provider(server).complete("be a villager", List.of(ChatMessage.user("hello")), Tools.all());
			assertEquals("/v1beta/models/test-model:generateContent", server.lastPath.get());
			assertEquals("secret-key", server.lastHeaders.get().getFirst("x-goog-api-key"));
			JsonObject body = JsonParser.parseString(server.lastBody.get()).getAsJsonObject();
			assertEquals("be a villager", body.getAsJsonObject("systemInstruction").getAsJsonArray("parts")
					.get(0).getAsJsonObject().get("text").getAsString());
			JsonObject first = body.getAsJsonArray("contents").get(0).getAsJsonObject();
			assertEquals("user", first.get("role").getAsString());
			assertEquals("hello", first.getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString());
			JsonArray declarations = body.getAsJsonArray("tools").get(0).getAsJsonObject().getAsJsonArray("functionDeclarations");
			assertEquals(5, declarations.size());
		}
	}

	@Test
	void toolsWithoutParametersOmitTheParametersObject() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, TEXT_ONLY)) {
			provider(server).complete("s", List.of(ChatMessage.user("hi")), Tools.all());
			JsonArray declarations = JsonParser.parseString(server.lastBody.get()).getAsJsonObject()
					.getAsJsonArray("tools").get(0).getAsJsonObject().getAsJsonArray("functionDeclarations");
			for (var e : declarations) {
				JsonObject d = e.getAsJsonObject();
				if (d.get("name").getAsString().equals("follow_me")) {
					assertFalse(d.has("parameters"), "Gemini rejects empty parameter objects");
				}
				if (d.get("name").getAsString().equals("go_to")) {
					assertTrue(d.has("parameters"));
				}
			}
		}
	}

	@Test
	void parsesTextOnly() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, TEXT_ONLY)) {
			AiResponse r = provider(server).complete("s", List.of(ChatMessage.user("hi")), Tools.all());
			assertEquals("Yes, my king.", r.text());
			assertTrue(r.toolCalls().isEmpty());
		}
	}

	@Test
	void parsesFunctionCallsKeepingIds() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, WITH_CALLS)) {
			AiResponse r = provider(server).complete("s", List.of(ChatMessage.user("go")), Tools.all());
			assertEquals(2, r.toolCalls().size());
			assertEquals("call_1", r.toolCalls().get(0).id());
			assertEquals("follow_me", r.toolCalls().get(0).name());
			assertEquals("hello", r.toolCalls().get(1).args().get("text").getAsString());
		}
	}

	@Test
	void ignoresThoughtParts() throws Exception {
		String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"private reasoning\",\"thought\":true},"
				+ "{\"text\":\"Visible answer.\"}]}}]}";
		try (FakeHttpServer server = new FakeHttpServer(200, body)) {
			AiResponse r = provider(server).complete("s", List.of(ChatMessage.user("hi")), Tools.all());
			assertEquals("Visible answer.", r.text());
		}
	}

	@Test
	void echoesThoughtSignatureBackAndEncodesToolResultsWithNamesMerged() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, WITH_CALLS)) {
			GeminiProvider provider = provider(server);
			AiResponse first = provider.complete("s", List.of(ChatMessage.user("go")), Tools.all());
			List<ChatMessage> history = List.of(
					ChatMessage.user("go"),
					ChatMessage.assistant("", first.toolCalls()),
					ChatMessage.toolResult("call_1", "ok"),
					ChatMessage.toolResult("call_2", "failed: nope"));
			provider.complete("s", history, Tools.all());

			JsonArray contents = JsonParser.parseString(server.lastBody.get()).getAsJsonObject().getAsJsonArray("contents");
			assertEquals(3, contents.size());
			JsonObject model = contents.get(1).getAsJsonObject();
			assertEquals("model", model.get("role").getAsString());
			JsonObject firstPart = model.getAsJsonArray("parts").get(0).getAsJsonObject();
			assertEquals("follow_me", firstPart.getAsJsonObject("functionCall").get("name").getAsString());
			assertEquals("SIG-ABC", firstPart.get("thoughtSignature").getAsString());
			JsonObject results = contents.get(2).getAsJsonObject();
			assertEquals("user", results.get("role").getAsString());
			JsonArray parts = results.getAsJsonArray("parts");
			assertEquals(2, parts.size());
			JsonObject r0 = parts.get(0).getAsJsonObject().getAsJsonObject("functionResponse");
			assertEquals("follow_me", r0.get("name").getAsString());
			assertEquals("call_1", r0.get("id").getAsString());
			assertEquals("ok", r0.getAsJsonObject("response").get("result").getAsString());
			JsonObject r1 = parts.get(1).getAsJsonObject().getAsJsonObject("functionResponse");
			assertEquals("say", r1.get("name").getAsString());
			assertEquals("failed: nope", r1.getAsJsonObject("response").get("result").getAsString());
		}
	}

	@Test
	void functionCallWithoutIdGetsAGeneratedUniqueId() throws Exception {
		String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{\"name\":\"stop\",\"args\":{}}}]}}]}";
		try (FakeHttpServer server = new FakeHttpServer(200, body)) {
			GeminiProvider provider = provider(server);
			String a = provider.complete("s", List.of(ChatMessage.user("1")), Tools.all()).toolCalls().get(0).id();
			String b = provider.complete("s", List.of(ChatMessage.user("2")), Tools.all()).toolCalls().get(0).id();
			assertFalse(a.isEmpty());
			assertTrue(!a.equals(b));
		}
	}

	@Test
	void noCandidatesGivesEmptyResponse() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}")) {
			AiResponse r = provider(server).complete("s", List.of(ChatMessage.user("hi")), Tools.all());
			assertEquals("", r.text());
			assertTrue(r.toolCalls().isEmpty());
		}
	}

	@Test
	void non200ThrowsAiExceptionWithStatus() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(403, "{\"error\":{\"message\":\"bad key\"}}")) {
			AiException e = assertThrows(AiException.class,
					() -> provider(server).complete("s", List.of(ChatMessage.user("hi")), Tools.all()));
			assertTrue(e.getMessage().contains("403"));
			assertFalse(e.getMessage().contains("secret-key"));
		}
	}

	@Test
	void malformedFunctionCallThrowsAiException() throws Exception {
		String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":{}}]}}]}";
		try (FakeHttpServer server = new FakeHttpServer(200, body)) {
			assertThrows(AiException.class,
					() -> provider(server).complete("s", List.of(ChatMessage.user("hi")), Tools.all()));
		}
	}

	@Test
	void timeoutThrowsAiException() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, TEXT_ONLY, 1500)) {
			assertThrows(AiException.class, () -> new GeminiProvider(server.url(), "k", "m", Duration.ofMillis(200))
					.complete("s", List.of(ChatMessage.user("hi")), Tools.all()));
		}
	}
}
