package com.villaging.ai.tools;

import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import java.util.Optional;

/** Movement beyond walking: sitting still and sleeping in a bed. */
public final class MoveTools {
	private MoveTools() {
	}

	public static void register() {
		Tools.register(new ToolDef("sit", "Sit down and stay where you are until told to do something else.", object().build()),
				(args, range, ox, oy, oz) -> Optional.empty());
		Tools.register(new ToolDef("sleep",
				"Walk to a bed and sleep in it until morning. Only possible at night or in a thunderstorm.",
				object(prop("x", "integer", "Bed X."), prop("y", "integer", "Bed Y."), prop("z", "integer", "Bed Z."))
						.required("x", "y", "z").build()),
				(args, range, ox, oy, oz) -> ToolSchema.coordinates(args, true, range, ox, oy, oz));
	}
}
