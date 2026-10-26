package com.villaging.command;

import com.villaging.Villaging;
import com.villaging.ai.AgentLoop;
import com.villaging.ai.AiProvider;
import com.villaging.ai.ChatMessage;
import com.villaging.ai.ProviderFactory;
import com.villaging.ai.SystemPrompt;
import com.villaging.command.CommandService.CommandResult;
import com.villaging.command.CommandService.VillagerStatus;
import com.villaging.config.VillagingConfig;
import com.villaging.king.KingContextReader;
import com.villaging.king.Kings;
import com.villaging.villager.MinecraftActionExecutor;
import com.villaging.villager.VillagerBrain;
import com.villaging.villager.VillagerBrains;
import com.villaging.villager.VillagerSpeech;
import com.villaging.villager.VillagerState;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/** Per-server glue: owns the AI provider, the background threads and the {@link CommandService}. */
public final class VillagingRuntime implements CommandService.Backend {
	private static final String KING_PREFIX = "king: ";
	private static final String SELF_PREFIX = "me: ";

	private final MinecraftServer server;
	private final VillagingConfig config;
	private final AiProvider provider;
	private final String configProblem;
	private final ExecutorService aiThreads;
	private final CommandService service;

	public VillagingRuntime(MinecraftServer server, VillagingConfig config) {
		this.server = server;
		this.config = config;
		AiProvider created = null;
		String problem = config.problem();
		if (problem.isEmpty()) {
			try {
				created = ProviderFactory.create(config);
			} catch (RuntimeException e) {
				problem = String.valueOf(e.getMessage());
			}
		}
		this.provider = created;
		this.configProblem = problem;
		AtomicInteger counter = new AtomicInteger();
		this.aiThreads = Executors.newCachedThreadPool(r -> {
			Thread t = new Thread(r, "villaging-ai-" + counter.incrementAndGet());
			t.setDaemon(true);
			return t;
		});
		this.service = new CommandService(this, config.callsPerMinute(), System::nanoTime);
		if (!configProblem.isEmpty()) {
			Villaging.LOGGER.warn("Smart villagers are disabled: {}", configProblem);
		}
	}

	public CommandResult handle(ServerPlayer player, int villagerId, String message) {
		return service.handle(player.getUUID(), villagerId, message);
	}

	public void shutdown() {
		service.cancelAll();
		aiThreads.shutdownNow();
	}

	public static String rejectionMessage(CommandResult result, String configProblem) {
		return switch (result) {
			case ACCEPTED -> "";
			case REJECTED_NOT_KING -> "Only the king may command villagers.";
			case REJECTED_NOT_SMART -> "That villager is not smart. Use the crown on it first.";
			case REJECTED_TOO_FAR -> "You are too far from that villager.";
			case REJECTED_BAD_MESSAGE -> "Say something between 1 and " + CommandService.MAX_MESSAGE_LENGTH + " characters.";
			case REJECTED_RATE_LIMITED -> "Please wait a moment; this villager is thinking too often.";
			case DISABLED_BAD_CONFIG -> "Smart villagers are disabled: " + configProblem;
		};
	}

	public String problem() {
		return configProblem;
	}

	// --- CommandService.Backend ---

	@Override
	public boolean isKing(UUID player) {
		return Kings.isKing(server, player);
	}

	@Override
	public VillagerStatus villagerStatus(UUID player, int villagerId, int maxDistance) {
		ServerPlayer sp = server.getPlayerList().getPlayer(player);
		if (sp == null) {
			return VillagerStatus.NOT_SMART;
		}
		Entity entity = sp.level().getEntity(villagerId);
		if (!(entity instanceof Villager villager) || !VillagerBrains.get(villager).smart()) {
			return VillagerStatus.NOT_SMART;
		}
		return villager.distanceTo(sp) > maxDistance ? VillagerStatus.TOO_FAR : VillagerStatus.OK;
	}

	@Override
	public String configProblem() {
		return configProblem;
	}

	@Override
	public AgentLoop.Handle startLoop(UUID player, int villagerId, String message) {
		ServerPlayer king = server.getPlayerList().getPlayer(player);
		Villager villager = (Villager) king.level().getEntity(villagerId);
		VillagerBrain brain = VillagerBrains.get(villager);

		String system = SystemPrompt.build(brain, VillagerState.describe(villager), KingContextReader.read(king));
		List<ChatMessage> history = historyFrom(brain);
		MinecraftActionExecutor executor = new MinecraftActionExecutor(server, villager, player, config.actionRange());

		return new AgentLoop(provider, config.maxSteps()).start(system, history, message, executor,
				reply -> server.execute(() -> VillagerSpeech.say(server, villager, player, reply)),
				produced -> server.execute(() -> remember(villager, message, produced)),
				aiThreads);
	}

	private static List<ChatMessage> historyFrom(VillagerBrain brain) {
		List<ChatMessage> history = new ArrayList<>();
		for (String line : brain.memory()) {
			if (line.startsWith(KING_PREFIX)) {
				history.add(ChatMessage.user(line.substring(KING_PREFIX.length())));
			} else if (line.startsWith(SELF_PREFIX) && !history.isEmpty()) {
				history.add(ChatMessage.assistant(line.substring(SELF_PREFIX.length()), List.of()));
			}
		}
		return history;
	}

	private static void remember(Villager villager, String kingMessage, List<ChatMessage> produced) {
		if (villager.isRemoved()) {
			return;
		}
		VillagerBrain brain = VillagerBrains.get(villager).withMemory(KING_PREFIX + kingMessage);
		for (ChatMessage m : produced) {
			if (m.role() == ChatMessage.Role.ASSISTANT && !m.text().isBlank()) {
				brain = brain.withMemory(SELF_PREFIX + m.text());
			}
		}
		VillagerBrains.set(villager, brain);
	}
}
