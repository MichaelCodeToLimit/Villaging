package com.villaging.ai.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.villaging.ai.SmartKind;
import com.villaging.ai.SystemPrompt;
import com.villaging.ai.ToolCall;
import com.villaging.ai.Tools;
import com.villaging.guide.GuideContent;
import com.villaging.villager.VillagerBrain;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BuildToolsTest {
	private static final List<String> NEW_TOOLS = List.of("design_blueprint", "use_template", "list_blueprints", "show_blueprint",
			"plan_build", "start_build", "build_status", "stop_build", "cancel_build");

	@BeforeAll
	static void register() {
		AbilityTools.registerAll();
	}

	private static Optional<String> validate(String name, String json) {
		JsonObject args = JsonParser.parseString(json).getAsJsonObject();
		return Tools.validate(new ToolCall("1", name, args), 64, 0, 0, 0);
	}

	@Test
	void everyNewToolIsRegistered() {
		Set<String> names = Tools.all().stream().map(t -> t.name()).collect(Collectors.toSet());
		assertTrue(names.containsAll(NEW_TOOLS), "missing: " + NEW_TOOLS.stream().filter(n -> !names.contains(n)).toList());
	}

	@Test
	void designBlueprintNeedsANameAndAPlanString() {
		assertEquals(Optional.empty(), validate("design_blueprint", "{\"name\":\"hut\",\"plan\":\"{}\"}"));
		assertTrue(validate("design_blueprint", "{\"name\":\"Bad Name\",\"plan\":\"{}\"}").isPresent());
		assertTrue(validate("design_blueprint", "{\"name\":\"hut\"}").isPresent());
		assertTrue(validate("design_blueprint", "{\"name\":\"hut\",\"plan\":\"not json\"}").isPresent());
		assertTrue(validate("design_blueprint", "{\"name\":\"hut\",\"plan\":\"[1,2]\"}").isPresent());
		assertTrue(validate("design_blueprint", "{\"name\":\"hut\",\"plan\":\"" + "x".repeat(20001) + "\"}").isPresent());
	}

	@Test
	void useTemplateOnlyAcceptsKnownTemplatesStylesAndRoofs() {
		assertEquals(Optional.empty(), validate("use_template", "{\"name\":\"home\",\"template\":\"cottage\"}"));
		assertEquals(Optional.empty(), validate("use_template",
				"{\"name\":\"home\",\"template\":\"cottage\",\"width\":9,\"depth\":8,\"height\":4,\"style\":\"wood\",\"roof\":\"flat\"}"));
		assertTrue(validate("use_template", "{\"name\":\"home\",\"template\":\"igloo\"}").isPresent());
		assertTrue(validate("use_template", "{\"name\":\"home\",\"template\":\"cottage\",\"style\":\"pink\"}").isPresent());
		assertTrue(validate("use_template", "{\"name\":\"home\",\"template\":\"cottage\",\"roof\":\"spire\"}").isPresent());
		assertTrue(validate("use_template", "{\"name\":\"home\",\"template\":\"cottage\",\"width\":0}").isPresent());
		assertTrue(validate("use_template", "{\"name\":\"home\",\"template\":\"cottage\",\"width\":100}").isPresent());
		assertTrue(validate("use_template", "{\"template\":\"cottage\"}").isPresent());
	}

	@Test
	void planBuildWantsWholeCoordinatesNearTheKingAndAKnownRotation() {
		assertEquals(Optional.empty(), validate("plan_build", "{\"name\":\"home\",\"x\":5,\"y\":10,\"z\":-3}"));
		assertEquals(Optional.empty(), validate("plan_build", "{\"name\":\"home\",\"x\":5,\"y\":10,\"z\":-3,\"rotation\":\"90\"}"));
		assertEquals(Optional.empty(), validate("plan_build", "{\"name\":\"home\",\"x\":5,\"y\":10,\"z\":-3,\"rotation\":180}"));
		assertTrue(validate("plan_build", "{\"name\":\"home\",\"x\":5,\"y\":10,\"z\":-3,\"rotation\":45}").isPresent());
		assertTrue(validate("plan_build", "{\"name\":\"home\",\"x\":5.5,\"y\":10,\"z\":-3}").isPresent());
		assertTrue(validate("plan_build", "{\"name\":\"home\",\"x\":500,\"y\":10,\"z\":0}").isPresent());
		assertTrue(validate("plan_build", "{\"x\":1,\"y\":1,\"z\":1}").isPresent());
	}

	@Test
	void theStockpilePositionIsAllOrNothing() {
		String base = "{\"name\":\"home\",\"x\":5,\"y\":10,\"z\":-3,";
		assertEquals(Optional.empty(), validate("plan_build", base + "\"stockpile_x\":6,\"stockpile_y\":10,\"stockpile_z\":-3}"));
		assertTrue(validate("plan_build", base + "\"stockpile_x\":6}").isPresent());
		assertTrue(validate("plan_build", base + "\"stockpile_x\":6,\"stockpile_y\":10}").isPresent());
		assertTrue(validate("plan_build", base + "\"stockpile_x\":600,\"stockpile_y\":10,\"stockpile_z\":-3}").isPresent());
	}

	@Test
	void showBlueprintNeedsANameAndALayer() {
		assertEquals(Optional.empty(), validate("show_blueprint", "{\"name\":\"home\",\"layer\":0}"));
		assertTrue(validate("show_blueprint", "{\"name\":\"home\",\"layer\":-1}").isPresent());
		assertTrue(validate("show_blueprint", "{\"name\":\"home\"}").isPresent());
	}

	@Test
	void theToolsWithoutArgumentsAcceptAnEmptyObject() {
		for (String name : List.of("list_blueprints", "start_build", "build_status", "stop_build", "cancel_build")) {
			assertEquals(Optional.empty(), validate(name, "{}"), name);
		}
	}

	@Test
	void theBuildingToolsAreExplainedInThePromptAndTheGuide() {
		String prompt = SystemPrompt.build(VillagerBrain.DEFAULT.makeSmart("Alma"), SmartKind.VILLAGER, "state", "king", "King");
		assertTrue(prompt.contains("plan_build") && prompt.contains("start_build"));
		assertTrue(prompt.contains("BUILDING"));
		StringBuilder guide = new StringBuilder();
		for (GuideContent.Page page : GuideContent.pages()) {
			guide.append(page.title()).append(' ').append(page.body()).append(' ');
		}
		for (String tool : NEW_TOOLS) {
			assertTrue(guide.toString().contains(tool), "the guide does not mention " + tool);
		}
	}
}
