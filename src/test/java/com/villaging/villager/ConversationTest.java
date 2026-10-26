package com.villaging.villager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.villaging.ai.ChatMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConversationTest {
	private static final VillagerBrain SMART = VillagerBrain.DEFAULT.makeSmart("Alma");

	@Test
	void historyAlternatesKingAndVillagerLines() {
		VillagerBrain brain = SMART.withMemory("king: hello").withMemory("me: hi").withMemory("king: follow me");
		List<ChatMessage> history = Conversation.historyFrom(brain);
		assertEquals(3, history.size());
		assertEquals(ChatMessage.Role.USER, history.get(0).role());
		assertEquals("hello", history.get(0).text());
		assertEquals(ChatMessage.Role.ASSISTANT, history.get(1).role());
		assertEquals("hi", history.get(1).text());
		assertEquals("follow me", history.get(2).text());
	}

	@Test
	void historyNeverStartsWithAVillagerLine() {
		VillagerBrain brain = SMART.withMemory("me: orphaned reply").withMemory("king: hello");
		List<ChatMessage> history = Conversation.historyFrom(brain);
		assertEquals(1, history.size());
		assertEquals(ChatMessage.Role.USER, history.get(0).role());
	}

	@Test
	void kingMessageIsRecordedImmediately() {
		VillagerBrain brain = Conversation.withKingMessage(SMART, "mine that block");
		assertEquals(List.of("king: mine that block"), brain.memory());
	}

	@Test
	void finishedRunStoresOnlyNonBlankAssistantText() {
		List<ChatMessage> produced = List.of(
				ChatMessage.user("hi"),
				ChatMessage.assistant("", List.of()),
				ChatMessage.assistant("Hello, my king.", List.of()));
		VillagerBrain brain = Conversation.withReplies(SMART, produced, false);
		assertEquals(List.of("me: Hello, my king."), brain.memory());
	}

	@Test
	void cancelledRunNeverWritesAReplyAfterTheNewerCommand() {
		VillagerBrain afterNewCommand = Conversation.withKingMessage(Conversation.withKingMessage(SMART, "A"), "B");
		List<ChatMessage> producedByCancelledA = List.of(ChatMessage.user("A"), ChatMessage.assistant("late reply to A", List.of()));
		VillagerBrain result = Conversation.withReplies(afterNewCommand, producedByCancelledA, true);
		assertEquals(afterNewCommand, result);
		assertTrue(result.memory().indexOf("king: B") > result.memory().indexOf("king: A"));
	}
}
