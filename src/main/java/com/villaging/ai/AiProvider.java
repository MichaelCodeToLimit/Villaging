package com.villaging.ai;

import java.util.List;

public interface AiProvider {
	AiResponse complete(String system, List<ChatMessage> messages, List<ToolDef> tools) throws AiException;
}
