package com.villaging.villager;

/** Handlers for the combat tools. */
final class CombatHandlers {
	private CombatHandlers() {
	}

	static void register() {
		ToolHandlers.register("attack", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(),
				new CombatActions.Attack(args.get("entity_id").getAsInt())));
		ToolHandlers.register("shoot", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(),
				new CombatActions.Shoot(args.get("entity_id").getAsInt())));
		ToolHandlers.register("guard", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(),
				new CombatActions.Guard(ctx.kingId())));
		ToolHandlers.register("flee", (ctx, args) -> ActionManager.INSTANCE.run(ctx.villager(),
				new CombatActions.Flee(args.get("seconds").getAsInt())));
	}
}
