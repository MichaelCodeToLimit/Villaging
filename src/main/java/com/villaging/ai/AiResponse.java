package com.villaging.ai;

import java.util.List;

public record AiResponse(String text, List<ToolCall> toolCalls) {
	public AiResponse {
		text = text == null ? "" : text;
		toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
	}
}
