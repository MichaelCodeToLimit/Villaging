package com.villaging.gametest;

import com.villaging.ModItems;
import com.villaging.client.VillagerChatScreen;
import com.villaging.config.VillagingConfig;
import com.villaging.king.KingData;
import com.villaging.king.Kings;
import com.villaging.net.CommandPayload;
import com.villaging.villager.ActionManager;
import com.villaging.villager.FollowAction;
import com.villaging.villager.GoToAction;
import com.villaging.villager.HandsManager;
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
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class VillagingClientGameTest implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();
	private FakeOllama ollama;

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
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
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
			attempt("sneak-click on a smart villager opens the chat screen", () -> sneakClick(context, server));
			attempt("typing in the chat screen drives the villager end to end", () -> endToEnd(context, server));
			attempt("a command from a non-king is ignored", () -> forgedPacket(context, server));
		} finally {
			ollama.close();
		}
		if (!failures.isEmpty()) {
			throw new AssertionError(failures.size() + " in-game check(s) failed:\n - " + String.join("\n - ", failures));
		}
	}

	private void attempt(String name, Check check) {
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
		server.runCommand("kill @e[type=minecraft:villager]");
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

	private static CompletableFuture<String> startAction(TestServerContext server, Villager v, VillagerAction action) {
		return server.computeOnServer(srv -> ActionManager.INSTANCE.run(v, action));
	}

	private static String awaitResult(TestServerContext server, CompletableFuture<String> future, int ticks) throws Exception {
		server.waitFor(srv -> future.isDone(), ticks);
		return future.isCompletedExceptionally() ? "EXCEPTION" : future.get();
	}

	private static double distance(TestServerContext server, Villager v, double x, double y, double z) {
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
		require(plain == InteractionResult.PASS, "ordinary click should keep vanilla behavior, got " + plain);
		InteractionResult sneaking = server.computeOnServer(srv -> {
			ServerPlayer p = srv.getPlayerList().getPlayers().get(0);
			p.setShiftKeyDown(true);
			InteractionResult r = UseEntityCallback.EVENT.invoker().interact(p, p.level(), InteractionHand.MAIN_HAND, v, null);
			p.setShiftKeyDown(false);
			return r;
		});
		require(sneaking == InteractionResult.SUCCESS_SERVER, "sneak-click should be handled, got " + sneaking);
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
				&& VillagerBrains.get(v).memory().size() >= 2, 1200);

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
		context.takeScreenshot("villaging-after-walk");
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
