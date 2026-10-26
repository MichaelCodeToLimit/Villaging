package com.villaging.ai;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/** Single-endpoint fake HTTP server for provider tests. */
final class FakeHttpServer implements AutoCloseable {
	final AtomicReference<String> lastBody = new AtomicReference<>();
	final AtomicReference<com.sun.net.httpserver.Headers> lastHeaders = new AtomicReference<>();
	final AtomicReference<String> lastPath = new AtomicReference<>();
	private final HttpServer server;

	FakeHttpServer(int status, String responseBody, long delayMillis) throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
			lastHeaders.set(exchange.getRequestHeaders());
			lastPath.set(exchange.getRequestURI().getPath());
			if (delayMillis > 0) {
				try {
					Thread.sleep(delayMillis);
				} catch (InterruptedException ignored) {
					Thread.currentThread().interrupt();
				}
			}
			byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("content-type", "application/json");
			try {
				exchange.sendResponseHeaders(status, bytes.length);
				exchange.getResponseBody().write(bytes);
			} catch (IOException ignored) {
				// client gave up (timeout test)
			} finally {
				exchange.close();
			}
		});
		server.start();
	}

	FakeHttpServer(int status, String responseBody) throws IOException {
		this(status, responseBody, 0);
	}

	String url() {
		return "http://127.0.0.1:" + server.getAddress().getPort();
	}

	@Override
	public void close() {
		server.stop(0);
	}
}
