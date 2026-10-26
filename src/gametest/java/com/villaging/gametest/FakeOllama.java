package com.villaging.gametest;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

/** A scripted stand-in for an Ollama server so the whole command path can be tested without a real model. */
final class FakeOllama implements AutoCloseable {
	final List<String> requests = Collections.synchronizedList(new ArrayList<>());
	private final Queue<String> responses = new ArrayDeque<>();
	private final HttpServer server;

	FakeOllama() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/api/chat", exchange -> {
			requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			String next;
			synchronized (responses) {
				next = responses.poll();
			}
			byte[] bytes = (next == null ? "{\"message\":{\"content\":\"\"}}" : next).getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("content-type", "application/json");
			exchange.sendResponseHeaders(200, bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
	}

	void script(String... jsonResponses) {
		synchronized (responses) {
			responses.clear();
			responses.addAll(List.of(jsonResponses));
		}
		requests.clear();
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
