package com.villaging.ai.tools;

import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import com.villaging.ai.script.Script;
import com.villaging.ai.script.ScriptException;
import com.villaging.ai.script.ScriptParser;
import java.util.Optional;

/** Tools for long jobs: run a whole plan of tool calls without asking the model again at every step. */
public final class ScriptTools {
	public static final int MAX_SCRIPT_CHARS = 20000;
	/** Deepest nesting the tool accepts when it only checks the script; the configured limit applies when it runs. */
	private static final int VALIDATION_DEPTH = 10;

	private ScriptTools() {
	}

	/** Parses script text, throwing {@link ScriptException} with a message for the model when it is not acceptable. */
	public static Script parse(String text, int maxDepth) {
		JsonElement json;
		try {
			json = JsonParser.parseString(text);
		} catch (JsonParseException e) {
			throw new ScriptException("The script must be valid JSON text.");
		}
		if (!json.isJsonObject()) {
			throw new ScriptException("The script must be a JSON object like {\"steps\":[...]}.");
		}
		return ScriptParser.parse(json.getAsJsonObject(), maxDepth);
	}

	public static void register() {
		Tools.register(new ToolDef("run_script",
				"Run a whole plan of steps by yourself without asking again at each step. Use it for long or repetitive jobs. "
						+ "The script is JSON text: {\"steps\":[STEP,...]}. A STEP is one of: "
						+ "{\"tool\":\"mine_block\",\"args\":{\"x\":1,\"y\":64,\"z\":2}}; "
						+ "{\"repeat\":N,\"steps\":[...]} (1 to 100 times); "
						+ "{\"until\":COND,\"steps\":[...]} (repeat until COND); "
						+ "{\"if\":COND,\"then\":[...],\"else\":[...]}. "
						+ "COND is exactly one of {\"have\":{\"item\":\"minecraft:oak_log\",\"count\":10}}, "
						+ "{\"has_block\":{\"x\":1,\"y\":2,\"z\":3}}, {\"health_below\":8}, {\"night\":true}. "
						+ "Steps can use any tool except run_script and stop_script. The script stops at the first failed step.",
				object(prop("script", "string", "The script as JSON text.")).required("script").build()),
				(args, range, ox, oy, oz) -> {
					Optional<String> text = ToolSchema.stringNonBlank(args, "script", MAX_SCRIPT_CHARS);
					if (text.isPresent()) {
						return text;
					}
					try {
						parse(args.get("script").getAsString(), VALIDATION_DEPTH);
						return Optional.empty();
					} catch (ScriptException e) {
						return Optional.of(e.getMessage());
					}
				});
		Tools.register(new ToolDef("stop_script", "Stop the script you are running.", object().build()),
				(JsonObject args, int range, double ox, double oy, double oz) -> Optional.empty());
	}
}
