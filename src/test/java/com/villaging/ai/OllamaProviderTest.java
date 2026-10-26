package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

class OllamaProviderTest {
	private static final String WITH_TOOLS = "{\"message\":{\"role\":\"assistant\",\"content\":\"Coming.\","
			+ "\"tool_calls\":[{\"function\":{\"name\":\"go_to\",\"arguments\":{\"x\":1,\"y\":64,\"z\":2}}},"
			+ "{\"function\":{\"name\":\"follow_me\",\"arguments\":{}}}]}}";

	@Test
	void sendsModelStreamFalseSystemAndTools() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, "{\"message\":{\"content\":\"hi\"}}")) {
			new OllamaProvider(server.url(), "llama3.1:8b").complete("sys", List.of(ChatMessage.user("yo")), Tools.all());
			assertEquals("/api/chat", server.lastPath.get());
			JsonObject body = JsonParser.parseString(server.lastBody.get()).getAsJsonObject();
			assertEquals("llama3.1:8b", body.get("model").getAsString());
			assertTrue(!body.get("stream").getAsBoolean());
			JsonArray messages = body.getAsJsonArray("messages");
			assertEquals("system", messages.get(0).getAsJsonObject().get("role").getAsString());
			assertEquals("sys", messages.get(0).getAsJsonObject().get("content").getAsString());
			assertEquals("yo", messages.get(1).getAsJsonObject().get("content").getAsString());
			JsonObject tool = body.getAsJsonArray("tools").get(0).getAsJsonObject();
			assertEquals("function", tool.get("type").getAsString());
			assertTrue(tool.getAsJsonObject("function").has("parameters"));
		}
	}

	@Test
	void parsesTextAndToolCalls() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, WITH_TOOLS)) {
			AiResponse r = new OllamaProvider(server.url(), "m").complete("s", List.of(ChatMessage.user("go")), Tools.all());
			assertEquals("Coming.", r.text());
			assertEquals(2, r.toolCalls().size());
			assertEquals("go_to", r.toolCalls().get(0).name());
			assertEquals(2, r.toolCalls().get(0).args().get("z").getAsInt());
			assertEquals("follow_me", r.toolCalls().get(1).name());
		}
	}

	@Test
	void generatesUniqueToolCallIdsAcrossResponses() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, WITH_TOOLS)) {
			OllamaProvider provider = new OllamaProvider(server.url(), "m");
			AiResponse a = provider.complete("s", List.of(ChatMessage.user("1")), Tools.all());
			AiResponse b = provider.complete("s", List.of(ChatMessage.user("2")), Tools.all());
			assertNotEquals(a.toolCalls().get(0).id(), a.toolCalls().get(1).id());
			assertNotEquals(a.toolCalls().get(0).id(), b.toolCalls().get(0).id());
		}
	}

	@Test
	void encodesToolResultAsToolRoleAndAssistantToolCalls() throws Exception {
		JsonObject args = new JsonObject();
		args.addProperty("x", 1);
		List<ChatMessage> history = List.of(
				ChatMessage.user("go"),
				ChatMessage.assistant("ok", List.of(new ToolCall("c1", "go_to", args))),
				ChatMessage.toolResult("c1", "ok"));
		try (FakeHttpServer server = new FakeHttpServer(200, "{\"message\":{\"content\":\"done\"}}")) {
			new OllamaProvider(server.url(), "m").complete("s", history, Tools.all());
			JsonArray messages = JsonParser.parseString(server.lastBody.get()).getAsJsonObject().getAsJsonArray("messages");
			JsonObject assistant = messages.get(2).getAsJsonObject();
			assertEquals("assistant", assistant.get("role").getAsString());
			assertEquals("go_to", assistant.getAsJsonArray("tool_calls").get(0).getAsJsonObject()
					.getAsJsonObject("function").get("name").getAsString());
			JsonObject result = messages.get(3).getAsJsonObject();
			assertEquals("tool", result.get("role").getAsString());
			assertEquals("ok", result.get("content").getAsString());
		}
	}

	@Test
	void stripsSpecialTokenArtifactsFromContent() throws Exception {
		String body = "{\"message\":{\"content\":\"<|channel>Je suis là, Sire.<channel|>\"}}";
		try (FakeHttpServer server = new FakeHttpServer(200, body)) {
			AiResponse r = new OllamaProvider(server.url(), "m").complete("s", List.of(ChatMessage.user("hi")), Tools.all());
			assertEquals("Je suis là, Sire.", r.text());
		}
	}

	@Test
	void keepsLegitimateAngleBracketText() throws Exception {
		String body = "{\"message\":{\"content\":\"I <3 villages and <b>bold</b> ideas\"}}";
		try (FakeHttpServer server = new FakeHttpServer(200, body)) {
			AiResponse r = new OllamaProvider(server.url(), "m").complete("s", List.of(ChatMessage.user("hi")), Tools.all());
			assertEquals("I <3 villages and <b>bold</b> ideas", r.text());
		}
	}

	@Test
	void malformedToolCallsThrowAiExceptionInsteadOfRuntimeExceptions() throws Exception {
		for (String body : List.of(
				"{\"message\":{\"tool_calls\":[{\"function\":{}}]}}",
				"{\"message\":{\"tool_calls\":[{\"function\":\"oops\"}]}}",
				"{\"message\":{\"content\":{\"not\":\"a string\"}}}")) {
			try (FakeHttpServer server = new FakeHttpServer(200, body)) {
				assertThrows(AiException.class,
						() -> new OllamaProvider(server.url(), "m").complete("s", List.of(ChatMessage.user("hi")), Tools.all()),
						body);
			}
		}
	}

	@Test
	void urlWithoutSchemeThrowsAiException() {
		assertThrows(AiException.class,
				() -> new OllamaProvider("localhost:11434", "m").complete("s", List.of(ChatMessage.user("hi")), Tools.all()));
	}

	@Test
	void missingMessageGivesEmptyResponse() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, "{}")) {
			AiResponse r = new OllamaProvider(server.url(), "m").complete("s", List.of(ChatMessage.user("hi")), Tools.all());
			assertEquals("", r.text());
			assertTrue(r.toolCalls().isEmpty());
		}
	}

	@Test
	void connectionRefusedThrowsAiException() {
		assertThrows(AiException.class,
				() -> new OllamaProvider("http://127.0.0.1:1", "m").complete("s", List.of(ChatMessage.user("hi")), Tools.all()));
	}
}
