package com.villaging.ai.tools;

import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import java.util.Optional;

/** Tools for passing orders down the chain of command. */
public final class CommandTools {
	public static final int MAX_ORDER_LENGTH = 500;

	private CommandTools() {
	}

	public static void register() {
		Tools.register(new ToolDef("relay_order",
				"Commanders only: pass an order on to another smart villager by name. The king is asked to approve it first.",
				object(prop("villager_name", "string", "The other villager's name."),
						prop("order", "string", "What to tell them, in plain words.")).required("villager_name", "order").build()),
				(args, range, ox, oy, oz) -> {
					Optional<String> name = ToolSchema.stringNonBlank(args, "villager_name", 100);
					return name.isPresent() ? name : ToolSchema.stringNonBlank(args, "order", MAX_ORDER_LENGTH);
				});
	}
}
