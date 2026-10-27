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
			attempt("container tools look in, fill and empty a chest", () -> containerTools(server));
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
	private static String tool(TestServerContext server, Villager v, String name, String argsJson, int ticks) throws Exception {
		return tool(server, v, VillagingConfig.defaults(), name, argsJson, ticks);
	}

	private static String tool(TestServerContext server, Villager v, VillagingConfig config, String name, String argsJson,
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
	private static CompletableFuture<String> toolAsync(TestServerContext server, Villager v, String name, String argsJson) {
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
