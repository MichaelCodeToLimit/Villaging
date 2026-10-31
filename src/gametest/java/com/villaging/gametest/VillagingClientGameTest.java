package com.villaging.gametest;

import com.villaging.ModItems;
import com.villaging.ai.ToolCall;
import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import com.villaging.client.AskScreen;
import com.villaging.client.VillagerChatScreen;
import com.villaging.config.VillagingConfig;
import com.villaging.king.KingData;
import com.villaging.king.Kings;
import com.villaging.net.AnswerPayload;
import com.villaging.net.CommandPayload;
import com.villaging.villager.ActionManager;
import com.villaging.villager.FollowAction;
import com.villaging.villager.GoToAction;
import com.villaging.villager.HandsManager;
import com.villaging.villager.MinecraftActionExecutor;
import com.villaging.blueprint.Blueprint;
import com.villaging.blueprint.BlueprintStore;
import com.villaging.blueprint.BuildOrder;
import com.villaging.blueprint.Cell;
import com.villaging.blueprint.MaterialPlanner;
import com.villaging.build.BlueprintFiles;
import com.villaging.build.BuildJob;
import com.villaging.build.BuildJobs;
import com.villaging.build.BuildRegistry;
import com.villaging.config.BuildSettings;
import com.villaging.villager.TalkHold;
import com.villaging.villager.ToolHandlers;
import com.villaging.villager.VillagerHands;
import com.villaging.villager.MineAction;
import com.villaging.villager.SpeechBubble;
import com.villaging.villager.VillagerSpeech;
import com.villaging.villager.VillagerAction;
import com.villaging.villager.VillagerBrain;
import com.villaging.villager.VillagerBrains;
import com.villaging.villager.VillagerInventories;
import com.villaging.villager.VillagerInventory;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;

public class VillagingClientGameTest implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();
	private FakeOllama ollama;

	@FunctionalInterface
	private interface AskResponder {
		void respond(ClientGameTestContext context) throws Exception;
	}

	@FunctionalInterface
	private interface Check {
		void run() throws Exception;
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		// Stands in for a claims/protection mod: nobody may break the block at (9, 100, 9).
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, entity) -> !(pos.getX() == 9 && pos.getY() == 100 && pos.getZ() == 9));
		// Opt-in real-model runs: VILLAGING_REAL_MODEL=<ollama model> or VILLAGING_GEMINI_KEY=<key> (never stored in the repo).
		String geminiKey = System.getenv("VILLAGING_GEMINI_KEY");
		String realModel = geminiKey != null ? "gemini" : System.getenv("VILLAGING_REAL_MODEL");
		try {
			ollama = new FakeOllama();
			String config = realModel == null
					? "{\"provider\":\"ollama\",\"ollamaUrl\":\"" + ollama.url() + "\",\"ollamaModel\":\"fake\",\"callsPerMinute\":100}"
					: geminiKey != null
					? "{\"provider\":\"gemini\",\"apiKey\":\"" + geminiKey + "\",\"callsPerMinute\":100}"
					: "{\"provider\":\"ollama\",\"ollamaModel\":\"" + realModel + "\",\"callsPerMinute\":100}";
			Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("villaging.json"), config);
			// A new player has never seen the guide: it must open by itself on joining the world (checked first below).
			Files.deleteIfExists(FabricLoader.getInstance().getConfigDir().resolve("villaging-client.json"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			attempt("the guide opens by itself the first time, pages turn, and it is remembered", () -> guideFirstRun(context, server));
			prepareArena(server);
			if (realModel != null) {
				attempt("real model understands free-form phrasing", () -> realModel(context, server));
				ollama.close();
				if (!failures.isEmpty()) {
					throw new AssertionError(String.join("\n", failures));
				}
				return;
			}
			attempt("crown claims kingship and makes villagers smart", () -> crown(server));
			attempt("go_to reaches the target", () -> goTo(server));
			attempt("go_to to an unreachable spot fails cleanly", () -> goToUnreachable(server));
			attempt("mine_block breaks stone and drops it", () -> mineStone(server));
			attempt("mine_block refuses bedrock", () -> mineRefuses(server, Blocks.BEDROCK, "cannot be broken"));
			attempt("mine_block refuses a chest with items", () -> mineChest(server));
			attempt("mine_block refuses air", () -> mineRefuses(server, Blocks.AIR, "no block"));
			attempt("follow_me keeps the villager near the king", () -> follow(server));
			attempt("stop cancels the running action", () -> stop(server));
			attempt("a villager removed mid-action fails cleanly", () -> removedMidAction(server));
			attempt("a villager keeps its own saved 36-slot inventory", () -> villagerInventory(server));
			attempt("the villager fake player is hidden, holds items and is released", () -> villagerHands(server));
			attempt("the crown works through the real click hook (vanilla trading would eat it otherwise)", () -> crownViaClickHook(server));
			attempt("mine_block refuses in adventure mode", () -> mineProtectedAdventure(server));
			attempt("mine_block honours another mod vetoing the break", () -> mineVetoed(server));
			attempt("allowBreakBedrock lets a villager break bedrock like a creative player", () -> mineBedrockAllowed(server));
			attempt("respectProtection=false skips protection checks", () -> mineVetoIgnored(server));
			attempt("speech bubble text never stays as the villager name", () -> bubbleRestored(server));
			attempt("right-click on a smart villager opens the chat screen", () -> sneakClick(context, server));
			attempt("typing in the chat screen drives the villager end to end", () -> endToEnd(context, server));
			attempt("ask opens the dialog and an option button answers the model", () -> askRound(context, server, "Left", c -> c.runOnClient(mc -> ((AskScreen) mc.gui.screen()).choose("Left")), "ok: the king answered \"Left\""));
			attempt("ask accepts the king's own typed answer", () -> askRound(context, server, "Burn it all", c -> { c.getInput().typeChars("Burn it all"); c.getInput().pressKey(GLFW.GLFW_KEY_ENTER); }, "ok: the king answered \"Burn it all\""));
			attempt("closing the ask dialog reads as no answer", () -> askRound(context, server, null, c -> c.runOnClient(mc -> mc.gui.screen().onClose()), "failed: the king did not answer"));
			attempt("look_around, look_at and check_inventory report the surroundings", () -> lookTools(server));
			attempt("place_block places from the inventory and respects protection", () -> placeBlock(server));
			attempt("use_block flips a lever", () -> useBlock(server));
			attempt("break_area clears a box", () -> breakArea(server));
			attempt("risky acts ask the king first and asking can be switched off", () -> riskGate(context, server));
			attempt("item tools pick up, drop, hand over, take (with consent), equip and eat", () -> itemTools(context, server));
			attempt("craft makes small recipes anywhere and big ones at a table", () -> craftTools(server));
			attempt("smelt cooks raw iron in a furnace", () -> smeltTool(server));
			attempt("brew makes potions in a brewing stand", () -> brewTool(server));
			attempt("chop_tree fells a tree", () -> chopTree(server));
			attempt("till, plant and harvest farm a crop", () -> farming(server));
			attempt("shear, milk, breed and tame work on animals", () -> animals(server));
			attempt("fish catches something near water", () -> fishing(server));
			attempt("attack kills a hostile creature", () -> combatAttack(server));
			attempt("players and pets are never attacked or shot", () -> combatNeverHurtsPlayersOrPets(server));
			attempt("shoot hits a target with a bow", () -> combatShoot(server));
			attempt("guard protects the king and spares pets, flee runs away", () -> combatGuardAndFlee(server));
			attempt("sit stays put and sleep lies down at night and wakes at dawn", () -> sitAndSleep(server));
			attempt("task scripts run, fail cleanly, obey limits and stop", () -> scripts(context, server));
			attempt("a risky step inside a script still asks the king", () -> scriptRiskyStepAsks(context, server));
			attempt("a commander relays orders only with the king's approval", () -> commanderRelay(context, server));
			attempt("container tools look in, fill and empty a chest", () -> containerTools(server));
			attempt("review: a cancelled confirmation cannot be answered later", () -> reviewCancelledConfirmation(context, server));
			attempt("review: an approved risky act can still be cancelled", () -> reviewApprovedActCanBeCancelled(context, server));
			attempt("review: the running destruction total asks", () -> reviewDestructionTotalAsks(context, server));
			attempt("review: item data survives and stacks stay valid", () -> reviewItemDataSurvives(context, server));
			attempt("review: respectProtection=false skips the range limit", () -> reviewRangeSkippedWithoutProtection(server));
			attempt("review: no shooting through the king", () -> reviewLineOfFire(server));
			attempt("end to end: one order becomes a script that builds a chest", () -> getLogsAndMakeAChest(context, server));
			attempt("selecting, copying and pasting text in the chat screen", () -> chatSelectCopyPaste(context, server));
			attempt("/announcements reaches every nearby helper, one at a time, and only for the king", () -> announcements(context, server));
			attempt("/villaging summon brings the named helper to the king and never guesses", () -> summonByName(context, server));
			attempt("blueprint planning: template, plan_build report and the ASCII view", () -> blueprintPlanning(server));
			attempt("blueprint build: a cottage appears block by block and the materials are used up exactly", () -> blueprintBuild(context, server, 0));
			attempt("blueprint build: a rotated build puts the door where the plan says", () -> blueprintBuild(context, server, 1));
			attempt("blueprint build: missing stone pauses with a message and resumes after a top-up", () -> blueprintPausesAndResumes(context, server));
			attempt("blueprint build: occupied and protected cells are kept and reported", () -> blueprintBlockedCells(context, server));
			attempt("blueprint build: stop pauses and a fresh start resumes without redoing blocks", () -> blueprintStopAndResume(context, server));
			attempt("blueprint build: a big build asks the king and a no starts nothing", () -> blueprintAsksTheKing(context, server));
			attempt("blueprint safety: banned blocks and oversized plans are refused with a reason", () -> blueprintRefusals(server));
			attempt("team: the king makes, changes and ends teams, and strangers cannot", () -> teamBasics(server));
			attempt("team: a leader's assign reaches the member as the king's order", () -> teamAssignReachesTheMember(server));
			attempt("team: two helpers split a cottage and build every block", () -> teamBuild(context, server, 3));
			attempt("team: a slice waits for a free build place and then finishes", () -> teamBuild(context, server, 1));
			attempt("team: the leader dying frees the members", () -> teamLeaderDeathEndsTheTeam(server));
			attempt("a command from a non-king is ignored", () -> forgedPacket(context, server));
			attempt("speaking to an ordinary villager makes it smart (and the speaker king)", () -> speakingToAnOrdinaryVillager(context, server));
			attempt("sneak-click opens the chat on an ordinary villager", () -> sneakClickOnAnOrdinaryVillager(context, server));
			attempt("rename and the king's title reach the villager and the prompt", () -> renameAndKingTitle(context, server));
			attempt("recruit asks the king and makes the nearest villagers smart", () -> recruiting(context, server));
			attempt("a click opens the full chat with history and the villager stands still", () -> talkingHoldsTheVillagerStill(context, server));
			attempt("the chat hold never blocks an order", () -> aHoldNeverInterruptsAnOrder(context, server));
			attempt("the crown is in the creative inventory", () -> crownIsInTheCreativeInventory(context));
			attempt("recruit makes iron and copper golems smart", () -> recruitingGolems(context, server));
			attempt("smart golems obey orders and know what they are", () -> smartGolemsObey(context, server));
			attempt("a click talks to a smart golem but leaves ordinary ones alone", () -> golemClicks(context, server));
			attempt("the key box opens with /villaging key and saves the key", () -> keyBox(context, server));
		} finally {
			ollama.close();
		}
		if (!failures.isEmpty()) {
			throw new AssertionError(failures.size() + " in-game check(s) failed:\n - " + String.join("\n - ", failures));
		}
	}

	private void attempt(String name, Check check) {
		// VILLAGING_ONLY=<text> runs just the checks whose name contains it (for chasing one failure).
		String only = System.getenv("VILLAGING_ONLY");
		if (only != null && !name.contains(only)) {
			return;
		}
		try {
			check.run();
			System.out.println("[villaging-gametest] PASS " + name);
		} catch (Throwable t) {
			System.out.println("[villaging-gametest] FAIL " + name + ": " + t);
			failures.add(name + ": " + t);
		}
	}

	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	// --- arena helpers ---

	private static void prepareArena(TestServerContext server) {
		server.runCommand("difficulty peaceful");
		server.runCommand("time set day");
		server.runCommand("fill -16 99 -16 16 99 16 stone");
		server.runCommand("fill -16 100 -16 16 115 16 air");
		server.runCommand("gamemode creative @p");
		server.runCommand("tp @p 0 100 2");
	}

	private static void resetArena(TestServerContext server) {
		server.runCommand("difficulty peaceful");
		server.runCommand("kill @e[type=minecraft:villager]");
		// A killed villager lingers for a second while it dies and would still block placing blocks.
		server.waitFor(srv -> srv.overworld().getEntitiesOfClass(Villager.class, new AABB(-16, 90, -16, 16, 116, 16)).isEmpty(), 100);
		server.runCommand("kill @e[type=minecraft:item]");
		server.runCommand("fill -16 100 -16 16 115 16 air");
		server.runCommand("tp @p 0 100 2");
	}

	private static Villager spawnVillager(TestServerContext server, double x, double y, double z) {
		server.runCommand("summon minecraft:villager " + x + " " + y + " " + z);
		return server.computeOnServer(srv -> {
			List<Villager> found = srv.overworld().getEntitiesOfClass(Villager.class,
					new AABB(x - 2, y - 2, z - 2, x + 2, y + 2, z + 2), v -> v.isAlive() && !v.isRemoved());
			require(!found.isEmpty(), "villager did not spawn");
			return found.get(0);
		});
	}

	private static UUID playerId(TestServerContext server) {
		return server.computeOnServer(srv -> srv.getPlayerList().getPlayers().get(0).getUUID());
	}

	private static void makeKing(TestServerContext server) {
		UUID id = playerId(server);
		server.runOnServer(srv -> srv.overworld().setAttached(Kings.ATTACHMENT, KingData.EMPTY));
		require(server.computeOnServer(srv -> Kings.claim(srv, id)), "could not make the test player king");
	}

	private static CompletableFuture<String> startAction(TestServerContext server, PathfinderMob v, VillagerAction action) {
		return server.computeOnServer(srv -> ActionManager.INSTANCE.run(v, action));
	}

	private static String awaitResult(TestServerContext server, CompletableFuture<String> future, int ticks) throws Exception {
		server.waitFor(srv -> future.isDone(), ticks);
		return future.isCompletedExceptionally() ? "EXCEPTION" : future.get();
	}

	private static double distance(TestServerContext server, PathfinderMob v, double x, double y, double z) {
		return server.computeOnServer(srv -> v.position().distanceTo(new Vec3(x, y, z)));
	}

	// --- checks ---

	private void crown(TestServerContext server) {
		resetArena(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		UUID me = playerId(server);
		// Someone else is king: the test player's crown must not work on villagers.
		server.runOnServer(srv -> {
			srv.overworld().setAttached(Kings.ATTACHMENT, KingData.EMPTY);
			Kings.claim(srv, UUID.randomUUID());
		});
		InteractionResult denied = server.computeOnServer(srv -> {
			ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
			return ModItems.CROWN.interactLivingEntity(new ItemStack(ModItems.CROWN), p, v, InteractionHand.MAIN_HAND);
		});
		require(denied == InteractionResult.FAIL, "non-king crown use should fail, got " + denied);
		require(!server.computeOnServer(srv -> VillagerBrains.get(v).smart()), "villager became smart for a non-king");

		makeKing(server);
		InteractionResult ok = server.computeOnServer(srv -> {
			ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
			return ModItems.CROWN.interactLivingEntity(new ItemStack(ModItems.CROWN), p, v, InteractionHand.MAIN_HAND);
		});
		require(ok == InteractionResult.SUCCESS_SERVER, "king crown use should succeed, got " + ok);
		VillagerBrain brain = server.computeOnServer(srv -> VillagerBrains.get(v));
		require(brain.smart() && !brain.name().isEmpty(), "villager should be smart and named: " + brain);
		require(server.computeOnServer(srv -> Kings.isKing(srv, me)), "player should be king");
	}

	private void goTo(TestServerContext server) throws Exception {
		resetArena(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		String result = awaitResult(server, startAction(server, v, new GoToAction(8, 100, 0.5)), 900);
		require(result.equals("ok: arrived"), "expected ok: arrived, got " + result);
		require(distance(server, v, 8, 100, 0.5) <= 2.5, "villager is not near the target");
		require(!server.computeOnServer(srv -> ActionManager.isControlled(v)), "villager still controlled after arriving");
	}

	private void goToUnreachable(TestServerContext server) throws Exception {
		resetArena(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		String result = awaitResult(server, startAction(server, v, new GoToAction(5, 112, 0)), 1200);
		require(result.startsWith("failed"), "expected a failure for an unreachable spot, got " + result);
	}

	private void mineStone(TestServerContext server) throws Exception {
		resetArena(server);
		BlockPos target = new BlockPos(4, 100, 0);
		server.runOnServer(srv -> srv.overworld().setBlockAndUpdate(target, Blocks.STONE.defaultBlockState()));
		Villager v = spawnVillager(server, 0, 100, 0);
		String result = awaitResult(server, startAction(server, v, new MineAction(target, playerId(server))), 1200);
		require(result.startsWith("ok: mined"), "expected ok: mined, got " + result);
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(target).isAir()), "stone is still there");
		server.waitFor(srv -> !srv.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new AABB(target).inflate(3)).isEmpty(), 100);
	}

	private void mineRefuses(TestServerContext server, net.minecraft.world.level.block.Block block, String expected)
			throws Exception {
		resetArena(server);
		BlockPos target = new BlockPos(4, 100, 0);
		server.runOnServer(srv -> srv.overworld().setBlockAndUpdate(target, block.defaultBlockState()));
		Villager v = spawnVillager(server, 0, 100, 0);
		String result = awaitResult(server, startAction(server, v, new MineAction(target, playerId(server))), 400);
		require(result.startsWith("failed") && result.contains(expected), "expected failure containing '" + expected + "', got " + result);
		if (block != Blocks.AIR) {
			require(server.computeOnServer(srv -> srv.overworld().getBlockState(target).is(block)), "block was changed");
		}
	}

	private void mineChest(TestServerContext server) throws Exception {
		resetArena(server);
		server.runCommand("setblock 4 100 0 minecraft:chest");
		server.runCommand("item replace block 4 100 0 container.0 with minecraft:diamond 3");
		Villager v = spawnVillager(server, 0, 100, 0);
		String result = awaitResult(server, startAction(server, v, new MineAction(new BlockPos(4, 100, 0), playerId(server))), 400);
		require(result.equals("failed: container has items"), "expected container refusal, got " + result);
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(new BlockPos(4, 100, 0)).is(Blocks.CHEST)), "chest was broken");
	}

	private void follow(TestServerContext server) throws Exception {
		resetArena(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		UUID me = playerId(server);
		String started = awaitResult(server, startAction(server, v, new FollowAction(me)), 20);
		require(started.equals("ok"), "follow_me should report ok at once, got " + started);
		server.runCommand("tp @p 12 100 0");
		server.waitFor(srv -> v.distanceTo(srv.getPlayerList().getPlayers().get(0)) <= 5.0, 900);
	}

	private void stop(TestServerContext server) throws Exception {
		resetArena(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		CompletableFuture<String> far = startAction(server, v, new GoToAction(14, 100, 0));
		server.runOnServer(srv -> ActionManager.INSTANCE.stop(v));
		require(awaitResult(server, far, 40).startsWith("failed"), "stopped action should report failure");
		require(!server.computeOnServer(srv -> ActionManager.isControlled(v)), "villager still controlled after stop");
	}

	private void removedMidAction(TestServerContext server) throws Exception {
		resetArena(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		CompletableFuture<String> far = startAction(server, v, new GoToAction(14, 100, 0));
		server.runOnServer(srv -> v.discard());
		server.waitFor(srv -> far.isDone(), 100);
		require(far.isCompletedExceptionally(), "future should complete exceptionally when the villager is gone");
		require(!server.computeOnServer(srv -> ActionManager.isControlled(v)), "removed villager still controlled");
	}

	private void crownViaClickHook(TestServerContext server) {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		InteractionResult result = server.computeOnServer(srv -> {
			ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
			p.setShiftKeyDown(false);
			p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CROWN));
			InteractionResult r = UseEntityCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, v, null);
			p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			return r;
		});
		require(result == InteractionResult.SUCCESS_SERVER, "crown click should be handled before trading, got " + result);
		require(server.computeOnServer(srv -> VillagerBrains.get(v).smart()), "villager did not become smart through the click hook");
	}

	private void mineProtectedAdventure(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		BlockPos target = new BlockPos(4, 100, 0);
		server.runOnServer(srv -> srv.overworld().setBlockAndUpdate(target, Blocks.STONE.defaultBlockState()));
		server.runCommand("gamemode adventure @p");
		try {
			Villager v = spawnVillager(server, 0, 100, 0);
			String result = awaitResult(server, startAction(server, v, new MineAction(target, playerId(server))), 400);
			require(result.startsWith("failed") && result.contains("protected"), "expected a protection refusal, got " + result);
			require(server.computeOnServer(srv -> srv.overworld().getBlockState(target).is(Blocks.STONE)), "stone was mined anyway");
		} finally {
			server.runCommand("gamemode creative @p");
		}
	}

	private void mineVetoed(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		BlockPos target = new BlockPos(9, 100, 9);
		server.runOnServer(srv -> srv.overworld().setBlockAndUpdate(target, Blocks.STONE.defaultBlockState()));
		Villager v = spawnVillager(server, 8, 100, 8);
		String result = awaitResult(server, startAction(server, v, new MineAction(target, playerId(server))), 400);
		require(result.startsWith("failed") && result.contains("protect"), "expected a veto refusal, got " + result);
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(target).is(Blocks.STONE)), "vetoed block was mined");
	}

	private static VillagingConfig configWith(boolean respectProtection, boolean allowBreakBedrock) {
		VillagingConfig d = VillagingConfig.defaults();
		return new VillagingConfig(d.provider(), d.apiKey(), d.model(), d.cloudUrl(), d.ollamaUrl(), d.ollamaModel(),
				d.geminiModel(), d.geminiUrl(), d.maxSteps(), d.callsPerMinute(), d.actionRange(), respectProtection,
				allowBreakBedrock, d.maxScriptSteps(), d.maxScriptSeconds(), d.maxScriptDepth());
	}

	private void mineBedrockAllowed(TestServerContext server) throws Exception {
		resetArena(server);
		BlockPos target = new BlockPos(4, 100, 0);
		server.runOnServer(srv -> srv.overworld().setBlockAndUpdate(target, Blocks.BEDROCK.defaultBlockState()));
		Villager v = spawnVillager(server, 0, 100, 0);
		String result = awaitResult(server, startAction(server, v, new MineAction(target, playerId(server), configWith(true, true))), 1200);
		require(result.startsWith("ok: mined"), "expected bedrock to be mined, got " + result);
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(target).isAir()), "bedrock is still there");
	}

	private void mineVetoIgnored(TestServerContext server) throws Exception {
		resetArena(server);
		BlockPos target = new BlockPos(9, 100, 9);
		server.runOnServer(srv -> srv.overworld().setBlockAndUpdate(target, Blocks.STONE.defaultBlockState()));
		Villager v = spawnVillager(server, 8, 100, 8);
		String result = awaitResult(server, startAction(server, v, new MineAction(target, playerId(server), configWith(false, false))), 1200);
		require(result.startsWith("ok: mined"), "expected the veto to be ignored, got " + result);
	}

	private void villagerInventory(TestServerContext server) {
		resetArena(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> {
			VillagerInventory inv = VillagerInventories.get(v);
			inv.addItem(new ItemStack(Items.OAK_LOG, 64));
			inv.addItem(new ItemStack(Items.OAK_LOG, 10));
			inv.addItem(new ItemStack(Items.STICK, 3));
		});
		require(36 == server.computeOnServer(srv -> VillagerInventories.get(v).getContainerSize()), "inventory is not 36 slots");
		require(74 == server.computeOnServer(srv -> VillagerInventories.get(v).count(Items.OAK_LOG)), "log count wrong");
		require(!server.computeOnServer(srv -> VillagerInventories.get(v).remove(Items.OAK_LOG, 75)), "removed more than held");
		require(74 == server.computeOnServer(srv -> VillagerInventories.get(v).count(Items.OAK_LOG)), "failed remove changed the count");
		require(server.computeOnServer(srv -> VillagerInventories.get(v).remove(Items.OAK_LOG, 70)), "could not remove 70");
		require(4 == server.computeOnServer(srv -> VillagerInventories.get(v).count(Items.OAK_LOG)), "wrong remainder after remove");
		// Saved with the villager: encode and decode through the same codec the attachment uses.
		int after = server.computeOnServer(srv -> {
			var ops = net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, srv.registryAccess());
			var tag = VillagerInventory.CODEC.encodeStart(ops, VillagerInventories.get(v)).getOrThrow();
			return VillagerInventory.CODEC.parse(ops, tag).getOrThrow().count(Items.STICK);
		});
		require(after == 3, "inventory did not survive saving, sticks=" + after);
	}

	private void villagerHands(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.STONE, 5)));
		int players = server.computeOnServer(srv -> srv.getPlayerList().getPlayers().size());
		int fakeId = server.computeOnServer(srv -> {
			VillagerHands hands = HandsManager.get(v);
			hands.syncFrom();
			return hands.player().getId();
		});
		require(server.computeOnServer(srv -> srv.getPlayerList().getPlayers().size()) == players, "fake player joined the player list");
		require(server.computeOnServer(srv -> srv.overworld().getEntity(fakeId)) == null, "fake player is in the world");
		require(server.computeOnServer(srv -> HandsManager.get(v).player().position().distanceTo(v.position())) < 0.01, "hands are not at the villager");
		require(server.computeOnServer(srv -> HandsManager.get(v).hold(Items.STONE)), "could not hold stone");
		require(server.computeOnServer(srv -> HandsManager.get(v).player().getMainHandItem().is(Items.STONE)), "fake player is not holding stone");
		require(server.computeOnServer(srv -> v.getMainHandItem().is(Items.STONE)), "villager does not show the held stone");
		require(!server.computeOnServer(srv -> HandsManager.get(v).hold(Items.DIAMOND)), "held an item it does not have");
		server.runOnServer(srv -> HandsManager.get(v).player().getInventory().getItem(0).shrink(0));
		server.runOnServer(srv -> HandsManager.get(v).syncTo());
		require(5 == server.computeOnServer(srv -> VillagerInventories.get(v).count(Items.STONE)), "syncTo changed the villager inventory");
		require(HandsManager.size() == 1, "expected one hands object, got " + HandsManager.size());
		server.runCommand("kill @e[type=minecraft:villager]");
		server.waitFor(srv -> HandsManager.size() == 0, 200);
		require(HandsManager.size() == 0, "hands were not released when the villager died");
	}

	/** Runs one model-asks-the-king round: dialog opens, {@code respond} answers it, the model sees {@code expected}. */
	private void askRound(ClientGameTestContext context, TestServerContext server, String unused,
			AskResponder respond, String expected) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> VillagerBrains.set(v, VillagerBrain.DEFAULT.makeSmart("Alma")));
		int id = server.computeOnServer(srv -> v.getId());
		ollama.script(
				"{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"ask\",\"arguments\":{\"question\":\"Which way?\",\"options\":[\"Left\",\"Right\"]}}}]}}",
				"{\"message\":{\"role\":\"assistant\",\"content\":\"Understood.\"}}");
		context.runOnClient(mc -> ClientPlayNetworking.send(new CommandPayload(id, "decide for me")));
		context.waitForScreen(AskScreen.class);
		context.takeScreenshot("villaging-ask-dialog");
		// A forged or stale answer must not close or answer the real question.
		context.runOnClient(mc -> ClientPlayNetworking.send(new AnswerPayload(12345L, "Right")));
		context.waitTicks(5);
		require(context.computeOnClient(mc -> mc.gui.screen() instanceof AskScreen), "a stale answer closed the dialog");
		respond.respond(context);
		server.waitFor(srv -> ollama.requests.size() >= 2, 600);
		require(ollama.requests.size() >= 2, "the model never got the answer");
		String second = ollama.requests.get(1);
		require(second.contains(expected.replace("\"", "\\\"")), "expected the tool result '" + expected + "' in: " + second);
	}

	/** Runs one tool through the real validate, risk gate and handler path with no model, and returns its result. */
	private static String tool(TestServerContext server, PathfinderMob v, String name, String argsJson, int ticks) throws Exception {
		return tool(server, v, VillagingConfig.defaults(), name, argsJson, ticks);
	}

	private static String tool(TestServerContext server, PathfinderMob v, VillagingConfig config, String name, String argsJson,
			int ticks) throws Exception {
		UUID king = playerId(server);
		CompletableFuture<String> future = server.computeOnServer(srv -> {
			MinecraftActionExecutor exec = new MinecraftActionExecutor(srv, v, king, config);
			ToolCall call = new ToolCall("t", name, com.google.gson.JsonParser.parseString(argsJson).getAsJsonObject());
			Optional<String> bad = exec.validate(call);
			return bad.isPresent() ? CompletableFuture.completedFuture("invalid: " + bad.get()) : exec.execute(call);
		});
		return awaitResult(server, future, ticks);
	}

	private void lookTools(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		server.runCommand("setblock 3 100 0 minecraft:chest");
		server.runCommand("item replace block 3 100 0 container.0 with minecraft:diamond 3");
		server.runCommand("setblock -3 100 0 minecraft:iron_ore");
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.APPLE, 4)));
		String around = tool(server, v, "look_around", "{}", 20);
		require(around.startsWith("ok:") && around.contains("chest") && around.contains("iron_ore") && around.contains("3, 100, 0"),
				"look_around missed the chest or ore: " + around);
		require(around.contains("villager") || around.contains("Creatures"), "look_around has no creatures section: " + around);
		String at = tool(server, v, "look_at", "{\"x\":3,\"y\":100,\"z\":0}", 20);
		require(at.contains("chest") && at.contains("diamond") && at.contains("3"), "look_at did not show the chest contents: " + at);
		String inv = tool(server, v, "check_inventory", "{}", 20);
		require(inv.contains("apple") && inv.contains("4"), "check_inventory wrong: " + inv);
		require(tool(server, v, "look_around", "{\"radius\":99}", 20).startsWith("invalid:"), "radius 99 was accepted");
	}

	private void placeBlock(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.OAK_PLANKS, 5)));
		BlockPos target = new BlockPos(3, 100, 0);
		String result = tool(server, v, "place_block", "{\"item\":\"minecraft:oak_planks\",\"x\":3,\"y\":100,\"z\":0}", 600);
		require(result.startsWith("ok: placed"), "expected placement, got " + result);
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(target).is(Blocks.OAK_PLANKS)), "no planks at the target");
		require(4 == server.computeOnServer(srv -> VillagerInventories.get(v).count(Items.OAK_PLANKS)), "planks were not used up");
		String taken = tool(server, v, "place_block", "{\"item\":\"minecraft:oak_planks\",\"x\":3,\"y\":100,\"z\":0}", 200);
		require(taken.startsWith("failed") && taken.contains("taken"), "expected an occupied-spot failure, got " + taken);
		String none = tool(server, v, "place_block", "{\"item\":\"minecraft:diamond_block\",\"x\":4,\"y\":100,\"z\":0}", 200);
		require(none.startsWith("failed") && none.contains("don't have"), "expected a missing-item failure, got " + none);
		String floating = tool(server, v, "place_block", "{\"item\":\"minecraft:oak_planks\",\"x\":8,\"y\":105,\"z\":8}", 200);
		require(floating.startsWith("failed") && floating.contains("build against"), "expected no-support failure, got " + floating);
		// Protection: an adventure-mode king may not build, unless protection is switched off.
		server.runCommand("gamemode adventure @p");
		String blocked = tool(server, v, "place_block", "{\"item\":\"minecraft:oak_planks\",\"x\":4,\"y\":100,\"z\":0}", 200);
		require(blocked.startsWith("failed") && blocked.contains("protected"), "expected a protection failure, got " + blocked);
		String allowed = tool(server, v, configWith(false, false), "place_block",
				"{\"item\":\"minecraft:oak_planks\",\"x\":4,\"y\":100,\"z\":0}", 600);
		require(allowed.startsWith("ok: placed"), "respectProtection=false should allow it, got " + allowed);
		server.runCommand("gamemode creative @p");
	}

	private void useBlock(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		server.runCommand("setblock 3 100 0 minecraft:lever[face=floor]");
		Villager v = spawnVillager(server, 0, 100, 0);
		BlockPos lever = new BlockPos(3, 100, 0);
		require(!server.computeOnServer(srv -> srv.overworld().getBlockState(lever).getValue(
				net.minecraft.world.level.block.LeverBlock.POWERED)), "lever started on");
		String result = tool(server, v, "use_block", "{\"x\":3,\"y\":100,\"z\":0}", 400);
		require(result.startsWith("ok: used"), "expected the lever to be used, got " + result);
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(lever).getValue(
				net.minecraft.world.level.block.LeverBlock.POWERED)), "lever did not flip");
	}

	private void breakArea(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		server.runCommand("fill 4 100 0 6 102 2 minecraft:dirt");
		Villager v = spawnVillager(server, 0, 100, 0);
		String result = tool(server, v, "break_area", "{\"x1\":4,\"y1\":100,\"z1\":0,\"x2\":6,\"y2\":102,\"z2\":2}", 6000);
		require(result.equals("ok: broke 27 blocks"), "expected 27 blocks, got " + result);
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(new BlockPos(5, 101, 1)).isAir()), "dirt is still there");
		require(tool(server, v, "break_area", "{\"x1\":0,\"y1\":100,\"z1\":0,\"x2\":16,\"y2\":100,\"z2\":0}", 20).startsWith("invalid:"),
				"a 17-wide box was accepted");
	}

	/** Starts a tool without waiting, for tools that wait on the king's answer in the dialog. */
	private static CompletableFuture<String> toolAsync(TestServerContext server, PathfinderMob v, String name, String argsJson) {
		UUID king = playerId(server);
		return server.computeOnServer(srv -> {
			MinecraftActionExecutor exec = new MinecraftActionExecutor(srv, v, king, VillagingConfig.defaults());
			ToolCall call = new ToolCall("t", name, com.google.gson.JsonParser.parseString(argsJson).getAsJsonObject());
			Optional<String> bad = exec.validate(call);
			return bad.isPresent() ? CompletableFuture.completedFuture("invalid: " + bad.get()) : exec.execute(call);
		});
	}

	private static int kingCount(TestServerContext server, net.minecraft.world.item.Item item) {
		return server.computeOnServer(srv -> {
			int total = 0;
			for (ItemStack s : srv.getPlayerList().getPlayers().get(0).getInventory().getNonEquipmentItems()) {
				if (s.is(item)) {
					total += s.getCount();
				}
			}
			return total;
		});
	}

	private static int villagerCount(TestServerContext server, Villager v, net.minecraft.world.item.Item item) {
		return server.computeOnServer(srv -> VillagerInventories.get(v).count(item));
	}

	private void itemTools(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		server.runCommand("clear @p");
		// pick_up
		server.runCommand("summon minecraft:item 3 100 0 {Item:{id:\"minecraft:cobblestone\",count:4}}");
		Villager v = spawnVillager(server, 0, 100, 0);
		String picked = tool(server, v, "pick_up", "{\"item\":\"minecraft:cobblestone\"}", 600);
		require(picked.startsWith("ok: picked up 4"), "pick_up said " + picked);
		require(villagerCount(server, v, Items.COBBLESTONE) == 4, "cobblestone is not in the inventory");
		require(tool(server, v, "pick_up", "{}", 200).startsWith("failed"), "pick_up with nothing around should fail");
		// drop
		String dropped = tool(server, v, "drop", "{\"item\":\"minecraft:cobblestone\",\"count\":3}", 100);
		require(dropped.startsWith("ok: dropped 3"), "drop said " + dropped);
		require(villagerCount(server, v, Items.COBBLESTONE) == 1, "drop did not take the items from the inventory");
		require(tool(server, v, "drop", "{\"item\":\"minecraft:cobblestone\",\"count\":5}", 100).startsWith("failed"), "dropped more than held");
		// give_to_king
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.IRON_INGOT, 6)));
		String gave = tool(server, v, "give_to_king", "{\"item\":\"minecraft:iron_ingot\",\"count\":4}", 600);
		require(gave.startsWith("ok: gave the king 4"), "give_to_king said " + gave);
		require(kingCount(server, Items.IRON_INGOT) == 4 && villagerCount(server, v, Items.IRON_INGOT) == 2, "iron did not move 4 to the king");
		// take_from_king: asks, and moves exactly the named amount only on a yes.
		server.runCommand("give @p minecraft:gold_ingot 10");
		CompletableFuture<String> refused = toolAsync(server, v, "take_from_king", "{\"item\":\"minecraft:gold_ingot\",\"count\":4}");
		answerDialog(context, "No");
		require(awaitResult(server, refused, 100).contains("the king said no"), "a no was not reported");
		require(kingCount(server, Items.GOLD_INGOT) == 10 && villagerCount(server, v, Items.GOLD_INGOT) == 0, "items moved after a no");
		CompletableFuture<String> allowed = toolAsync(server, v, "take_from_king", "{\"item\":\"minecraft:gold_ingot\",\"count\":4}");
		answerDialog(context, "Yes");
		String took = awaitResult(server, allowed, 100);
		require(took.startsWith("ok: took 4"), "take_from_king said " + took);
		require(kingCount(server, Items.GOLD_INGOT) == 6 && villagerCount(server, v, Items.GOLD_INGOT) == 4, "gold did not move exactly 4");
		CompletableFuture<String> tooMany = toolAsync(server, v, "take_from_king", "{\"item\":\"minecraft:gold_ingot\",\"count\":50}");
		answerDialog(context, "Yes");
		require(awaitResult(server, tooMany, 100).startsWith("failed: the king only has 6"), "over-asking was not refused");
		// equip
		server.runOnServer(srv -> {
			VillagerInventories.get(v).addItem(new ItemStack(Items.IRON_SWORD, 1));
			VillagerInventories.get(v).addItem(new ItemStack(Items.IRON_HELMET, 1));
		});
		require(tool(server, v, "equip", "{\"item\":\"minecraft:iron_sword\"}", 50).startsWith("ok: equipped"), "equip sword failed");
		require(tool(server, v, "equip", "{\"item\":\"minecraft:iron_helmet\"}", 50).startsWith("ok: equipped"), "equip helmet failed");
		require(server.computeOnServer(srv -> v.getMainHandItem().is(Items.IRON_SWORD)), "sword is not in hand");
		require(server.computeOnServer(srv -> v.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(Items.IRON_HELMET)), "helmet is not worn");
		require(villagerCount(server, v, Items.IRON_SWORD) == 0, "equipped sword is still in the inventory too");
		// eat
		server.runOnServer(srv -> {
			VillagerInventories.get(v).addItem(new ItemStack(Items.APPLE, 2));
			v.setHealth(10);
		});
		require(tool(server, v, "eat", "{}", 50).startsWith("ok: ate"), "eat failed");
		require(server.computeOnServer(srv -> v.getHealth()) > 10, "eating did not heal");
		require(villagerCount(server, v, Items.APPLE) == 1, "an apple was not used up");
		require(tool(server, v, "use_item", "{\"item\":\"minecraft:diamond_pickaxe\"}", 50).contains("don't have"), "use_item of an unheld item");
	}

	private void containerTools(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		server.runCommand("setblock 3 100 0 minecraft:chest");
		server.runCommand("item replace block 3 100 0 container.0 with minecraft:diamond 5");
		Villager v = spawnVillager(server, 0, 100, 0);
		String open = tool(server, v, "open_container", "{\"x\":3,\"y\":100,\"z\":0}", 600);
		require(open.startsWith("ok:") && open.contains("diamond") && open.contains("5"), "open_container said " + open);
		String took = tool(server, v, "take_out", "{\"x\":3,\"y\":100,\"z\":0,\"item\":\"minecraft:diamond\",\"count\":3}", 600);
		require(took.startsWith("ok: took 3"), "take_out said " + took);
		require(villagerCount(server, v, Items.DIAMOND) == 3, "diamonds did not reach the villager");
		require(tool(server, v, "take_out", "{\"x\":3,\"y\":100,\"z\":0,\"item\":\"minecraft:diamond\",\"count\":9}", 200)
				.contains("only holds 2"), "taking more than the chest holds was not refused");
		String put = tool(server, v, "put_in", "{\"x\":3,\"y\":100,\"z\":0,\"item\":\"minecraft:diamond\",\"count\":2}", 600);
		require(put.startsWith("ok: put 2"), "put_in said " + put);
		require(villagerCount(server, v, Items.DIAMOND) == 1, "put_in did not use up the diamonds");
		require(tool(server, v, "put_in", "{\"x\":3,\"y\":100,\"z\":0,\"item\":\"minecraft:diamond\",\"count\":5}", 200)
				.contains("only have 1"), "putting in more than carried was not refused");
		require(tool(server, v, "open_container", "{\"x\":4,\"y\":100,\"z\":0}", 200).contains("no container"),
				"opening thin air should fail");
		String again = tool(server, v, "open_container", "{\"x\":3,\"y\":100,\"z\":0}", 200);
		require(again.contains("diamond") && again.contains("4"), "chest should now hold 4 diamonds: " + again);
		server.runCommand("gamemode adventure @p");
		String blocked = tool(server, v, "take_out", "{\"x\":3,\"y\":100,\"z\":0,\"item\":\"minecraft:diamond\",\"count\":1}", 200);
		server.runCommand("gamemode creative @p");
		require(blocked.startsWith("failed") && blocked.contains("protected"), "protection did not apply to containers: " + blocked);
	}

	private void craftTools(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		// A 2x2 recipe needs no table.
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.OAK_PLANKS, 2)));
		String sticks = tool(server, v, "craft", "{\"item\":\"minecraft:stick\",\"count\":4}", 100);
		require(sticks.startsWith("ok: crafted 4"), "craft sticks said " + sticks);
		require(villagerCount(server, v, Items.STICK) == 4 && villagerCount(server, v, Items.OAK_PLANKS) == 0, "sticks were not made from the planks");
		require(tool(server, v, "craft", "{\"item\":\"minecraft:diamond_sword\",\"count\":1}", 100).startsWith("failed"), "crafted without materials");
		// A 3x3 recipe needs a crafting table.
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.IRON_INGOT, 3)));
		String noTable = tool(server, v, "craft", "{\"item\":\"minecraft:iron_pickaxe\",\"count\":1}", 100);
		require(noTable.startsWith("failed") && noTable.contains("crafting table"), "expected a crafting-table failure, got " + noTable);
		server.runCommand("setblock 3 100 0 minecraft:crafting_table");
		String pick = tool(server, v, "craft", "{\"item\":\"minecraft:iron_pickaxe\",\"count\":1}", 600);
		require(pick.startsWith("ok: crafted 1"), "craft pickaxe said " + pick);
		require(villagerCount(server, v, Items.IRON_PICKAXE) == 1, "no pickaxe in the inventory");
		require(villagerCount(server, v, Items.IRON_INGOT) == 0 && villagerCount(server, v, Items.STICK) == 2, "ingredients were not used correctly");
	}

	private void smeltTool(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> {
			VillagerInventories.get(v).addItem(new ItemStack(Items.RAW_IRON, 2));
			VillagerInventories.get(v).addItem(new ItemStack(Items.COAL, 4));
		});
		String none = tool(server, v, "smelt", "{\"item\":\"minecraft:raw_iron\",\"count\":2}", 100);
		require(none.startsWith("failed") && none.contains("furnace"), "expected a no-furnace failure, got " + none);
		server.runCommand("setblock -3 100 0 minecraft:furnace");
		String result = tool(server, v, "smelt", "{\"item\":\"minecraft:raw_iron\",\"count\":2}", 2400);
		require(result.startsWith("ok: smelted"), "smelt said " + result);
		require(villagerCount(server, v, Items.IRON_INGOT) == 2 && villagerCount(server, v, Items.RAW_IRON) == 0, "iron was not smelted into ingots");
		require(villagerCount(server, v, Items.COAL) >= 3, "too much coal was used for 2 items");
		require(tool(server, v, "smelt", "{\"item\":\"minecraft:diamond\",\"count\":1}", 100).startsWith("failed"), "smelting a diamond should fail");
	}

	private void brewTool(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runCommand("setblock 0 100 -3 minecraft:brewing_stand");
		server.runOnServer(srv -> {
			VillagerInventories.get(v).addItem(net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,
					net.minecraft.world.item.alchemy.Potions.WATER));
			VillagerInventories.get(v).addItem(net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION,
					net.minecraft.world.item.alchemy.Potions.WATER));
			VillagerInventories.get(v).addItem(new ItemStack(Items.NETHER_WART, 1));
			VillagerInventories.get(v).addItem(new ItemStack(Items.BLAZE_POWDER, 1));
		});
		String result = tool(server, v, "brew", "{\"item\":\"minecraft:nether_wart\"}", 1800);
		require(result.startsWith("ok: brewed 2") && result.toLowerCase().contains("awkward"), "brew said " + result);
		require(villagerCount(server, v, Items.NETHER_WART) == 0 && villagerCount(server, v, Items.BLAZE_POWDER) == 0, "ingredient or fuel not used");
		require(tool(server, v, "brew", "{\"item\":\"minecraft:nether_wart\"}", 100).startsWith("failed"), "brewing with nothing should fail");
	}

	private void chopTree(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		server.runCommand("fill 4 100 0 4 103 0 minecraft:oak_log");
		server.runCommand("setblock 4 104 0 minecraft:oak_leaves");
		Villager v = spawnVillager(server, 0, 100, 0);
		String result = tool(server, v, "chop_tree", "{\"x\":4,\"y\":100,\"z\":0}", 3000);
		require(result.startsWith("ok: chopped 4"), "chop_tree said " + result);
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(new BlockPos(4, 103, 0)).isAir()), "the top log is still there");
		require(tool(server, v, "chop_tree", "{\"x\":9,\"y\":100,\"z\":0}", 100).contains("no log"), "chopping thin air should fail");
	}

	private void farming(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		server.runCommand("setblock 3 99 0 minecraft:dirt");
		Villager v = spawnVillager(server, 0, 100, 0);
		require(tool(server, v, "till", "{\"x\":3,\"y\":99,\"z\":0}", 100).contains("hoe"), "tilling without a hoe should fail");
		server.runOnServer(srv -> {
			VillagerInventories.get(v).addItem(new ItemStack(Items.IRON_HOE, 1));
			VillagerInventories.get(v).addItem(new ItemStack(Items.WHEAT_SEEDS, 2));
		});
		String tilled = tool(server, v, "till", "{\"x\":3,\"y\":99,\"z\":0}", 400);
		require(tilled.startsWith("ok: tilled"), "till said " + tilled);
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(new BlockPos(3, 99, 0)).is(Blocks.FARMLAND)), "no farmland");
		String planted = tool(server, v, "plant", "{\"item\":\"minecraft:wheat_seeds\",\"x\":3,\"y\":100,\"z\":0}", 400);
		require(planted.startsWith("ok: placed"), "plant said " + planted);
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(new BlockPos(3, 100, 0)).is(Blocks.WHEAT)), "no wheat");
		require(tool(server, v, "harvest", "{\"x\":3,\"y\":100,\"z\":0}", 100).contains("not ripe"), "a young crop was harvested");
		server.runCommand("setblock 3 100 0 minecraft:wheat[age=7]");
		String harvested = tool(server, v, "harvest", "{\"x\":3,\"y\":100,\"z\":0}", 800);
		require(harvested.contains("replanted"), "harvest said " + harvested);
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(new BlockPos(3, 100, 0)).is(Blocks.WHEAT)), "crop was not replanted");
	}

	private void animals(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		// shear
		server.runCommand("summon minecraft:sheep 2 100 1 {NoAI:1b}");
		require(tool(server, v, "shear", "{}", 100).contains("shears"), "shearing without shears should fail");
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.SHEARS, 1)));
		String sheared = tool(server, v, "shear", "{}", 600);
		require(sheared.startsWith("ok: sheared"), "shear said " + sheared);
		require(server.computeOnServer(srv -> srv.overworld().getEntitiesOfClass(
				net.minecraft.world.entity.animal.sheep.Sheep.class, new AABB(-8, 95, -8, 8, 110, 8)).get(0).isSheared()), "the sheep was not sheared");
		server.runCommand("kill @e[type=minecraft:sheep]");
		// milk
		server.runCommand("summon minecraft:cow 2 100 1 {NoAI:1b}");
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.BUCKET, 1)));
		String milked = tool(server, v, "milk", "{}", 600);
		require(milked.startsWith("ok: milked"), "milk said " + milked);
		require(villagerCount(server, v, Items.MILK_BUCKET) == 1 && villagerCount(server, v, Items.BUCKET) == 0, "the bucket was not filled");
		server.runCommand("kill @e[type=minecraft:cow]");
		// breed
		server.runCommand("summon minecraft:cow 2 100 1 {NoAI:1b}");
		server.runCommand("summon minecraft:cow 3 100 3 {NoAI:1b}");
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.WHEAT, 4)));
		String bred = tool(server, v, "breed", "{}", 900);
		require(bred.contains("fed 2"), "breed said " + bred);
		require(2 == server.computeOnServer(srv -> srv.overworld().getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class,
				new AABB(-8, 95, -8, 8, 110, 8), a -> a.isInLove()).size()), "both cows should be in love");
		server.runCommand("kill @e[type=minecraft:cow]");
		// tame
		server.runCommand("summon minecraft:wolf 2 100 1 {NoAI:1b}");
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.BONE, 32)));
		String tamed = tool(server, v, "tame", "{}", 1200);
		require(tamed.startsWith("ok: tamed"), "tame said " + tamed);
		require(tool(server, v, "shear", "{}", 100).startsWith("failed"), "no sheep is left to shear");
	}

	private void fishing(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		server.runCommand("fill 2 100 2 4 100 4 minecraft:water");
		Villager v = spawnVillager(server, 0, 100, 0);
		require(tool(server, v, "fish", "{\"seconds\":20}", 100).contains("rod"), "fishing without a rod should fail");
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.FISHING_ROD, 1)));
		String result = tool(server, v, "fish", "{\"seconds\":20}", 900);
		require(result.startsWith("ok: caught 1"), "fish said " + result);
		require(villagerCount(server, v, Items.FISHING_ROD) == 1, "the rod should survive one catch");
	}

	private static int mobId(TestServerContext server, String type, double x, double y, double z) {
		server.runCommand("difficulty easy"); // hostile mobs vanish on peaceful
		server.runCommand("summon " + type + " " + x + " " + y + " " + z + " {NoAI:1b,PersistenceRequired:1b}");
		return server.computeOnServer(srv -> {
			List<net.minecraft.world.entity.Mob> found = srv.overworld().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,
					new AABB(x - 1, y - 1, z - 1, x + 1, y + 2, z + 1), m -> !(m instanceof Villager));
			require(!found.isEmpty(), type + " did not spawn");
			return found.get(0).getId();
		});
	}

	private static float healthOf(TestServerContext server, int id) {
		return server.computeOnServer(srv -> srv.overworld().getEntity(id) instanceof net.minecraft.world.entity.LivingEntity l
				? l.getHealth() : -1f);
	}

	private void combatAttack(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> v.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)));
		int husk = mobId(server, "minecraft:husk", 3, 100, 0);
		String result = tool(server, v, "attack", "{\"entity_id\":" + husk + "}", 1500);
		require(result.startsWith("ok: killed"), "attack said " + result);
		require(healthOf(server, husk) <= 0f || server.computeOnServer(srv -> srv.overworld().getEntity(husk) == null
				|| !srv.overworld().getEntity(husk).isAlive()), "the husk is still alive");
		require(tool(server, v, "attack", "{\"entity_id\":99999}", 100).startsWith("failed"), "attacking a missing id should fail");
	}

	private void combatNeverHurtsPlayersOrPets(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> v.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)));
		int wolf = mobId(server, "minecraft:wolf", 2, 100, 0);
		server.runOnServer(srv -> ((net.minecraft.world.entity.TamableAnimal) srv.overworld().getEntity(wolf))
				.tame(srv.getPlayerList().getPlayers().get(0)));
		int king = server.computeOnServer(srv -> srv.getPlayerList().getPlayers().get(0).getId());
		float wolfHealth = healthOf(server, wolf);
		String petResult = tool(server, v, "attack", "{\"entity_id\":" + wolf + "}", 200);
		require(petResult.startsWith("failed") && petResult.contains("won't hurt"), "attacking a pet was not refused: " + petResult);
		String playerResult = tool(server, v, "attack", "{\"entity_id\":" + king + "}", 200);
		require(playerResult.startsWith("failed") && playerResult.contains("won't hurt"), "attacking the king was not refused: " + playerResult);
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.BOW)));
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.ARROW, 4)));
		String shot = tool(server, v, "shoot", "{\"entity_id\":" + wolf + "}", 200);
		require(shot.startsWith("failed") && shot.contains("won't hurt"), "shooting a pet was not refused: " + shot);
		require(healthOf(server, wolf) == wolfHealth, "the pet was hurt");
		require(villagerCount(server, v, Items.ARROW) == 4, "an arrow was spent on a refused shot");
	}

	private void combatShoot(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		int husk = mobId(server, "minecraft:husk", 8, 100, 0);
		require(tool(server, v, "shoot", "{\"entity_id\":" + husk + "}", 100).contains("bow"), "shooting without a bow should fail");
		server.runOnServer(srv -> {
			VillagerInventories.get(v).addItem(new ItemStack(Items.BOW));
			VillagerInventories.get(v).addItem(new ItemStack(Items.ARROW, 4));
		});
		float before = healthOf(server, husk);
		String shot = tool(server, v, "shoot", "{\"entity_id\":" + husk + "}", 200);
		require(shot.startsWith("ok: shot"), "shoot said " + shot);
		require(villagerCount(server, v, Items.ARROW) == 3, "the arrow was not used up");
		server.waitFor(srv -> healthOf(server, husk) < before, 200);
		require(healthOf(server, husk) < before, "the arrow never hit the husk");
	}

	private void combatGuardAndFlee(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> v.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD)));
		int cat = mobId(server, "minecraft:cat", 4, 100, 2);
		server.runOnServer(srv -> ((net.minecraft.world.entity.TamableAnimal) srv.overworld().getEntity(cat))
				.tame(srv.getPlayerList().getPlayers().get(0)));
		int husk = mobId(server, "minecraft:husk", 5, 100, 0);
		String started = tool(server, v, "guard", "{}", 100);
		require(started.startsWith("ok"), "guard said " + started);
		server.waitFor(srv -> healthOf(server, husk) <= 0f, 1500);
		require(healthOf(server, husk) <= 0f, "the guard did not kill the nearby husk");
		require(server.computeOnServer(srv -> ((net.minecraft.world.entity.LivingEntity) srv.overworld().getEntity(cat)).getHealth()
				== ((net.minecraft.world.entity.LivingEntity) srv.overworld().getEntity(cat)).getMaxHealth()), "the guard hurt a pet");
		require(tool(server, v, "stop", "{}", 50).startsWith("ok"), "stop failed");
		// flee: runs away from a hostile creature.
		server.runCommand("kill @e[type=minecraft:cat]");
		int threat = mobId(server, "minecraft:husk", 2, 100, 0);
		double before = server.computeOnServer(srv -> v.distanceTo(srv.overworld().getEntity(threat)));
		String fled = tool(server, v, "flee", "{\"seconds\":4}", 400);
		require(fled.startsWith("ok: ran away"), "flee said " + fled);
		double after = server.computeOnServer(srv -> v.distanceTo(srv.overworld().getEntity(threat)));
		require(after > before + 5, "the villager did not get away: " + before + " -> " + after);
	}

	/** Lets the server run for a number of ticks. */
	private static void waitTicks(TestServerContext server, int ticks) {
		int start = server.computeOnServer(srv -> srv.getTickCount());
		server.waitFor(srv -> srv.getTickCount() - start >= ticks, ticks + 40);
	}

	private void sitAndSleep(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		// sit: stays put while an ordinary villager would wander
		require(tool(server, v, "sit", "{}", 50).startsWith("ok"), "sit failed");
		Vec3 sat = server.computeOnServer(srv -> v.position());
		waitTicks(server, 80);
		require(server.computeOnServer(srv -> v.position().distanceTo(sat)) < 0.5, "the villager moved while sitting");
		require(tool(server, v, "stop", "{}", 20).startsWith("ok"), "stop failed");
		// sleep: not in daytime, yes at night, and it wakes at dawn
		server.runCommand("setblock 3 100 0 minecraft:red_bed[facing=east,part=foot]");
		server.runCommand("setblock 4 100 0 minecraft:red_bed[facing=east,part=head]");
		server.runCommand("time set noon");
		waitTicks(server, 5);
		String day = tool(server, v, "sleep", "{\"x\":3,\"y\":100,\"z\":0}", 100);
		require(day.startsWith("failed") && day.contains("night"), "sleeping by day should fail: " + day);
		server.runCommand("time set midnight");
		server.waitFor(srv -> srv.overworld().isDarkOutside(), 60);
		String night = tool(server, v, "sleep", "{\"x\":3,\"y\":100,\"z\":0}", 800);
		require(night.startsWith("ok"), "sleep said " + night);
		require(server.computeOnServer(srv -> v.isSleeping()), "the villager is not asleep");
		waitTicks(server, 40);
		require(server.computeOnServer(srv -> v.isSleeping()), "the villager woke up too early");
		server.runCommand("time set noon");
		server.waitFor(srv -> !v.isSleeping(), 200);
		require(!server.computeOnServer(srv -> v.isSleeping()), "the villager did not wake at dawn");
		require(tool(server, v, "sleep", "{\"x\":9,\"y\":100,\"z\":9}", 100).startsWith("failed"), "sleeping with no bed should fail");
	}

	private static String scriptArgs(String script) {
		com.google.gson.JsonObject o = new com.google.gson.JsonObject();
		o.addProperty("script", script);
		return o.toString();
	}

	private void scripts(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		server.runCommand("setblock 3 100 0 minecraft:stone");
		server.runCommand("setblock 4 100 0 minecraft:stone");
		server.runCommand("setblock 5 100 0 minecraft:stone");
		Villager v = spawnVillager(server, 0, 100, 0);
		ollama.script(reply("unused"));
		// Three mining steps run with no model call at all.
		String three = tool(server, v, "run_script", scriptArgs("{\"steps\":["
				+ "{\"tool\":\"mine_block\",\"args\":{\"x\":3,\"y\":100,\"z\":0}},"
				+ "{\"tool\":\"mine_block\",\"args\":{\"x\":4,\"y\":100,\"z\":0}},"
				+ "{\"tool\":\"mine_block\",\"args\":{\"x\":5,\"y\":100,\"z\":0}}]}"), 3000);
		require(three.equals("ok: ran 3 steps"), "script said " + three);
		for (int x = 3; x <= 5; x++) {
			int px = x;
			require(server.computeOnServer(srv -> srv.overworld().getBlockState(new BlockPos(px, 100, 0)).isAir()), "block " + x + " was not mined");
		}
		require(ollama.requests.isEmpty(), "the script called the model");
		// A failing step stops the run and names the step.
		server.runCommand("setblock 3 100 0 minecraft:stone");
		server.runCommand("setblock 4 100 0 minecraft:stone");
		String failing = tool(server, v, "run_script", scriptArgs("{\"steps\":["
				+ "{\"tool\":\"mine_block\",\"args\":{\"x\":3,\"y\":100,\"z\":0}},"
				+ "{\"tool\":\"mine_block\",\"args\":{\"x\":3,\"y\":100,\"z\":6}},"
				+ "{\"tool\":\"mine_block\",\"args\":{\"x\":4,\"y\":100,\"z\":0}}]}"), 3000);
		require(failing.startsWith("failed: step 2 mine_block") && failing.contains("no block"), "failing script said " + failing);
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(new BlockPos(4, 100, 0)).is(Blocks.STONE)), "a step ran after the failure");
		// A script the parser rejects never starts.
		require(tool(server, v, "run_script", scriptArgs("{\"steps\":[{\"tool\":\"fly\"}]}"), 50).startsWith("invalid:"), "unknown tool accepted");
		require(tool(server, v, "run_script", scriptArgs("{\"steps\":[{\"tool\":\"run_script\",\"args\":{}}]}"), 50).startsWith("invalid:"), "nested script accepted");
		// Conditions read the real world.
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.OAK_LOG, 5)));
		String conditional = tool(server, v, "run_script", scriptArgs("{\"steps\":["
				+ "{\"if\":{\"have\":{\"item\":\"minecraft:oak_log\",\"count\":5}},\"then\":[{\"tool\":\"drop\",\"args\":{\"item\":\"minecraft:oak_log\",\"count\":5}}],\"else\":[{\"tool\":\"look_around\"}]},"
				+ "{\"if\":{\"has_block\":{\"x\":4,\"y\":100,\"z\":0}},\"then\":[{\"tool\":\"mine_block\",\"args\":{\"x\":4,\"y\":100,\"z\":0}}]}]}"), 3000);
		require(conditional.equals("ok: ran 2 steps"), "conditional script said " + conditional);
		require(villagerCount(server, v, Items.OAK_LOG) == 0, "the have-condition did not see the logs");
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(new BlockPos(4, 100, 0)).isAir()), "the has_block condition did not see the stone");
		// The step limit applies.
		String limited = tool(server, v, VillagingConfigWithScriptSteps(3), "run_script",
				scriptArgs("{\"steps\":[{\"repeat\":10,\"steps\":[{\"tool\":\"look_around\"}]}]}"), 600);
		require(limited.startsWith("stopped") && limited.contains("limit of 3"), "limit script said " + limited);
		// stop ends a running script.
		CompletableFuture<String> long1 = toolAsync(server, v, "run_script", scriptArgs("{\"steps\":[{\"repeat\":20,\"steps\":["
				+ "{\"tool\":\"go_to\",\"args\":{\"x\":12,\"y\":100,\"z\":0}},{\"tool\":\"go_to\",\"args\":{\"x\":-12,\"y\":100,\"z\":0}}]}]}"));
		waitTicks(server, 40);
		require(!long1.isDone(), "the long script finished too early");
		require(tool(server, v, "stop", "{}", 50).startsWith("ok"), "stop failed");
		String stopped = awaitResult(server, long1, 200);
		require(stopped.startsWith("stopped") || stopped.startsWith("failed"), "the script did not end after stop: " + stopped);
		waitTicks(server, 20);
		require(!server.computeOnServer(srv -> ActionManager.isControlled(v)), "the villager is still under script control after stop");
	}

	private static VillagingConfig VillagingConfigWithScriptSteps(int steps) {
		VillagingConfig d = VillagingConfig.defaults();
		return new VillagingConfig(d.provider(), d.apiKey(), d.model(), d.cloudUrl(), d.ollamaUrl(), d.ollamaModel(),
				d.geminiModel(), d.geminiUrl(), d.maxSteps(), d.callsPerMinute(), d.actionRange(), d.respectProtection(),
				d.allowBreakBedrock(), steps, d.maxScriptSeconds(), d.maxScriptDepth());
	}

	private void scriptRiskyStepAsks(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> {
			v.setNoAi(true);
			VillagerInventories.get(v).addItem(new ItemStack(Items.TNT, 2));
		});
		CompletableFuture<String> run = toolAsync(server, v, "run_script", scriptArgs("{\"steps\":["
				+ "{\"tool\":\"place_block\",\"args\":{\"item\":\"minecraft:tnt\",\"x\":3,\"y\":100,\"z\":1}}]}"));
		answerDialog(context, "No");
		String result = awaitResult(server, run, 300);
		require(result.startsWith("failed: step 1 place_block") && result.contains("the king said no"), "risky script step said " + result);
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(new BlockPos(3, 100, 1)).isAir()), "TNT was placed without a yes");
	}

	private void commanderRelay(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager alma = spawnVillager(server, 0, 100, 0);
		Villager bram = spawnVillager(server, 3, 100, 0);
		server.runOnServer(srv -> {
			VillagerBrains.set(alma, VillagerBrain.DEFAULT.makeSmart("Alma"));
			VillagerBrains.set(bram, VillagerBrain.DEFAULT.makeSmart("Bram"));
		});
		String order = "{\"villager_name\":\"Bram\",\"order\":\"gather some wood\"}";
		// Only commanders may relay.
		require(tool(server, alma, "relay_order", order, 100).contains("only a commander"), "a plain villager relayed an order");
		server.runOnServer(srv -> VillagerBrains.set(alma, VillagerBrains.get(alma).withCommander(true)));
		require(tool(server, alma, "relay_order", "{\"villager_name\":\"Zed\",\"order\":\"x\"}", 100).contains("can't find"), "unknown villager accepted");
		// The king says no: nothing reaches Bram.
		CompletableFuture<String> refused = toolAsync(server, alma, "relay_order", order);
		answerDialog(context, "No");
		require(awaitResult(server, refused, 100).contains("the king said no"), "a no was not reported");
		waitTicks(server, 20);
		require(server.computeOnServer(srv -> VillagerBrains.get(bram).memory().isEmpty()), "Bram heard the order after a no");
		// The king says yes: Bram receives it as a command and answers through the normal model path.
		ollama.script(reply("On it, my king."));
		CompletableFuture<String> approved = toolAsync(server, alma, "relay_order", order);
		answerDialog(context, "Yes");
		String result = awaitResult(server, approved, 100);
		require(result.startsWith("ok: passed the order to Bram"), "relay said " + result);
		server.waitFor(srv -> VillagerBrains.get(bram).memory().size() >= 2, 600);
		VillagerBrain heard = server.computeOnServer(srv -> VillagerBrains.get(bram));
		require(heard.memory().contains("king: gather some wood"), "Bram's memory lacks the order: " + heard.memory());
		require(ollama.requests.size() >= 1 && ollama.requests.get(0).contains("gather some wood"), "the model never saw the relayed order");
	}

	/** One typed command, one model call that writes a script, and a finished chest at the end. */
	private void getLogsAndMakeAChest(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		server.runCommand("fill 4 100 0 4 103 0 minecraft:oak_log");
		server.runCommand("setblock -3 100 0 minecraft:crafting_table");
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> VillagerBrains.set(v, VillagerBrain.DEFAULT.makeSmart("Alma")));
		int id = server.computeOnServer(srv -> v.getId());
		String script = "{\"steps\":["
				+ "{\"tool\":\"chop_tree\",\"args\":{\"x\":4,\"y\":100,\"z\":0}},"
				+ "{\"tool\":\"pick_up\",\"args\":{\"item\":\"minecraft:oak_log\"}},"
				+ "{\"tool\":\"craft\",\"args\":{\"item\":\"minecraft:oak_planks\",\"count\":8}},"
				+ "{\"tool\":\"craft\",\"args\":{\"item\":\"minecraft:chest\",\"count\":1}},"
				+ "{\"tool\":\"place_block\",\"args\":{\"item\":\"minecraft:chest\",\"x\":3,\"y\":100,\"z\":1}}]}";
		com.google.gson.JsonObject scriptArgs = new com.google.gson.JsonObject();
		scriptArgs.addProperty("script", script);
		ollama.script(toolCall("run_script", scriptArgs.toString()), reply("Your chest is ready, my king."));
		context.runOnClient(mc -> ClientPlayNetworking.send(new CommandPayload(id, "get some logs and make me a chest")));
		server.waitFor(srv -> srv.overworld().getBlockState(new BlockPos(3, 100, 1)).is(Blocks.CHEST), 6000);
		context.takeScreenshot("villaging-chest-built");
		require(server.computeOnServer(srv -> srv.overworld().getBlockState(new BlockPos(3, 100, 1)).is(Blocks.CHEST)), "no chest was built");
		server.waitFor(srv -> ollama.requests.size() >= 2, 600);
		require(ollama.requests.size() == 2, "expected exactly 2 model calls (the order, then the result), saw " + ollama.requests.size());
		require(ollama.requests.get(1).contains("ok: ran 5 steps"), "the model was not told the script finished: " + ollama.requests.get(1));
		require(server.computeOnServer(srv -> v.getCustomName() != null), "the villager lost its name");
	}

	private static ItemStack named(net.minecraft.world.item.Item item, int count, String name) {
		ItemStack stack = new ItemStack(item, count);
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
		return stack;
	}

	private static boolean hasName(ItemStack stack, String name) {
		net.minecraft.network.chat.Component n = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
		return n != null && n.getString().equals(name);
	}

	/** A closed question (the call was cancelled) must not be answerable later. */
	private void reviewCancelledConfirmation(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		server.runCommand("clear @p");
		server.runCommand("give @p minecraft:gold_ingot 10");
		Villager v = spawnVillager(server, 0, 100, 0);
		CompletableFuture<String> take = toolAsync(server, v, "take_from_king", "{\"item\":\"minecraft:gold_ingot\",\"count\":10}");
		context.waitForScreen(AskScreen.class);
		server.runOnServer(srv -> take.cancel(true)); // a newer command cancels the old call
		waitTicks(server, 10);
		context.runOnClient(mc -> ((AskScreen) mc.gui.screen()).choose("Yes")); // the stale dialog is still on screen
		waitTicks(server, 20);
		require(kingCount(server, Items.GOLD_INGOT) == 10 && villagerCount(server, v, Items.GOLD_INGOT) == 0,
				"a cancelled confirmation still took the items");
	}

	/** Once approved, a risky act must still be stoppable. */
	private void reviewApprovedActCanBeCancelled(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		server.runCommand("fill 2 100 0 6 103 3 minecraft:stone");
		Villager v = spawnVillager(server, 0, 100, 4);
		CompletableFuture<String> run = toolAsync(server, v, "break_area", "{\"x1\":2,\"y1\":100,\"z1\":0,\"x2\":6,\"y2\":103,\"z2\":3}");
		answerDialog(context, "Yes");
		waitTicks(server, 40);
		require(server.computeOnServer(srv -> ActionManager.isControlled(v)), "the approved act never started");
		server.runOnServer(srv -> run.cancel(true));
		waitTicks(server, 20);
		require(!server.computeOnServer(srv -> ActionManager.isControlled(v)), "the villager kept working after the call was cancelled");
	}

	/** Many small jobs add up to mass destruction and then ask. */
	private void reviewDestructionTotalAsks(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		MinecraftActionExecutor exec = server.computeOnServer(srv -> new MinecraftActionExecutor(srv, v, playerId(server), VillagingConfig.defaults()));
		String box = "{\"x1\":2,\"y1\":110,\"z1\":2,\"x2\":5,\"y2\":113,\"z2\":5}"; // 4x4x4 = 64 blocks of air each time
		for (int i = 0; i < 2; i++) {
			CompletableFuture<String> call = server.computeOnServer(srv -> exec.execute(new ToolCall("t", "break_area",
					com.google.gson.JsonParser.parseString(box).getAsJsonObject())));
			if (i == 0) {
				require(awaitResult(server, call, 100).startsWith("ok"), "the first 64 blocks should not need a question");
			} else {
				answerDialog(context, "No");
				require(awaitResult(server, call, 100).contains("the king said no"), "the running total did not trigger the question");
			}
		}
	}

	/** Names, enchantments and contents survive being handed over; and no oversized stacks. */
	private void reviewItemDataSurvives(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		server.runCommand("clear @p");
		server.runCommand("setblock 3 100 0 minecraft:chest");
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(named(Items.DIAMOND_SWORD, 1, "Excalibur")));
		require(tool(server, v, "put_in", "{\"x\":3,\"y\":100,\"z\":0,\"item\":\"minecraft:diamond_sword\",\"count\":1}", 600).startsWith("ok: put 1"), "put_in failed");
		require(server.computeOnServer(srv -> hasName(((net.minecraft.world.Container) srv.overworld().getBlockEntity(new BlockPos(3, 100, 0))).getItem(0), "Excalibur")),
				"the sword lost its name in the chest");
		require(tool(server, v, "take_out", "{\"x\":3,\"y\":100,\"z\":0,\"item\":\"minecraft:diamond_sword\",\"count\":1}", 600).startsWith("ok: took 1"), "take_out failed");
		require(tool(server, v, "give_to_king", "{\"item\":\"minecraft:diamond_sword\",\"count\":1}", 600).startsWith("ok: gave"), "give failed");
		require(server.computeOnServer(srv -> {
			for (ItemStack s : srv.getPlayerList().getPlayers().get(0).getInventory().getNonEquipmentItems()) {
				if (s.is(Items.DIAMOND_SWORD) && hasName(s, "Excalibur")) {
					return true;
				}
			}
			return false;
		}), "the sword lost its name on the way to the king");
		// The king's stacks move over whole and never as one oversized stack.
		server.runCommand("give @p minecraft:cobblestone 500");
		CompletableFuture<String> take = toolAsync(server, v, "take_from_king", "{\"item\":\"minecraft:cobblestone\",\"count\":500}");
		answerDialog(context, "Yes");
		require(awaitResult(server, take, 100).startsWith("ok: took 500"), "take_from_king 500 failed");
		require(villagerCount(server, v, Items.COBBLESTONE) == 500, "wrong cobblestone count");
		require(server.computeOnServer(srv -> {
			VillagerInventory inv = VillagerInventories.get(v);
			for (int i = 0; i < inv.getContainerSize(); i++) {
				if (inv.getItem(i).getCount() > inv.getItem(i).getMaxStackSize()) {
					return false;
				}
			}
			return true;
		}), "an oversized stack was created");
		// drop keeps names too
		server.runOnServer(srv -> VillagerInventories.get(v).addItem(named(Items.IRON_AXE, 1, "Splitter")));
		require(tool(server, v, "drop", "{\"item\":\"minecraft:iron_axe\",\"count\":1}", 50).startsWith("ok: dropped"), "drop failed");
		require(server.computeOnServer(srv -> srv.overworld().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				new AABB(-6, 98, -6, 6, 104, 6), e -> hasName(e.getItem(), "Splitter")).size()) == 1, "the dropped axe lost its name");
	}

	/** With protection off, the range limit is skipped too. */
	private void reviewRangeSkippedWithoutProtection(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		require(tool(server, v, "look_at", "{\"x\":400,\"y\":100,\"z\":0}", 50).startsWith("invalid:"), "the default range should refuse a far block");
		require(!tool(server, v, configWith(false, false), "look_at", "{\"x\":400,\"y\":100,\"z\":0}", 50).startsWith("invalid:"),
				"respectProtection=false should skip the range limit");
	}

	/** A villager never shoots through the king or a pet. */
	private void reviewLineOfFire(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		int husk = mobId(server, "minecraft:husk", 9, 100, 0);
		server.runOnServer(srv -> {
			VillagerInventories.get(v).addItem(new ItemStack(Items.BOW));
			VillagerInventories.get(v).addItem(new ItemStack(Items.ARROW, 4));
		});
		server.runCommand("tp @p 4 100 0");
		String blocked = tool(server, v, "shoot", "{\"entity_id\":" + husk + "}", 200);
		require(blocked.startsWith("failed") && blocked.contains("line of fire"), "shot through the king: " + blocked);
		require(villagerCount(server, v, Items.ARROW) == 4, "an arrow was spent");
		server.runCommand("tp @p 0 100 6");
		require(tool(server, v, "shoot", "{\"entity_id\":" + husk + "}", 200).startsWith("ok: shot"), "a clear shot should work");
	}

	private static String toolCall(String name, String argsJson) {
		return "{\"message\":{\"role\":\"assistant\",\"content\":\"\",\"tool_calls\":[{\"function\":{\"name\":\"" + name
				+ "\",\"arguments\":" + argsJson + "}}]}}";
	}

	private static String reply(String text) {
		return "{\"message\":{\"role\":\"assistant\",\"content\":\"" + text + "\"}}";
	}

	/** Sends a typed command for the villager and waits until the scripted model has been called {@code calls} times. */
	private void command(ClientGameTestContext context, TestServerContext server, Villager v, String text, int calls)
			throws Exception {
		int id = server.computeOnServer(srv -> v.getId());
		context.runOnClient(mc -> ClientPlayNetworking.send(new CommandPayload(id, text)));
	}

	private void answerDialog(ClientGameTestContext context, String answer) {
		context.waitForScreen(AskScreen.class);
		context.runOnClient(mc -> ((AskScreen) mc.gui.screen()).choose(answer));
	}

	private void riskGate(ClientGameTestContext context, TestServerContext server) throws Exception {
		try {
			resetArena(server);
			makeKing(server);
			Villager v = spawnVillager(server, 0, 100, 0);
			server.runOnServer(srv -> VillagerBrains.set(v, VillagerBrain.DEFAULT.makeSmart("Alma")));
			server.runOnServer(srv -> VillagerInventories.get(v).addItem(new ItemStack(Items.TNT, 3)));
			server.runOnServer(srv -> v.setNoAi(true)); // a wandering villager could step onto the target block
			BlockPos first = new BlockPos(3, 100, 0);
			BlockPos second = new BlockPos(3, 100, 1);

			// "No": nothing is placed and the model is told.
			ollama.script(toolCall("place_block", "{\"item\":\"minecraft:tnt\",\"x\":3,\"y\":100,\"z\":0}"), reply("Understood."));
			command(context, server, v, "put tnt there", 2);
			answerDialog(context, "No");
			server.waitFor(srv -> ollama.requests.size() >= 2, 600);
			require(ollama.requests.size() >= 2 && ollama.requests.get(1).contains("the king said no"),
					"the model was not told the king said no");
			require(server.computeOnServer(srv -> srv.overworld().getBlockState(first).isAir()), "TNT was placed after a no");

			// "Yes": placed.
			ollama.script(toolCall("place_block", "{\"item\":\"minecraft:tnt\",\"x\":3,\"y\":100,\"z\":0}"), reply("Done."));
			command(context, server, v, "put tnt there", 2);
			answerDialog(context, "Yes");
			server.waitFor(srv -> ollama.requests.size() >= 2, 600);
			require(server.computeOnServer(srv -> srv.overworld().getBlockState(first).is(Blocks.TNT)), "TNT was not placed after a yes; villager at " + server.computeOnServer(srv -> v.position() + " noai=" + v.isNoAi() + " mainhand=" + v.getMainHandItem() + " inv=" + VillagerInventories.get(v).count(Items.TNT)));

			// Switching asking off needs the king's yes, and is remembered.
			ollama.script(toolCall("set_ask_preference", "{\"category\":\"explosive\",\"ask\":false}"), reply("Noted."));
			command(context, server, v, "you can stop asking about explosives", 2);
			answerDialog(context, "Yes");
			server.waitFor(srv -> VillagerBrains.get(v).askOff().contains("explosive"), 600);
			require(server.computeOnServer(srv -> VillagerBrains.get(v).askOff().contains("explosive")), "preference was not saved");

			// With asking off, no dialog appears.
			server.waitFor(srv -> ollama.requests.size() >= 2, 600);
			ollama.script(toolCall("place_block", "{\"item\":\"minecraft:tnt\",\"x\":3,\"y\":100,\"z\":1}"), reply("Done."));
			command(context, server, v, "again", 2);
			server.waitFor(srv -> srv.overworld().getBlockState(second).is(Blocks.TNT), 600);
			require(server.computeOnServer(srv -> srv.overworld().getBlockState(second).is(Blocks.TNT)), "TNT was not placed without asking");
			require(!context.computeOnClient(mc -> mc.gui.screen() instanceof AskScreen), "a dialog opened although asking is off");

			// Turning asking back on is immediate and needs no dialog.
			ollama.script(toolCall("set_ask_preference", "{\"category\":\"explosive\",\"ask\":true}"), reply("Noted."));
			command(context, server, v, "ask me again about explosives", 2);
			server.waitFor(srv -> !VillagerBrains.get(v).askOff().contains("explosive"), 600);
			require(server.computeOnServer(srv -> VillagerBrains.get(v).askOff().isEmpty()), "asking was not switched back on");
		} finally {
			// nothing to undo
		}
	}

	private void bubbleRestored(TestServerContext server) {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> VillagerBrains.set(v, VillagerBrain.DEFAULT.makeSmart("Alma")));
		UUID me = playerId(server);
		server.runOnServer(srv -> VillagerSpeech.say(srv, v, me, "Right away, my king!"));
		require(server.computeOnServer(srv -> v.getCustomName().getString()).contains("Right away"), "bubble was not shown");
		server.runOnServer(srv -> VillagerSpeech.clear());
		require("Alma".equals(server.computeOnServer(srv -> v.getCustomName().getString())),
				"name was not restored when the server stopped");
		// A name left behind by an older crash heals when the villager loads again.
		server.runOnServer(srv -> v.setCustomName(net.minecraft.network.chat.Component.literal(SpeechBubble.format("stale"))));
		server.runOnServer(srv -> VillagerSpeech.heal(v));
		require("Alma".equals(server.computeOnServer(srv -> v.getCustomName().getString())), "stale bubble was not healed");
		// A name the player chose is left alone.
		server.runOnServer(srv -> v.setCustomName(net.minecraft.network.chat.Component.literal("Sir Bob")));
		server.runOnServer(srv -> VillagerSpeech.heal(v));
		require("Sir Bob".equals(server.computeOnServer(srv -> v.getCustomName().getString())), "a player-chosen name was overwritten");
	}

	private void sneakClick(ClientGameTestContext context, TestServerContext server) {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> VillagerBrains.set(v, VillagerBrain.DEFAULT.makeSmart("Alma")));
		InteractionResult plain = server.computeOnServer(srv -> {
			ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
			p.setShiftKeyDown(false);
			return UseEntityCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, v, null);
		});
		require(plain == InteractionResult.SUCCESS_SERVER, "a plain right-click on a smart villager should open the chat, got " + plain);
		InteractionResult sneaking = server.computeOnServer(srv -> {
			ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
			p.setShiftKeyDown(true);
			InteractionResult r = UseEntityCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, v, null);
			p.setShiftKeyDown(false);
			return r;
		});
		require(sneaking == InteractionResult.PASS, "sneak-click on a smart villager should leave trading to vanilla, got " + sneaking);
		context.waitForScreen(VillagerChatScreen.class);
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	private void endToEnd(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> VillagerBrains.set(v, VillagerBrain.DEFAULT.makeSmart("Alma")));
		int id = server.computeOnServer(srv -> v.getId());
		ollama.script(
				"{\"message\":{\"role\":\"assistant\",\"content\":\"On my way.\",\"tool_calls\":[{\"function\":{\"name\":\"go_to\",\"arguments\":{\"x\":6,\"y\":100,\"z\":0}}}]}}",
				"{\"message\":{\"role\":\"assistant\",\"content\":\"I have arrived, my king.\"}}");

		context.runOnClient(mc -> mc.gui.setScreen(new VillagerChatScreen(id, "Alma")));
		context.waitForScreen(VillagerChatScreen.class);
		context.getInput().typeChars("please walk to the spot six blocks east");
		context.takeScreenshot("villaging-chat-screen");
		context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);

		server.waitFor(srv -> v.position().distanceTo(new Vec3(6, 100, 0)) <= 2.5 && ollama.requests.size() >= 2
				&& VillagerBrains.get(v).memory().contains("me: I have arrived, my king."), 1200);

		require(ollama.requests.size() == 2, "expected 2 model calls, saw " + ollama.requests.size());
		String first = ollama.requests.get(0);
		require(first.contains("please walk to the spot six blocks east"), "first call is missing the king's words");
		require(first.contains("Alma") && first.contains("go_to"), "first call is missing the prompt or tools");
		String second = ollama.requests.get(1);
		require(second.contains("\"role\":\"tool\"") && second.contains("ok: arrived"), "second call is missing the tool result");
		VillagerBrain brain = server.computeOnServer(srv -> VillagerBrains.get(v));
		require(brain.memory().contains("king: please walk to the spot six blocks east"), "memory missing king line: " + brain.memory());
		require(brain.memory().contains("me: I have arrived, my king."), "memory missing reply: " + brain.memory());
		require(server.computeOnServer(srv -> v.getCustomName() != null), "villager should show a name or bubble");
		// The screen stays open for the whole conversation and shows both sides of it.
		context.waitFor(mc -> mc.gui.screen() instanceof VillagerChatScreen chat
				&& chat.transcript().contains("Alma: I have arrived, my king."));
		List<String> shown = context.computeOnClient(mc -> ((VillagerChatScreen) mc.gui.screen()).transcript());
		require(shown.contains("You: please walk to the spot six blocks east"), "the king's line is missing on screen: " + shown);
		context.takeScreenshot("villaging-after-walk");
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	// --- blueprint checks ---

	private static final BlockPos SITE = new BlockPos(6, 100, -10);

	private record Site(Villager helper, int helperId) {
	}

	/** Build settings for a quick test: one tick per block and the given size above which the king is asked. */
	private static VillagingConfig buildConfig(int askThreshold) {
		VillagingConfig d = VillagingConfig.defaults();
		return new VillagingConfig(d.provider(), d.apiKey(), d.model(), d.cloudUrl(), d.ollamaUrl(), d.ollamaModel(), d.geminiModel(),
				d.geminiUrl(), d.maxSteps(), d.callsPerMinute(), d.actionRange(), d.respectProtection(), d.allowBreakBedrock(),
				d.maxScriptSteps(), d.maxScriptSeconds(), d.maxScriptDepth(), "", new BuildSettings(20_000, 50, 1, askThreshold, 3));
	}

	private static CompletableFuture<String> toolAsync(TestServerContext server, PathfinderMob v, VillagingConfig config, String name, String argsJson) {
		UUID king = playerId(server);
		return server.computeOnServer(srv -> {
			MinecraftActionExecutor exec = new MinecraftActionExecutor(srv, v, king, config);
			ToolCall call = new ToolCall("t", name, com.google.gson.JsonParser.parseString(argsJson).getAsJsonObject());
			Optional<String> bad = exec.validate(call);
			return bad.isPresent() ? CompletableFuture.completedFuture("invalid: " + bad.get()) : exec.execute(call);
		});
	}

	/** A fresh arena with a smart helper and a saved 5x5 stone cottage called {@code name}. */
	private static Site blueprintSite(TestServerContext server, String name) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = smartVillager(server, "Alma", 0, 100, 0);
		int id = server.computeOnServer(srv -> v.getId());
		String made = tool(server, v, buildConfig(1000), "use_template",
				"{\"name\":\"" + name + "\",\"template\":\"cottage\",\"width\":5,\"depth\":5,\"height\":3,\"style\":\"stone\",\"roof\":\"flat\"}", 100);
		require(made.startsWith("ok") && made.contains("blocks"), "use_template said: " + made);
		return new Site(v, id);
	}

	private static Blueprint savedBlueprint(TestServerContext server, String name) {
		return server.computeOnServer(srv -> BlueprintStore.load(BlueprintFiles.dir(srv), name, 20_000).orElseThrow());
	}

	private static void deleteBlueprints(TestServerContext server, String... names) {
		server.runOnServer(srv -> {
			for (String n : names) {
				BlueprintStore.delete(BlueprintFiles.dir(srv), n);
			}
		});
	}

	private static net.minecraft.world.item.Item itemNamed(String id) {
		return net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(id)).orElseThrow();
	}

	/** Gives the helper exactly what the blueprint needs, plus {@code extra} of each. */
	private static void stock(TestServerContext server, PathfinderMob v, Blueprint bp, int extra) {
		server.runOnServer(srv -> {
			for (MaterialPlanner.Row r : MaterialPlanner.plan(bp, java.util.Map.of(), java.util.Map.of()).rows()) {
				for (int left = r.needed() + extra; left > 0; left -= 64) {
					VillagerInventories.get(v).addItem(new ItemStack(itemNamed(r.item()), Math.min(64, left)));
				}
			}
		});
	}

	/** How many of the blueprint's items the helper still carries. */
	private static int carriedOf(TestServerContext server, PathfinderMob v, Blueprint bp) {
		return server.computeOnServer(srv -> {
			int total = 0;
			for (MaterialPlanner.Row r : MaterialPlanner.plan(bp, java.util.Map.of(), java.util.Map.of()).rows()) {
				total += VillagerInventories.get(v).count(itemNamed(r.item()));
			}
			return total;
		});
	}

	private static int builtNow(net.minecraft.server.MinecraftServer srv, Blueprint bp, int turns) {
		net.minecraft.server.level.ServerLevel level = srv.overworld();
		Blueprint rotated = bp.rotated(turns);
		int built = 0;
		for (Cell c : BuildOrder.order(rotated)) {
			net.minecraft.world.level.block.state.BlockState want =
					com.villaging.build.Blocks.rotate(com.villaging.build.Blocks.parse(level, rotated.stateAt(c)).orElseThrow(), turns);
			if (level.getBlockState(SITE.offset(c.x(), c.y(), c.z())) == want) {
				built++;
			}
		}
		return built;
	}

	private static int built(TestServerContext server, Blueprint bp, int turns) {
		return server.computeOnServer(srv -> builtNow(srv, bp, turns));
	}

	private static void awaitBuildDone(TestServerContext server, PathfinderMob v, int ticks) {
		server.waitFor(srv -> BuildJobs.get(v).isEmpty() && !BuildRegistry.isBuilding(v.getUUID()), ticks);
	}

	private static String planAt(TestServerContext server, PathfinderMob v, String name, int rotation) throws Exception {
		return tool(server, v, buildConfig(1000), "plan_build",
				"{\"name\":\"" + name + "\",\"x\":" + SITE.getX() + ",\"y\":" + SITE.getY() + ",\"z\":" + SITE.getZ() + ",\"rotation\":\"" + rotation + "\"}", 100);
	}

	private static String chatText(ClientGameTestContext context) {
		return context.computeOnClient(mc -> mc.gui.screen() instanceof VillagerChatScreen s ? String.join("\n", s.transcript()) : "");
	}

	private static void openChat(ClientGameTestContext context, Site site) {
		context.runOnClient(mc -> mc.gui.setScreen(new VillagerChatScreen(site.helperId(), "Alma")));
		context.waitForScreen(VillagerChatScreen.class);
	}

	private void blueprintPlanning(TestServerContext server) throws Exception {
		Site s = blueprintSite(server, "hut1");
		try {
			String layer = tool(server, s.helper(), buildConfig(1000), "show_blueprint", "{\"name\":\"hut1\",\"layer\":0}", 50);
			require(layer.startsWith("ok") && layer.contains("Layer 0") && layer.contains(" = minecraft:"), "show_blueprint said: " + layer);
			require(tool(server, s.helper(), buildConfig(1000), "list_blueprints", "{}", 50).contains("hut1"), "list_blueprints does not show hut1");
			require(tool(server, s.helper(), buildConfig(1000), "plan_build", "{\"name\":\"nope\",\"x\":6,\"y\":100,\"z\":-10}", 50)
					.contains("no blueprint called nope"), "an unknown blueprint should be named in the failure");

			server.runCommand("setblock 4 100 -6 minecraft:chest");
			server.runCommand("item replace block 4 100 -6 container.0 with minecraft:stone_bricks 30");
			String plan = tool(server, s.helper(), buildConfig(1000), "plan_build",
					"{\"name\":\"hut1\",\"x\":6,\"y\":100,\"z\":-10,\"stockpile_x\":4,\"stockpile_y\":100,\"stockpile_z\":-6}", 100);
			require(plan.startsWith("ok") && plan.contains("Missing") && plan.contains("stone bricks") && plan.contains("start_build"), "plan_build said: " + plan);
			BuildJob job = server.computeOnServer(srv -> BuildJobs.get(s.helper()).orElseThrow());
			require(job.status() == BuildJob.Status.PLANNED && job.total() > 60 && job.stockpile().isPresent(), "the plan was not saved as a job: " + job);
		} finally {
			deleteBlueprints(server, "hut1");
		}
	}

	private void blueprintBuild(ClientGameTestContext context, TestServerContext server, int quarterTurns) throws Exception {
		String name = quarterTurns == 0 ? "hut2" : "hut3";
		Site s = blueprintSite(server, name);
		try {
			Blueprint bp = savedBlueprint(server, name);
			stock(server, s.helper(), bp, 0);
			String plan = planAt(server, s.helper(), name, quarterTurns * 90);
			require(plan.contains("I have all the materials"), "plan_build said: " + plan);
			openChat(context, s);
			String started = tool(server, s.helper(), buildConfig(1000), "start_build", "{}", 100);
			require(started.startsWith("ok: building"), "start_build said: " + started);
			awaitBuildDone(server, s.helper(), 8000);
			int total = BuildOrder.order(bp).size();
			require(built(server, bp, quarterTurns) == total, "only " + built(server, bp, quarterTurns) + " of " + total + " blocks were built as planned");
			require(carriedOf(server, s.helper(), bp) == 0, "the materials were not used up exactly; " + carriedOf(server, s.helper(), bp) + " items are left");
			require(chatText(context).contains("is built"), "the helper did not say it was done: " + chatText(context));
			if (quarterTurns == 1) {
				// the cottage door faces north; turned a quarter turn clockwise it faces east
				Blueprint turned = bp.rotated(1);
				Cell lowerDoor = turned.cells().stream().filter(c -> turned.stateAt(c).contains("_door") && turned.stateAt(c).contains("half=lower")).findFirst().orElseThrow();
				net.minecraft.world.level.block.state.BlockState door = server.computeOnServer(srv ->
						srv.overworld().getBlockState(SITE.offset(lowerDoor.x(), lowerDoor.y(), lowerDoor.z())));
				require(door.getBlock() instanceof net.minecraft.world.level.block.DoorBlock
						&& door.getValue(net.minecraft.world.level.block.DoorBlock.FACING) == net.minecraft.core.Direction.EAST,
						"the turned door should face east, found " + door);
			}
			context.runOnClient(mc -> mc.gui.setScreen(null));
		} finally {
			deleteBlueprints(server, name);
		}
	}

	private void blueprintPausesAndResumes(ClientGameTestContext context, TestServerContext server) throws Exception {
		Site s = blueprintSite(server, "hut4");
		try {
			Blueprint bp = savedBlueprint(server, "hut4");
			stock(server, s.helper(), bp, 0);
			server.runOnServer(srv -> VillagerInventories.get(s.helper()).remove(Items.COBBLESTONE, 5));   // five short
			ollama.script("{\"message\":{\"role\":\"assistant\",\"content\":\"On it, my king.\"}}");
			planAt(server, s.helper(), "hut4", 0);
			openChat(context, s);
			require(tool(server, s.helper(), buildConfig(1000), "start_build", "{}", 100).startsWith("ok"), "start_build failed");
			server.waitFor(srv -> BuildJobs.get(s.helper()).map(j -> j.status() == BuildJob.Status.PAUSED_MISSING_MATERIAL).orElse(false), 4000);
			BuildJob paused = server.computeOnServer(srv -> BuildJobs.get(s.helper()).orElseThrow());
			require(paused.note().contains("I need 5 more cobblestone"), "the pause note was: " + paused.note());
			require(chatText(context).contains("I need 5 more cobblestone"), "the helper did not say what is missing: " + chatText(context));
			require(built(server, bp, 0) == 20, "twenty floor blocks should be placed before the pause, found " + built(server, bp, 0));
			server.waitFor(srv -> !ollama.requests.isEmpty(), 600);
			require(ollama.requests.get(0).contains("paused"), "the model was not woken with the pause message");

			server.runOnServer(srv -> VillagerInventories.get(s.helper()).addItem(new ItemStack(Items.COBBLESTONE, 5)));
			String resumed = tool(server, s.helper(), buildConfig(1000), "start_build", "{}", 100);
			require(resumed.startsWith("ok: building"), "resuming said: " + resumed);
			awaitBuildDone(server, s.helper(), 8000);
			int total = BuildOrder.order(bp).size();
			require(built(server, bp, 0) == total, "the resumed build is incomplete: " + built(server, bp, 0) + " of " + total);
			require(carriedOf(server, s.helper(), bp) == 0, "something was placed twice or wasted; " + carriedOf(server, s.helper(), bp) + " items are left");
			context.runOnClient(mc -> mc.gui.setScreen(null));
		} finally {
			deleteBlueprints(server, "hut4");
		}
	}

	private void blueprintBlockedCells(ClientGameTestContext context, TestServerContext server) throws Exception {
		Site s = blueprintSite(server, "hut5");
		try {
			Blueprint bp = savedBlueprint(server, "hut5");
			stock(server, s.helper(), bp, 0);
			server.runCommand("setblock 6 101 -10 minecraft:dirt");        // sits in the wall's first cell
			String plan = planAt(server, s.helper(), "hut5", 0);
			require(plan.contains("occupied"), "plan_build should mention the occupied spot: " + plan);
			CompletableFuture<String> started = toolAsync(server, s.helper(), buildConfig(1000), "start_build", "{}");
			context.waitForScreen(AskScreen.class);                       // occupied spots always ask the king first
			context.runOnClient(mc -> ((AskScreen) mc.gui.screen()).choose("Yes"));
			require(awaitResult(server, started, 200).startsWith("ok: building"), "the build did not start after a yes");
			openChat(context, s);
			awaitBuildDone(server, s.helper(), 8000);
			int total = BuildOrder.order(bp).size();
			require(server.computeOnServer(srv -> srv.overworld().getBlockState(new BlockPos(6, 101, -10)).is(Blocks.DIRT)), "the dirt block was replaced");
			require(built(server, bp, 0) == total - 1, "all other blocks should be built, found " + built(server, bp, 0) + " of " + total);
			require(chatText(context).contains("occupied"), "the helper did not report the occupied spot: " + chatText(context));
			context.runOnClient(mc -> mc.gui.setScreen(null));

			// Nothing is forced where protection says no: in adventure mode every spot is protected.
			resetArena(server);
			Villager again = smartVillager(server, "Alma", 0, 100, 0);
			stock(server, again, bp, 0);
			server.runCommand("gamemode adventure @p");
			try {
				planAt(server, again, "hut5", 0);
				require(tool(server, again, buildConfig(1000), "start_build", "{}", 100).startsWith("ok"), "start_build failed in adventure mode");
				awaitBuildDone(server, again, 3000);
				require(built(server, bp, 0) == 0, "blocks were placed where protection forbids it");
			} finally {
				server.runCommand("gamemode creative @p");
			}
		} finally {
			deleteBlueprints(server, "hut5");
		}
	}

	private void blueprintStopAndResume(ClientGameTestContext context, TestServerContext server) throws Exception {
		Site s = blueprintSite(server, "hut6");
		try {
			Blueprint bp = savedBlueprint(server, "hut6");
			stock(server, s.helper(), bp, 0);
			planAt(server, s.helper(), "hut6", 0);
			require(tool(server, s.helper(), buildConfig(1000), "start_build", "{}", 100).startsWith("ok"), "start_build failed");
			server.waitFor(srv -> builtNow(srv, bp, 0) >= 15, 4000);
			String stopped = tool(server, s.helper(), buildConfig(1000), "stop_build", "{}", 100);
			require(stopped.startsWith("ok") && stopped.contains("paused"), "stop_build said: " + stopped);
			BuildJob job = server.computeOnServer(srv -> BuildJobs.get(s.helper()).orElseThrow());
			require(job.status() == BuildJob.Status.PAUSED_BY_KING, "the job should be paused by the king, it is " + job.status());
			require(!server.computeOnServer(srv -> BuildRegistry.isBuilding(s.helper().getUUID())), "still counted as building after a stop");
			int atStop = built(server, bp, 0);
			require(atStop >= 15 && atStop < BuildOrder.order(bp).size(), "the stop came at " + atStop + " blocks");
			context.waitTicks(40);
			require(built(server, bp, 0) == atStop, "blocks kept appearing after the stop");

			// A world reload turns a running job into a paused one; carrying on works from either.
			server.runOnServer(srv -> BuildJobs.set(s.helper(), BuildJob.afterReload(BuildJobs.get(s.helper()).orElseThrow())));
			require(tool(server, s.helper(), buildConfig(1000), "start_build", "{}", 100).startsWith("ok: building"), "start_build did not carry on");
			awaitBuildDone(server, s.helper(), 8000);
			int total = BuildOrder.order(bp).size();
			require(built(server, bp, 0) == total, "the resumed build is incomplete: " + built(server, bp, 0) + " of " + total);
			require(carriedOf(server, s.helper(), bp) == 0, "blocks were placed twice; " + carriedOf(server, s.helper(), bp) + " items are left");
		} finally {
			deleteBlueprints(server, "hut6");
		}
	}

	private void blueprintAsksTheKing(ClientGameTestContext context, TestServerContext server) throws Exception {
		Site s = blueprintSite(server, "hut7");
		try {
			Blueprint bp = savedBlueprint(server, "hut7");
			stock(server, s.helper(), bp, 0);
			planAt(server, s.helper(), "hut7", 0);
			VillagingConfig asking = buildConfig(64);
			CompletableFuture<String> no = toolAsync(server, s.helper(), asking, "start_build", "{}");
			context.waitForScreen(AskScreen.class);
			context.takeScreenshot("villaging-build-ask");
			context.runOnClient(mc -> ((AskScreen) mc.gui.screen()).choose("No"));
			String refused = awaitResult(server, no, 200);
			require(refused.contains("not building"), "answering no should start nothing: " + refused);
			require(built(server, bp, 0) == 0 && !server.computeOnServer(srv -> BuildRegistry.isBuilding(s.helper().getUUID())), "a no still started the build");
			require(server.computeOnServer(srv -> BuildJobs.get(s.helper()).orElseThrow().status()) == BuildJob.Status.PLANNED, "the plan should be kept after a no");

			CompletableFuture<String> yes = toolAsync(server, s.helper(), asking, "start_build", "{}");
			context.waitForScreen(AskScreen.class);
			context.runOnClient(mc -> ((AskScreen) mc.gui.screen()).choose("Yes"));
			require(awaitResult(server, yes, 200).startsWith("ok: building"), "a yes should start the build");
			server.waitFor(srv -> builtNow(srv, bp, 0) >= 3, 2000);
			String cancelled = tool(server, s.helper(), asking, "cancel_build", "{}", 100);
			require(cancelled.startsWith("ok") && cancelled.contains("cancelled"), "cancel_build said: " + cancelled);
			require(server.computeOnServer(srv -> BuildJobs.get(s.helper()).isEmpty() && !BuildRegistry.isBuilding(s.helper().getUUID())), "the job survived cancel_build");
		} finally {
			deleteBlueprints(server, "hut7");
		}
	}

	private void blueprintRefusals(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = smartVillager(server, "Alma", 0, 100, 0);
		try {
			com.google.gson.JsonObject boom = new com.google.gson.JsonObject();
			boom.addProperty("name", "bad1");
			boom.addProperty("plan", "{\"size\":[1,1,1],\"palette\":{\"T\":\"minecraft:tnt\"},\"shapes\":[{\"type\":\"box\",\"x\":0,\"y\":0,\"z\":0,\"w\":1,\"h\":1,\"d\":1,\"block\":\"T\"}]}");
			String tnt = tool(server, v, buildConfig(1000), "design_blueprint", boom.toString(), 100);
			require(tnt.startsWith("failed") && tnt.contains("tnt"), "TNT should be refused by name: " + tnt);
			com.google.gson.JsonObject wide = new com.google.gson.JsonObject();
			wide.addProperty("name", "bad2");
			wide.addProperty("plan", "{\"size\":[5,5,5],\"palette\":{\"S\":\"minecraft:stone\"},\"shapes\":[{\"type\":\"box\",\"x\":0,\"y\":0,\"z\":0,\"w\":60,\"h\":1,\"d\":1,\"block\":\"S\"}]}");
			String tooWide = tool(server, v, buildConfig(1000), "design_blueprint", wide.toString(), 100);
			require(tooWide.startsWith("failed") && tooWide.contains("shape 1"), "an oversized shape should name itself: " + tooWide);
			String list = tool(server, v, buildConfig(1000), "list_blueprints", "{}", 50);
			require(!list.contains("bad1") && !list.contains("bad2"), "a refused plan must not be saved: " + list);
		} finally {
			deleteBlueprints(server, "bad1", "bad2");
		}
	}

	// --- team checks ---

	private static VillagingConfig teamConfig(int maxActiveBuilds) {
		VillagingConfig d = VillagingConfig.defaults();
		return new VillagingConfig(d.provider(), d.apiKey(), d.model(), d.cloudUrl(), d.ollamaUrl(), d.ollamaModel(), d.geminiModel(),
				d.geminiUrl(), d.maxSteps(), d.callsPerMinute(), d.actionRange(), d.respectProtection(), d.allowBreakBedrock(),
				d.maxScriptSteps(), d.maxScriptSeconds(), d.maxScriptDepth(), "", new BuildSettings(20_000, 50, 1, 1000, maxActiveBuilds));
	}

	private static boolean inTeam(TestServerContext server, PathfinderMob v) {
		return server.computeOnServer(srv -> com.villaging.team.Teams.teamOf(v).isPresent());
	}

	private void teamBasics(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager alma = smartVillager(server, "Alma", 3, 100, 0);
		Villager bram = smartVillager(server, "Bram", -3, 100, 0);
		Villager cleo = smartVillager(server, "Cleo", 0, 100, 5);
		String made = tool(server, alma, "create_team", "{\"name\":\"crew\",\"leader\":\"Alma\",\"members\":[\"Bram\"]}", 100);
		require(made.startsWith("ok") && made.contains("Leader Alma"), "create_team said: " + made);
		require(inTeam(server, alma) && inTeam(server, bram) && !inTeam(server, cleo), "the team should hold exactly Alma and Bram");
		String listed = tool(server, alma, "list_teams", "{}", 50);
		require(listed.contains("crew") && listed.contains("Alma") && listed.contains("Bram"), "list_teams said: " + listed);

		String twin = tool(server, alma, "create_team", "{\"name\":\"crew\",\"leader\":\"Cleo\",\"members\":[]}", 100);
		require(twin.startsWith("failed") && twin.contains("already exists"), "a second team with the same name: " + twin);
		String taken = tool(server, alma, "create_team", "{\"name\":\"other\",\"leader\":\"Cleo\",\"members\":[\"Bram\"]}", 100);
		require(taken.startsWith("failed") && taken.contains("already in team crew"), "a helper in two teams: " + taken);
		require(!inTeam(server, cleo), "a refused team must not change anyone");

		UUID stranger = UUID.randomUUID();
		String notKing = awaitResult(server, server.computeOnServer(srv -> {
			MinecraftActionExecutor exec = new MinecraftActionExecutor(srv, alma, stranger, VillagingConfig.defaults());
			return exec.execute(new ToolCall("t", "disband_team", com.google.gson.JsonParser.parseString("{\"team\":\"crew\"}").getAsJsonObject()));
		}), 100);
		require(notKing.startsWith("failed") && notKing.contains("only the king"), "someone who is not king changed a team: " + notKing);
		require(inTeam(server, alma), "the team must survive a stranger's disband");

		require(tool(server, alma, "add_to_team", "{\"team\":\"crew\",\"helper\":\"Cleo\"}", 100).startsWith("ok"), "add_to_team failed");
		require(inTeam(server, cleo), "Cleo should be in the team now");
		String leaderOut = tool(server, alma, "remove_from_team", "{\"team\":\"crew\",\"helper\":\"Alma\"}", 100);
		require(leaderOut.startsWith("failed") && leaderOut.contains("leads team"), "removing the leader: " + leaderOut);
		require(tool(server, alma, "remove_from_team", "{\"team\":\"crew\",\"helper\":\"Bram\"}", 100).startsWith("ok"), "remove_from_team failed");
		require(!inTeam(server, bram), "Bram should be out of the team");
		require(tool(server, alma, "disband_team", "{\"team\":\"crew\"}", 100).startsWith("ok"), "disband_team failed");
		require(!inTeam(server, alma) && !inTeam(server, cleo), "a disbanded team leaves nobody in it");
	}

	private void teamAssignReachesTheMember(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager alma = smartVillager(server, "Alma", 3, 100, 0);
		Villager bram = smartVillager(server, "Bram", -3, 100, 0);
		require(tool(server, alma, "create_team", "{\"name\":\"crew\",\"leader\":\"Alma\",\"members\":[\"Bram\"]}", 100).startsWith("ok"), "no team");
		String notLeader = tool(server, bram, "assign", "{\"member\":\"Alma\",\"task\":\"say hi\"}", 100);
		require(notLeader.startsWith("failed") && notLeader.contains("only a team leader"), "a member used assign: " + notLeader);
		ollama.script("{\"message\":{\"role\":\"assistant\",\"content\":\"On it, boss.\"}}");
		String passed = tool(server, alma, "assign", "{\"member\":\"Bram\",\"task\":\"say hello to the king\"}", 100);
		require(passed.startsWith("ok") && passed.contains("Bram"), "assign said: " + passed);
		server.waitFor(srv -> ollama.requests.size() >= 1, 600);
		String request = ollama.requests.get(0);
		require(request.contains("Your team leader Alma passes on this order from the king") && request.contains("say hello to the king"),
				"the member did not get the leader's order");
		require(request.contains("You are in team crew, led by Alma"), "the member's prompt should know its team");
		String status = tool(server, alma, "team_status", "{}", 100);
		require(status.startsWith("ok") && status.contains("Bram") && status.contains("Alma"), "team_status said: " + status);
	}

	private void teamLeaderDeathEndsTheTeam(TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager alma = smartVillager(server, "Alma", 3, 100, 0);
		Villager bram = smartVillager(server, "Bram", -3, 100, 0);
		Villager cleo = smartVillager(server, "Cleo", 0, 100, 5);
		require(tool(server, alma, "create_team", "{\"name\":\"crew\",\"leader\":\"Alma\",\"members\":[\"Bram\",\"Cleo\"]}", 100).startsWith("ok"), "no team");
		server.runCommand("kill @e[type=minecraft:villager,sort=nearest,limit=1,x=3,y=100,z=0]");
		server.waitFor(srv -> !inTeamNow(bram) && !inTeamNow(cleo), 200);
		require(!inTeam(server, bram) && !inTeam(server, cleo), "the members should be free when their leader dies");
	}

	private static boolean inTeamNow(PathfinderMob v) {
		return com.villaging.team.Teams.teamOf(v).isPresent();
	}

	/** Two helpers split a cottage; with {@code maxActive} 1 the second must wait for the first to finish. */
	private void teamBuild(ClientGameTestContext context, TestServerContext server, int maxActive) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager alma = smartVillager(server, "Alma", 0, 100, 0);
		Villager bram = smartVillager(server, "Bram", 2, 100, 0);
		VillagingConfig config = teamConfig(maxActive);
		try {
			String made = tool(server, alma, config, "use_template",
					"{\"name\":\"teamhut\",\"template\":\"cottage\",\"width\":5,\"depth\":5,\"height\":3,\"style\":\"stone\",\"roof\":\"flat\"}", 100);
			require(made.startsWith("ok"), "use_template said: " + made);
			require(tool(server, alma, config, "create_team", "{\"name\":\"crew\",\"leader\":\"Alma\",\"members\":[\"Bram\"]}", 100).startsWith("ok"), "no team");
			Blueprint bp = savedBlueprint(server, "teamhut");
			stock(server, alma, bp, 0);
			stock(server, bram, bp, 0);
			String plan = tool(server, alma, config, "plan_build",
					"{\"name\":\"teamhut\",\"x\":" + SITE.getX() + ",\"y\":" + SITE.getY() + ",\"z\":" + SITE.getZ() + ",\"rotation\":\"0\"}", 100);
			require(plan.startsWith("ok"), "plan_build said: " + plan);
			String result = tool(server, alma, config, "assign_build", "{\"blueprint\":\"teamhut\"}", 100);
			require(result.startsWith("ok"), "assign_build said: " + result);
			if (maxActive == 1) {
				require(result.contains("1 helpers are building") && result.contains("1 are waiting"), "one slice should wait: " + result);
			} else {
				require(result.contains("2 helpers are building"), "both should start: " + result);
			}
			require(server.computeOnServer(srv -> BuildJobs.get(bram).map(j -> j.blueprint().startsWith("tcrew_")).orElse(false)),
					"Bram should be building his own slice");
			server.waitFor(srv -> BuildJobs.get(alma).isEmpty() && BuildJobs.get(bram).isEmpty()
					&& !BuildRegistry.isBuilding(alma.getUUID()) && !BuildRegistry.isBuilding(bram.getUUID()), 16000);
			int total = BuildOrder.order(bp).size();
			int builtCount = built(server, bp, 0);
			require(builtCount == total, "only " + builtCount + " of " + total + " blocks were built");
			require(tool(server, alma, config, "disband_team", "{\"team\":\"crew\"}", 100).startsWith("ok"), "disband failed");
			require(server.computeOnServer(srv -> BlueprintStore.names(BlueprintFiles.dir(srv)).stream().noneMatch(n -> n.startsWith("tcrew_"))),
					"the saved slices should be deleted with the team");
		} finally {
			deleteBlueprints(server, "teamhut");
		}
	}

	private static Villager smartVillager(TestServerContext server, String name, double x, double y, double z) {
		Villager v = spawnVillager(server, x, y, z);
		server.runOnServer(srv -> VillagerBrains.set(v, VillagerBrain.DEFAULT.makeSmart(name)));
		return v;
	}

	private void announcements(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		smartVillager(server, "Alma", 6, 100, 0);
		smartVillager(server, "Bob", -6, 100, 0);
		smartVillager(server, "Cleo", 0, 100, 8);
		ollama.script(
				"{\"message\":{\"role\":\"assistant\",\"content\":\"Heard, my king.\"}}",
				"{\"message\":{\"role\":\"assistant\",\"content\":\"Heard, my king.\"}}",
				"{\"message\":{\"role\":\"assistant\",\"content\":\"Heard, my king.\"}}");
		server.runCommand("execute as @p run announcements gather at the well");
		server.waitFor(srv -> ollama.requests.size() >= 3, 600);
		require(ollama.requests.size() == 3, "all three helpers should hear it, saw " + ollama.requests.size() + " requests");
		String all = String.join("\n", ollama.requests);
		for (String request : ollama.requests) {
			require(request.contains("gather at the well") && request.contains("speaks to all helpers"),
					"a helper did not get the announcement text");
		}
		require(all.contains("Alma") && all.contains("Bob") && all.contains("Cleo"), "every helper should be asked in its own name");

		// Only the king may announce.
		noKing(server);
		ollama.script("{\"message\":{\"role\":\"assistant\",\"content\":\"Should not happen.\"}}");
		server.runCommand("execute as @p run announcements hello");
		context.waitTicks(90);
		require(ollama.requests.isEmpty(), "a player who is not king must not be able to announce, saw " + ollama.requests.size());
	}

	private void summonByName(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager alma = smartVillager(server, "Alma", 12, 100, 12);
		Villager bob = smartVillager(server, "Bob", -12, 100, -6);
		Villager bobbi = smartVillager(server, "Bobbi", -12, 100, 12);
		require(distance(server, alma, 0, 100, 2) > 10, "Alma should start far away");

		// Two helpers start with "bo": nobody is moved and the king is asked which one.
		server.runCommand("execute as @p run villaging summon bo");
		context.waitTicks(10);
		require(distance(server, bob, 0, 100, 2) > 10 && distance(server, bobbi, 0, 100, 2) > 10, "an ambiguous name must not move anyone");

		// An unknown name moves nobody.
		server.runCommand("execute as @p run villaging summon Zed");
		context.waitTicks(10);
		require(distance(server, alma, 0, 100, 2) > 10, "an unknown name must not move anyone");

		// A full name picks one even though it also starts another name.
		server.runCommand("execute as @p run villaging summon bob");
		server.waitFor(srv -> bob.position().distanceTo(new Vec3(0, 100, 2)) < 4, 60);
		require(distance(server, bobbi, 0, 100, 2) > 10, "summoning Bob must leave Bobbi where she is");

		// A start of a name that fits one helper is enough, and it works while the AI is unusable.
		server.runCommand("execute as @p run villaging summon al");
		server.waitFor(srv -> alma.position().distanceTo(new Vec3(0, 100, 2)) < 4, 60);

		// Only the king may summon.
		noKing(server);
		server.runCommand("execute as @p run villaging summon bobbi");
		context.waitTicks(10);
		require(distance(server, bobbi, 0, 100, 2) > 10, "a player who is not king must not be able to summon");
	}

	private void chatSelectCopyPaste(ClientGameTestContext context, TestServerContext server) throws Exception {
		List<String> history = List.of("king: please walk to the spot six blocks east", "me: On my way, my king!");
		context.runOnClient(mc -> mc.gui.setScreen(new VillagerChatScreen(1, "Alma", history)));
		context.waitForScreen(VillagerChatScreen.class);
		context.waitTick();

		// Real mouse handlers: press on one character, drag to another, release.
		double[] from = context.computeOnClient(mc -> ((VillagerChatScreen) mc.gui.screen()).pointAt(0, 5));
		double[] to = context.computeOnClient(mc -> ((VillagerChatScreen) mc.gui.screen()).pointAt(0, 11));
		context.runOnClient(mc -> {
			VillagerChatScreen chat = (VillagerChatScreen) mc.gui.screen();
			chat.mouseClicked(new MouseButtonEvent(from[0], from[1], new MouseButtonInfo(0, 0)), false);
			chat.mouseDragged(new MouseButtonEvent(to[0], to[1], new MouseButtonInfo(0, 0)), 0, 0);
			chat.mouseReleased(new MouseButtonEvent(to[0], to[1], new MouseButtonInfo(0, 0)));
		});
		String selected = context.computeOnClient(mc -> ((VillagerChatScreen) mc.gui.screen()).selectedText());
		require(selected.equals("please"), "dragging should select 'please', got '" + selected + "'");
		context.takeScreenshot("villaging-chat-selection");

		// Ctrl+C copies the selection and Ctrl+V pastes it into the box. The key events are sent straight to the
		// screen because the test input does not deliver the Ctrl modifier; the screen sees the same event from a keyboard.
		context.runOnClient(mc -> mc.keyboardHandler.setClipboard("untouched"));
		// The system clipboard belongs to the operating system; if another program is holding it the game cannot write or read it.
		boolean clipboardUsable = context.computeOnClient(mc -> mc.keyboardHandler.getClipboard()).equals("untouched");
		boolean copied = context.computeOnClient(mc -> ((VillagerChatScreen) mc.gui.screen())
				.keyPressed(new KeyEvent(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL)));
		require(copied, "Ctrl+C should be handled when something is selected");
		if (!clipboardUsable) {
			System.out.println("[villaging-gametest] note: the system clipboard cannot be read back here, so the copy and paste read-backs are skipped");
			context.runOnClient(mc -> mc.gui.setScreen(null));
			return;
		}
		String clipboard = context.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
		require(clipboard.equals("please"), "Ctrl+C should copy the selection, clipboard has '" + clipboard + "'");
		context.runOnClient(mc -> ((VillagerChatScreen) mc.gui.screen())
				.keyPressed(new KeyEvent(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL)));
		String typed = context.computeOnClient(mc -> ((VillagerChatScreen) mc.gui.screen()).typed());
		require(typed.equals("please"), "Ctrl+V should paste into the box, it holds '" + typed + "'");

		// Right-click copies the whole message under the pointer.
		double[] reply = context.computeOnClient(mc -> ((VillagerChatScreen) mc.gui.screen()).pointAt(1, 3));
		context.runOnClient(mc -> ((VillagerChatScreen) mc.gui.screen())
				.mouseClicked(new MouseButtonEvent(reply[0], reply[1], new MouseButtonInfo(1, 0)), false));
		String whole = context.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
		require(whole.equals("Alma: On my way, my king!"), "right-click should copy the whole message, got '" + whole + "'");

		// A new message from the villager clears the highlight.
		context.runOnClient(mc -> ((VillagerChatScreen) mc.gui.screen()).addVillagerLine("Done."));
		String after = context.computeOnClient(mc -> ((VillagerChatScreen) mc.gui.screen()).selectedText());
		require(after.isEmpty(), "a new line should clear the selection, still has '" + after + "'");
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	/** Opt-in (VILLAGING_REAL_MODEL=<ollama model>): sends varied phrasings to a real model and logs what happened. */
	private void realModel(ClientGameTestContext context, TestServerContext server) throws Exception {
		String[] phrases = {
				"yo come with me",
				"folow me plz",
				"how are you doing?",
				"what can you do?",
				"mine that block",
				"go stand over there",
				"build me a castle",
				"mine something",
				"viens avec moi",
		};
		String only = System.getenv("VILLAGING_REAL_ONLY");
		for (String phrase : phrases) {
			if (only != null && !phrase.contains(only)) {
				continue;
			}
			resetArena(server);
			makeKing(server);
			server.runCommand("setblock 0 101 6 minecraft:stone");
			server.runCommand("tp @p 0 100 2 0 2");
			Villager v = spawnVillager(server, 1, 100, 2.5);
			server.runOnServer(srv -> VillagerBrains.set(v, VillagerBrain.DEFAULT.makeSmart("Alma")));
			int id = server.computeOnServer(srv -> v.getId());
			int before = server.computeOnServer(srv -> VillagerBrains.get(v).memory().size());
			context.waitTicks(10);
			context.runOnClient(mc -> ClientPlayNetworking.send(new CommandPayload(id, phrase)));
			try {
				server.waitFor(srv -> VillagerBrains.get(v).memory().size() > before + 1, 3000);
				context.waitTicks(100);
			} catch (AssertionError timeout) {
				System.out.println("[villaging-real] TIMEOUT waiting for the model on: " + phrase);
			}
			String summary = server.computeOnServer(srv -> {
				VillagerBrain b = VillagerBrains.get(v);
				ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
				return "dbg[rot=" + p.getXRot() + "/" + p.getYRot() + " eye=" + p.getEyePosition() + " hit=" + p.pick(20.0, 1.0f, false) + " type=" + p.pick(20.0, 1.0f, false).getType() + "] kingContext=[" + com.villaging.king.KingContextReader.read(p) + "] reply=" + b.memory().stream().filter(l -> l.startsWith("me: ")).toList()
						+ " | controlled=" + ActionManager.isControlled(v)
						+ " | distToKing=" + String.format("%.1f", v.distanceTo(p))
						+ " | stoneStillThere=" + srv.overworld().getBlockState(new BlockPos(0, 101, 6)).is(Blocks.STONE);
			});
			System.out.println("[villaging-real] \"" + phrase + "\" -> " + summary);
		}
	}

	private static void noKing(TestServerContext server) {
		server.runOnServer(srv -> srv.overworld().setAttached(Kings.ATTACHMENT, KingData.EMPTY));
	}

	private void speakingToAnOrdinaryVillager(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		noKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		int id = server.computeOnServer(srv -> v.getId());
		ollama.script(reply("Gladly, my king."));
		context.runOnClient(mc -> ClientPlayNetworking.send(new CommandPayload(id, "hello there, be my helper")));
		server.waitFor(srv -> VillagerBrains.get(v).smart() && VillagerBrains.get(v).memory().size() >= 2, 600);
		require(server.computeOnServer(srv -> Kings.isKing(srv, playerId(server))), "the first speaker did not become king");
		require(!server.computeOnServer(srv -> VillagerBrains.get(v).name().isEmpty()), "the new smart villager has no name");
		require(ollama.requests.size() == 1 && ollama.requests.get(0).contains("be my helper"), "the model never got the message");

		// With another king in place, an ordinary villager stays ordinary.
		resetArena(server);
		noKing(server);
		server.runOnServer(srv -> Kings.claim(srv, UUID.randomUUID()));
		Villager other = spawnVillager(server, 0, 100, 0);
		int otherId = server.computeOnServer(srv -> other.getId());
		int before = ollama.requests.size();
		context.runOnClient(mc -> ClientPlayNetworking.send(new CommandPayload(otherId, "hello")));
		context.waitTicks(60);
		require(!server.computeOnServer(srv -> VillagerBrains.get(other).smart()), "a non-king made a villager smart");
		require(ollama.requests.size() == before, "a non-king reached the model");
	}

	private void sneakClickOnAnOrdinaryVillager(ClientGameTestContext context, TestServerContext server) {
		resetArena(server);
		noKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		InteractionResult sneaking = server.computeOnServer(srv -> {
			ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
			p.setShiftKeyDown(true);
			InteractionResult r = UseEntityCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, v, null);
			p.setShiftKeyDown(false);
			return r;
		});
		require(sneaking == InteractionResult.SUCCESS_SERVER, "sneak-click on an ordinary villager was not handled: " + sneaking);
		context.waitForScreen(VillagerChatScreen.class);
		context.runOnClient(mc -> mc.gui.setScreen(null));
		// Someone else is king: vanilla behaviour stays.
		server.runOnServer(srv -> Kings.claim(srv, UUID.randomUUID()));
		InteractionResult other = server.computeOnServer(srv -> {
			ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
			p.setShiftKeyDown(true);
			InteractionResult r = UseEntityCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, v, null);
			p.setShiftKeyDown(false);
			return r;
		});
		require(other == InteractionResult.PASS, "a non-king sneak-click should stay vanilla, got " + other);
	}

	private void renameAndKingTitle(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager alma = spawnVillager(server, 0, 100, 0);
		Villager bram = spawnVillager(server, 3, 100, 0);
		server.runOnServer(srv -> {
			VillagerBrains.set(alma, VillagerBrain.DEFAULT.makeSmart("Alma"));
			VillagerBrains.set(bram, VillagerBrain.DEFAULT.makeSmart("Bram"));
		});
		require(tool(server, alma, "rename", "{\"new_name\":\"Garrick\"}", 50).startsWith("ok"), "self rename failed");
		require(server.computeOnServer(srv -> VillagerBrains.get(alma).name().equals("Garrick")), "the brain kept the old name");
		require(server.computeOnServer(srv -> alma.getCustomName().getString().equals("Garrick")), "the name tag kept the old name");
		require(tool(server, alma, "rename", "{\"new_name\":\"Odo\",\"villager_name\":\"Bram\"}", 50).startsWith("ok"), "renaming another failed");
		require(server.computeOnServer(srv -> VillagerBrains.get(bram).name().equals("Odo")), "Bram was not renamed");
		require(tool(server, alma, "rename", "{\"new_name\":\"odo\"}", 50).contains("already called"), "a duplicate name was accepted");
		require(tool(server, alma, "rename", "{\"new_name\":\"X\",\"villager_name\":\"Nobody\"}", 50).contains("can't find"), "an unknown villager was renamed");

		require(tool(server, alma, "set_king_title", "{\"title\":\"Boss\",\"scope\":\"everyone\"}", 50).startsWith("ok"), "world title failed");
		require(server.computeOnServer(srv -> Kings.title(srv).equals("Boss")), "the world title was not saved");
		ollama.script(reply("Yes, Boss."));
		int id = server.computeOnServer(srv -> alma.getId());
		context.runOnClient(mc -> ClientPlayNetworking.send(new CommandPayload(id, "hi")));
		server.waitFor(srv -> !ollama.requests.isEmpty(), 600);
		require(ollama.requests.get(ollama.requests.size() - 1).contains("call the king \\\"Boss\\\""), "the prompt does not use the new title");

		require(tool(server, alma, "set_king_title", "{\"title\":\"Sire\",\"scope\":\"me\"}", 50).startsWith("ok"), "own title failed");
		require(server.computeOnServer(srv -> VillagerBrains.get(alma).kingTitle().equals("Sire")), "the own title was not saved");
		require(server.computeOnServer(srv -> VillagerBrains.get(bram).kingTitle().isEmpty()), "another villager's title changed");
		server.runOnServer(srv -> Kings.setTitle(srv, "King"));
	}

	private void recruiting(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager boss = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> VillagerBrains.set(boss, VillagerBrain.DEFAULT.makeSmart("Alma")));
		Villager a = spawnVillager(server, 5, 100, 0);
		Villager b = spawnVillager(server, 10, 100, 0);
		Villager c = spawnVillager(server, 15, 100, 0);
		for (Villager v : List.of(boss, a, b, c)) {
			server.runOnServer(srv -> v.setNoAi(true));
		}
		CompletableFuture<String> refused = toolAsync(server, boss, "recruit", "{\"count\":2}");
		answerDialog(context, "No");
		require(awaitResult(server, refused, 100).contains("the king said no"), "a no was not reported");
		require(server.computeOnServer(srv -> !VillagerBrains.get(a).smart() && !VillagerBrains.get(b).smart()), "villagers were recruited after a no");
		CompletableFuture<String> approved = toolAsync(server, boss, "recruit", "{\"count\":2}");
		answerDialog(context, "Yes");
		String result = awaitResult(server, approved, 100);
		require(result.startsWith("ok: recruited"), "recruit said " + result);
		int smart = server.computeOnServer(srv -> (int) List.of(a, b, c).stream().filter(v -> VillagerBrains.get(v).smart()).count());
		require(smart == 2, "expected exactly 2 recruits, got " + smart);
		require(server.computeOnServer(srv -> VillagerBrains.get(a).smart() && VillagerBrains.get(b).smart() && !VillagerBrains.get(c).smart()),
				"the two nearest villagers should be the recruits");
		require(server.computeOnServer(srv -> !VillagerBrains.get(a).name().equalsIgnoreCase(VillagerBrains.get(b).name())), "recruits share a name");
	}

	private void talkingHoldsTheVillagerStill(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 5, 100, 0);
		server.runOnServer(srv -> VillagerBrains.set(v, VillagerBrain.DEFAULT.makeSmart("Alma")
				.withMemory("king: hi").withMemory("me: Greetings, King.")));
		// A plain right-click opens the full chat, with the saved conversation, and the villager stops.
		InteractionResult clicked = server.computeOnServer(srv -> {
			ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
			p.setShiftKeyDown(false);
			return UseEntityCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, v, null);
		});
		require(clicked == InteractionResult.SUCCESS_SERVER, "the click did not open the chat: " + clicked);
		context.waitForScreen(VillagerChatScreen.class);
		List<String> shown = context.computeOnClient(mc -> ((VillagerChatScreen) mc.gui.screen()).transcript());
		require(shown.equals(List.of("You: hi", "Alma: Greetings, King.")), "the history is not shown: " + shown);
		require(server.computeOnServer(srv -> TalkHold.holds(v) && ActionManager.pausesVanillaAi(v)), "the villager is not held");
		// Something tries to send it walking (like its own wandering would); the hold stops it.
		Vec3 start = server.computeOnServer(srv -> {
			v.getNavigation().moveTo(14.0, 100.0, 0.0, 1.0);
			return v.position();
		});
		waitTicks(server, 80);
		double moved = server.computeOnServer(srv -> v.position().distanceTo(start));
		require(moved < 1.0, "the villager walked away while being talked to: " + moved);
		require(server.computeOnServer(srv -> v.getNavigation().isDone()), "the villager is still trying to walk");
		// Closing the chat lets it go again.
		context.runOnClient(mc -> mc.gui.setScreen(null));
		server.waitFor(srv -> !TalkHold.holds(v), 100);
		require(!server.computeOnServer(srv -> TalkHold.holds(v)), "the hold was not released");
	}

	private void aHoldNeverInterruptsAnOrder(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		makeKing(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> VillagerBrains.set(v, VillagerBrain.DEFAULT.makeSmart("Alma")));
		server.runOnServer(srv -> TalkHold.start(v, playerId(server)));
		String result = awaitResult(server, startAction(server, v, new GoToAction(8, 100, 0.5)), 900);
		require(result.equals("ok: arrived"), "an order was blocked by the chat hold: " + result);
		server.runOnServer(srv -> TalkHold.release(v));
	}

	private void crownIsInTheCreativeInventory(ClientGameTestContext context) {
		boolean found = context.computeOnClient(mc -> {
			net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(mc.level.enabledFeatures(), true, mc.level.registryAccess());
			return net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.stream()
					.anyMatch(tab -> tab.getDisplayItems().stream().anyMatch(s -> s.is(ModItems.CROWN)));
		});
		require(found, "the crown is not in the creative inventory");
	}

	private void guideFirstRun(ClientGameTestContext context, TestServerContext server) throws Exception {
		context.waitForScreen(com.villaging.client.GuideScreen.class);
		context.takeScreenshot("villaging-guide-first");
		int pages = com.villaging.guide.GuideContent.pages().size();
		context.runOnClient(mc -> ((com.villaging.client.GuideScreen) mc.gui.screen()).goToPage(pages - 1));
		context.takeScreenshot("villaging-guide-last");
		context.runOnClient(mc -> mc.gui.screen().onClose());
		context.waitFor(mc -> mc.gui.screen() == null);
		java.nio.file.Path flag = FabricLoader.getInstance().getConfigDir().resolve("villaging-client.json");
		require(Files.exists(flag) && Files.readString(flag).contains("true"), "the guide was not remembered");
		// It does not come back by itself, but the command opens it again.
		server.runCommand("execute as @p run villaging guide");
		context.waitForScreen(com.villaging.client.GuideScreen.class);
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	private static PathfinderMob spawnMob(TestServerContext server, String type, double x, double y, double z) {
		server.runCommand("summon minecraft:" + type + " " + x + " " + y + " " + z);
		return server.computeOnServer(srv -> {
			List<PathfinderMob> found = srv.overworld().getEntitiesOfClass(PathfinderMob.class,
					new AABB(x - 1, y - 1, z - 1, x + 1, y + 2, z + 1), m -> m.isAlive() && !m.isRemoved()
							&& m.getType().toString().contains(type));
			require(!found.isEmpty(), type + " did not spawn");
			return found.get(0);
		});
	}

	private static void clearGolems(TestServerContext server) {
		server.runCommand("kill @e[type=minecraft:iron_golem]");
		server.runCommand("kill @e[type=minecraft:copper_golem]");
		server.waitFor(srv -> srv.overworld().getEntitiesOfClass(PathfinderMob.class, new AABB(-16, 90, -16, 16, 116, 16),
				m -> m.getType().toString().contains("golem")).isEmpty(), 100);
	}

	private void recruitingGolems(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		clearGolems(server);
		makeKing(server);
		Villager boss = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> {
			VillagerBrains.set(boss, VillagerBrain.DEFAULT.makeSmart("Alma"));
			boss.setNoAi(true);
		});
		Villager plain = spawnVillager(server, 5, 100, 0);
		PathfinderMob iron = spawnMob(server, "iron_golem", 10, 100, 0);
		PathfinderMob copper = spawnMob(server, "copper_golem", 15, 100, 0);
		server.runOnServer(srv -> {
			plain.setNoAi(true);
			iron.setNoAi(true);
			copper.setNoAi(true);
		});
		// Only golems of the asked kind are recruited, however close the villager is.
		CompletableFuture<String> ask = toolAsync(server, boss, "recruit", "{\"kind\":\"iron_golem\"}");
		answerDialog(context, "Yes");
		String result = awaitResult(server, ask, 100);
		require(result.startsWith("ok: recruited") && result.contains("iron golem"), "recruit said " + result);
		require(server.computeOnServer(srv -> VillagerBrains.get(iron).smart() && !VillagerBrains.get(iron).name().isEmpty()), "the iron golem is not smart");
		require(server.computeOnServer(srv -> !VillagerBrains.get(plain).smart() && !VillagerBrains.get(copper).smart()), "someone else was recruited");
		require(server.computeOnServer(srv -> iron.getCustomName() != null), "the golem shows no name");
		CompletableFuture<String> ask2 = toolAsync(server, boss, "recruit", "{\"kind\":\"copper_golem\"}");
		answerDialog(context, "Yes");
		require(awaitResult(server, ask2, 100).contains("copper golem"), "the copper golem was not recruited");
		require(server.computeOnServer(srv -> VillagerBrains.get(copper).smart()), "the copper golem is not smart");
		String nobody = tool(server, boss, "recruit", "{\"kind\":\"iron_golem\"}", 50);
		require(nobody.startsWith("failed"), "recruiting with no golem left said " + nobody);
	}

	private void smartGolemsObey(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		clearGolems(server);
		makeKing(server);
		for (String type : List.of("iron_golem", "copper_golem")) {
			PathfinderMob golem = spawnMob(server, type, 0, 100, 0);
			server.runOnServer(srv -> VillagerBrains.set(golem, VillagerBrain.DEFAULT.makeSmart("Rex")));
			int id = server.computeOnServer(srv -> golem.getId());
			ollama.script(toolCall("go_to", "{\"x\":8,\"y\":100,\"z\":0.5}"), reply("Here, " + type + "."));
			int before = ollama.requests.size();
			context.runOnClient(mc -> ClientPlayNetworking.send(new CommandPayload(id, "walk east")));
			server.waitFor(srv -> golem.position().distanceTo(new Vec3(8, 100, 0.5)) <= 3.5, 1500);
			require(distance(server, golem, 8, 100, 0.5) <= 3.5, type + " did not reach the target");
			server.waitFor(srv -> ollama.requests.size() >= before + 2, 600);
			String first = ollama.requests.get(before);
			require(first.contains("a smart " + type.replace('_', ' ')), type + " was not told what it is");
			clearGolems(server);
		}
	}

	private void golemClicks(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		clearGolems(server);
		makeKing(server);
		PathfinderMob golem = spawnMob(server, "iron_golem", 0, 100, 0);
		InteractionResult ordinary = server.computeOnServer(srv -> {
			ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
			p.setShiftKeyDown(true);
			InteractionResult r = UseEntityCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, golem, null);
			p.setShiftKeyDown(false);
			return r;
		});
		require(ordinary == InteractionResult.PASS, "an ordinary golem should stay vanilla, got " + ordinary);
		server.runOnServer(srv -> VillagerBrains.set(golem, VillagerBrain.DEFAULT.makeSmart("Rex")));
		InteractionResult smart = server.computeOnServer(srv -> {
			ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
			p.setShiftKeyDown(false);
			InteractionResult r = UseEntityCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, golem, null);
			p.setShiftKeyDown(false);
			return r;
		});
		require(smart == InteractionResult.SUCCESS_SERVER, "a plain click on a smart golem was not handled: " + smart);
		context.waitForScreen(VillagerChatScreen.class);
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	/** The key box: /villaging key opens it, typing and Enter save the key and the AI starts using it. */
	private void keyBox(ClientGameTestContext context, TestServerContext server) throws Exception {
		VillagingConfig original = server.computeOnServer(srv -> com.villaging.Villaging.runtime().config());
		try {
			server.runCommand("execute as @p run villaging key");
			context.waitForScreen(com.villaging.client.KeyScreen.class);
			context.takeScreenshot("villaging-key-box");
			context.runOnClient(mc -> ((com.villaging.client.KeyScreen) mc.gui.screen()).chooseProvider("gemini"));
			context.getInput().typeChars("test-key-123");
			context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
			server.waitFor(srv -> "test-key-123".equals(com.villaging.Villaging.runtime().config().apiKey()), 200);
			VillagingConfig saved = VillagingConfig.load(com.villaging.Villaging.configFile());
			require(saved.apiKey().equals("test-key-123"), "the key was not written to the config file");
			require(!saved.provider().equals("ollama"), "the provider was not switched to a keyed one: " + saved.provider());
		} finally {
			server.runOnServer(srv -> {
				try {
					original.save(com.villaging.Villaging.configFile());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				com.villaging.Villaging.replaceRuntime(new com.villaging.command.VillagingRuntime(srv, original));
			});
			context.runOnClient(mc -> mc.gui.setScreen(null));
		}
	}

	private void forgedPacket(ClientGameTestContext context, TestServerContext server) throws Exception {
		resetArena(server);
		Villager v = spawnVillager(server, 0, 100, 0);
		server.runOnServer(srv -> {
			VillagerBrains.set(v, VillagerBrain.DEFAULT.makeSmart("Alma"));
			srv.overworld().setAttached(Kings.ATTACHMENT, KingData.EMPTY);
			Kings.claim(srv, UUID.randomUUID());
		});
		int id = server.computeOnServer(srv -> v.getId());
		ollama.script("{\"message\":{\"content\":\"I should never be asked.\"}}");
		context.runOnClient(mc -> ClientPlayNetworking.send(new CommandPayload(id, "obey me")));
		context.waitTicks(60);
		require(ollama.requests.isEmpty(), "a non-king command reached the model");
	}
}
