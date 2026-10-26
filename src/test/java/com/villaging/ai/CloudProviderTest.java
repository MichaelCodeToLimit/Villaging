package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class CloudProviderTest {
	private static final String TEXT_ONLY = "{\"content\":[{\"type\":\"text\",\"text\":\"Yes, my king.\"}]}";

	private static List<ToolDef> tools() {
		return Tools.all();
	}

	@Test
	void sendsKeyVersionAndToolsHeadersAndBody() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, TEXT_ONLY)) {
			new CloudProvider(server.url(), "secret-key", "test-model")
					.complete("be a villager", List.of(ChatMessage.user("hello")), tools());
			assertEquals("/v1/messages", server.lastPath.get());
			assertEquals("secret-key", server.lastHeaders.get().getFirst("x-api-key"));
			assertEquals("2023-06-01", server.lastHeaders.get().getFirst("anthropic-version"));
			JsonObject body = JsonParser.parseString(server.lastBody.get()).getAsJsonObject();
			assertEquals("test-model", body.get("model").getAsString());
			assertEquals("be a villager", body.get("system").getAsString());
			assertEquals(1024, body.get("max_tokens").getAsInt());
			assertEquals(5, body.getAsJsonArray("tools").size());
			JsonObject firstTool = body.getAsJsonArray("tools").get(0).getAsJsonObject();
			assertTrue(firstTool.has("input_schema"));
			JsonObject msg = body.getAsJsonArray("messages").get(0).getAsJsonObject();
			assertEquals("user", msg.get("role").getAsString());
			assertEquals("hello", msg.get("content").getAsString());
		}
	}

	@Test
	void parsesTextOnly() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, TEXT_ONLY)) {
			AiResponse r = new CloudProvider(server.url(), "k", "m").complete("s", List.of(ChatMessage.user("hi")), tools());
			assertEquals("Yes, my king.", r.text());
			assertTrue(r.toolCalls().isEmpty());
		}
	}

	@Test
	void parsesTextAndToolUse() throws Exception {
		String body = "{\"content\":[{\"type\":\"text\",\"text\":\"On my way.\"},"
				+ "{\"type\":\"tool_use\",\"id\":\"toolu_1\",\"name\":\"go_to\",\"input\":{\"x\":1,\"y\":64,\"z\":2}}]}";
		try (FakeHttpServer server = new FakeHttpServer(200, body)) {
			AiResponse r = new CloudProvider(server.url(), "k", "m").complete("s", List.of(ChatMessage.user("go")), tools());
			assertEquals("On my way.", r.text());
			assertEquals(1, r.toolCalls().size());
			ToolCall call = r.toolCalls().get(0);
			assertEquals("toolu_1", call.id());
			assertEquals("go_to", call.name());
			assertEquals(2, call.args().get("z").getAsInt());
		}
	}

	@Test
	void encodesToolResultMessagesAndMergesConsecutiveOnes() throws Exception {
		JsonObject args = new JsonObject();
		List<ChatMessage> history = List.of(
				ChatMessage.user("do two things"),
				ChatMessage.assistant("ok", List.of(new ToolCall("a", "stop", args), new ToolCall("b", "follow_me", args))),
				ChatMessage.toolResult("a", "ok"),
				ChatMessage.toolResult("b", "failed: nope"));
		try (FakeHttpServer server = new FakeHttpServer(200, TEXT_ONLY)) {
			new CloudProvider(server.url(), "k", "m").complete("s", history, tools());
			JsonArray messages = JsonParser.parseString(server.lastBody.get()).getAsJsonObject().getAsJsonArray("messages");
			assertEquals(3, messages.size());
			JsonArray assistant = messages.get(1).getAsJsonObject().getAsJsonArray("content");
			assertEquals("text", assistant.get(0).getAsJsonObject().get("type").getAsString());
			assertEquals("tool_use", assistant.get(1).getAsJsonObject().get("type").getAsString());
			assertEquals("b", assistant.get(2).getAsJsonObject().get("id").getAsString());
			JsonObject results = messages.get(2).getAsJsonObject();
			assertEquals("user", results.get("role").getAsString());
			JsonArray blocks = results.getAsJsonArray("content");
			assertEquals(2, blocks.size());
			assertEquals("tool_result", blocks.get(0).getAsJsonObject().get("type").getAsString());
			assertEquals("a", blocks.get(0).getAsJsonObject().get("tool_use_id").getAsString());
			assertEquals("failed: nope", blocks.get(1).getAsJsonObject().get("content").getAsString());
		}
	}

	@Test
	void non200ThrowsAiExceptionWithStatus() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(401, "{\"error\":\"bad key\"}")) {
			AiException e = assertThrows(AiException.class,
					() -> new CloudProvider(server.url(), "k", "m").complete("s", List.of(ChatMessage.user("hi")), tools()));
			assertTrue(e.getMessage().contains("401"));
		}
	}

	@Test
	void emptyContentGivesEmptyResponse() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, "{\"content\":[]}")) {
			AiResponse r = new CloudProvider(server.url(), "k", "m").complete("s", List.of(ChatMessage.user("hi")), tools());
			assertEquals("", r.text());
			assertTrue(r.toolCalls().isEmpty());
		}
	}

	@Test
	void nonJsonBodyThrowsAiException() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, "<html>oops</html>")) {
			assertThrows(AiException.class,
					() -> new CloudProvider(server.url(), "k", "m").complete("s", List.of(ChatMessage.user("hi")), tools()));
		}
	}

	@Test
	void badUrlThrowsAiExceptionWithoutLeakingTheKey() {
		AiException e = assertThrows(AiException.class,
				() -> new CloudProvider("not a url", "SECRET-KEY-123", "m").complete("s", List.of(ChatMessage.user("hi")), tools()));
		assertTrue(!String.valueOf(e.getMessage()).contains("SECRET-KEY-123"));
	}

	@Test
	void keyWithIllegalHeaderCharactersThrowsAiExceptionWithoutLeakingTheKey() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, TEXT_ONLY)) {
			AiException e = assertThrows(AiException.class,
					() -> new CloudProvider(server.url(), "bad\nkey-SECRET", "m").complete("s", List.of(ChatMessage.user("hi")), tools()));
			assertTrue(!String.valueOf(e.getMessage()).contains("key-SECRET"));
		}
	}

	@Test
	void toolUseWithoutNameThrowsAiException() throws Exception {
		String body = "{\"content\":[{\"type\":\"tool_use\",\"id\":\"x\"}]}";
		try (FakeHttpServer server = new FakeHttpServer(200, body)) {
			assertThrows(AiException.class,
					() -> new CloudProvider(server.url(), "k", "m").complete("s", List.of(ChatMessage.user("hi")), tools()));
		}
	}

	@Test
	void timeoutThrowsAiException() throws Exception {
		try (FakeHttpServer server = new FakeHttpServer(200, TEXT_ONLY, 1500)) {
			assertThrows(AiException.class, () -> new CloudProvider(server.url(), "k", "m", Duration.ofMillis(200))
					.complete("s", List.of(ChatMessage.user("hi")), tools()));
		}
	}
}
