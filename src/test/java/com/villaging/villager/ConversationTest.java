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
	void aSpokenLineIsRememberedTheMomentItIsSaid() {
		VillagerBrain brain = Conversation.withVillagerLine(Conversation.withKingMessage(SMART, "hi"), "Hello, my king.");
		assertEquals(List.of("king: hi", "me: Hello, my king."), brain.memory());
	}

	@Test
	void aLineSpokenWhileANewerCommandIsPendingIsStillKept() {
		VillagerBrain brain = Conversation.withKingMessage(Conversation.withKingMessage(SMART, "A"), "B");
		VillagerBrain result = Conversation.withVillagerLine(brain, "late reply to A");
		assertTrue(result.memory().contains("me: late reply to A"));
	}

	@Test
	void blankSpeechIsNotRemembered() {
		assertEquals(SMART, Conversation.withVillagerLine(SMART, "   "));
	}

	@Test
	void whatTheVillagerSaidShowsUpInTheChatScreenHistory() {
		VillagerBrain brain = Conversation.withVillagerLine(Conversation.withKingMessage(SMART, "hi"), "Hello.");
		List<ChatTranscript.Line> lines = ChatTranscript.parse(brain.memory());
		assertEquals(2, lines.size());
		assertEquals(false, lines.get(1).fromKing());
	}
}
