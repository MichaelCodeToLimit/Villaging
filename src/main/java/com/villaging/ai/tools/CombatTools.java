package com.villaging.ai.tools;

import static com.villaging.ai.ToolSchema.object;
import static com.villaging.ai.ToolSchema.prop;

import com.villaging.ai.ToolDef;
import com.villaging.ai.ToolSchema;
import com.villaging.ai.Tools;
import java.util.Optional;

/** Tools for fighting and keeping safe. Players and pets are never valid targets. */
public final class CombatTools {
	public static final int MAX_FLEE_SECONDS = 60;

	private CombatTools() {
	}

	public static void register() {
		Tools.register(new ToolDef("attack",
				"Fight a creature in melee until it is dead, using the weapon you hold or carry. You will not hurt players or pets.",
				object(prop("entity_id", "integer", "The target's id from look_around.")).required("entity_id").build()),
				(args, range, ox, oy, oz) -> ToolSchema.intIn(args, "entity_id", 0, Integer.MAX_VALUE));
		Tools.register(new ToolDef("shoot",
				"Shoot a creature once with a bow and arrows from your inventory. You will not hurt players or pets.",
				object(prop("entity_id", "integer", "The target's id from look_around.")).required("entity_id").build()),
				(args, range, ox, oy, oz) -> ToolSchema.intIn(args, "entity_id", 0, Integer.MAX_VALUE));
		Tools.register(new ToolDef("guard",
				"Stay near the king and fight hostile monsters that come close, until told to stop.", object().build()),
				(args, range, ox, oy, oz) -> Optional.empty());
		Tools.register(new ToolDef("flee", "Run away from nearby danger for a while.",
				object(prop("seconds", "integer", "How long to run, 1 to " + MAX_FLEE_SECONDS + ".")).required("seconds").build()),
				(args, range, ox, oy, oz) -> ToolSchema.intIn(args, "seconds", 1, MAX_FLEE_SECONDS));
	}
}
