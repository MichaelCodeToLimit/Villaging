package com.villaging.ai.tools;

import static com.villaging.ai.ToolSchema.enumProp;
import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.villaging.ai.RiskPolicy;
import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Tools about asking the king: risky-act preferences (and, later, the question dialog). */
public final class AskTools {
	private static final Set<String> CATEGORIES = Arrays.stream(RiskPolicy.Category.values())
			.map(RiskPolicy.Category::key).collect(Collectors.toSet());

	public static final int MAX_QUESTION = 300;
	public static final int MAX_OPTIONS = 4;
	public static final int MAX_OPTION_LENGTH = 60;

	private AskTools() {
	}

	public static void register() {
		Tools.register(new ToolDef("ask",
				"Ask the king a question in a pop-up dialog with a few options (and a box for their own answer). Use it "
						+ "when you truly need a decision, such as which of two things to do. The answer comes back as the result.",
				object(prop("question", "string", "The question, short and clear."),
						ToolSchema.arrayOfStrings("options", "Up to 4 short choices; may be empty.")).required("question").build()),
				(args, range, ox, oy, oz) -> {
					Optional<String> question = ToolSchema.stringNonBlank(args, "question", MAX_QUESTION);
					if (question.isPresent()) {
						return question;
					}
					JsonElement options = args.get("options");
					if (options == null) {
						return Optional.empty();
					}
					if (!options.isJsonArray() || options.getAsJsonArray().size() > MAX_OPTIONS) {
						return Optional.of("\"options\" must be a list of at most " + MAX_OPTIONS + " short choices.");
					}
					for (JsonElement option : options.getAsJsonArray()) {
						if (!option.isJsonPrimitive() || !((JsonPrimitive) option).isString() || option.getAsString().isBlank()
								|| option.getAsString().length() > MAX_OPTION_LENGTH) {
							return Optional.of("Each option must be non-blank text of at most " + MAX_OPTION_LENGTH + " characters.");
						}
					}
					return Optional.empty();
				});
		Tools.register(new ToolDef("set_ask_preference",
				"Change whether you ask the king before a risky act. Use it only when the king tells you to stop asking "
						+ "(ask=false) or to ask again (ask=true) about a category. Turning asking off needs the king's yes.",
				object(enumProp("category", "The kind of risky act.", CATEGORIES.stream().sorted().toArray(String[]::new)),
						prop("ask", "boolean", "true to ask the king first, false to stop asking.")).required("category", "ask").build()),
				(args, range, ox, oy, oz) -> {
					Optional<String> category = ToolSchema.oneOf(args, "category", CATEGORIES);
					if (category.isPresent()) {
						return category;
					}
					JsonElement ask = args.get("ask");
					if (ask == null || !ask.isJsonPrimitive() || !((JsonPrimitive) ask).isBoolean()) {
						return Optional.of("\"ask\" must be true or false.");
					}
					return Optional.empty();
				});
	}
}
