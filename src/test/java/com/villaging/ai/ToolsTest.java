package com.villaging.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ToolsTest {
	private static ToolCall call(String name, JsonObject args) {
		return new ToolCall("id1", name, args);
	}

	private static JsonObject xyz(Object x, Object y, Object z) {
		JsonObject o = new JsonObject();
		put(o, "x", x);
		put(o, "y", y);
		put(o, "z", z);
		return o;
	}

	private static void put(JsonObject o, String key, Object v) {
		if (v == null) {
			return;
		}
		if (v instanceof Number n) {
			o.addProperty(key, n);
		} else {
			o.addProperty(key, v.toString());
		}
	}

	private static Optional<String> validate(ToolCall c) {
		return Tools.validate(c, 64, 0, 64, 0);
	}

	@Test
	void foundationRegistersTheFiveTools() {
		assertEquals(5, Tools.all().size());
		assertTrue(Tools.all().stream().anyMatch(t -> t.name().equals("mine_block")));
	}

	@Test
	void registeredToolIsListedAndValidated() {
		ToolDef def = new ToolDef("dance", "Dance", new JsonObject());
		Tools.register(def, (args, range, ox, oy, oz) -> Optional.of("no dancing"));
		try {
			assertTrue(Tools.all().contains(def));
			assertEquals(Optional.of("no dancing"), validate(call("dance", new JsonObject())));
		} finally {
			Tools.unregister("dance");
		}
	}

	@Test
	void unknownToolIsRejected() {
		assertTrue(validate(call("fly_to_moon", new JsonObject())).orElseThrow().contains("Unknown tool"));
	}

	@Test
	void goToMissingCoordinateRejected() {
		assertTrue(validate(call("go_to", xyz(1, 64, null))).orElseThrow().contains("z"));
	}

	@Test
	void goToNonNumericRejected() {
		assertTrue(validate(call("go_to", xyz("here", 64, 3))).orElseThrow().contains("x"));
	}

	@Test
	void goToNaNOrInfiniteRejected() {
		assertTrue(validate(call("go_to", xyz(Double.NaN, 64, 3))).isPresent());
		assertTrue(validate(call("go_to", xyz(1, Double.POSITIVE_INFINITY, 3))).isPresent());
	}

	@Test
	void goToOutsideRangeRejected() {
		assertTrue(validate(call("go_to", xyz(65, 64, 0))).orElseThrow().contains("too far"));
	}

	@Test
	void goToInsideRangeAccepted() {
		assertEquals(Optional.empty(), validate(call("go_to", xyz(10.5, 64, -20.25))));
	}

	@Test
	void mineBlockInsideRangeAccepted() {
		assertEquals(Optional.empty(), validate(call("mine_block", xyz(3, 63, 4))));
	}

	@Test
	void mineBlockFractionalCoordinateRejected() {
		assertTrue(validate(call("mine_block", xyz(3.5, 63, 4))).isPresent());
	}

	@Test
	void sayWithBlankTextRejected() {
		JsonObject args = new JsonObject();
		args.addProperty("text", "   ");
		assertTrue(validate(call("say", args)).isPresent());
	}

	@Test
	void sayOver500CharsRejected() {
		JsonObject args = new JsonObject();
		args.addProperty("text", "a".repeat(501));
		assertTrue(validate(call("say", args)).orElseThrow().contains("500"));
	}

	@Test
	void sayWithNormalTextAccepted() {
		JsonObject args = new JsonObject();
		args.addProperty("text", "Yes, my king.");
		assertEquals(Optional.empty(), validate(call("say", args)));
	}

	@Test
	void followMeAndStopNeedNoArguments() {
		assertEquals(Optional.empty(), validate(call("follow_me", new JsonObject())));
		assertEquals(Optional.empty(), validate(call("stop", new JsonObject())));
	}
}
