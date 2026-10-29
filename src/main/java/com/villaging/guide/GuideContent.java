package com.villaging.guide;

import java.util.List;

/** The text of the in-game guide. Pure data; a test makes sure it mentions every ability. */
public final class GuideContent {
	public record Page(String title, String body) {
	}

	private static final List<Page> PAGES = List.of(
			new Page("Welcome, King",
					"Villaging turns villagers and golems into smart helpers that you command in plain words.\n\n"
							+ "They are powered by an AI. The first time you enter a world a box asks for your AI key. "
							+ "Pick Claude, Gemini or Ollama (free, runs on your own computer), paste the key and press "
							+ "Enter.\n\nYou can open that box again any time with /villaging key."),
			new Page("Getting started",
					"1. Sneak + right-click an ordinary villager and type anything. It becomes smart, gets a name, "
							+ "and if the world has no king yet, you become the king.\n\n"
							+ "2. Or use the Crown (gold ingots around a diamond: GGG / GDG) on a villager or golem.\n\n"
							+ "3. After that, just right-click a smart villager to talk. Sneak + right-click gives you the "
							+ "normal trading screen. Enter sends your words, Escape closes the box."),
			new Page("Talking",
					"Talk normally: slang, typos, other languages, questions. Say \"mine that block\" and it mines "
							+ "the block you are looking at.\n\n"
							+ "If it cannot do something it tells you honestly and offers the closest thing.\n\n"
							+ "When a villager needs a decision it opens a pop-up with a few options and a box for your own "
							+ "answer. Escape counts as no answer."),
			new Page("People",
					"rename: \"your name is Garrick\" or \"rename Bob to Odo\".\n\n"
							+ "set_king_title: \"call me Boss\" (everyone) or \"you call me Sire\" (just that one). "
							+ "The default is King.\n\n"
							+ "recruit: \"recruit two villagers\" or \"recruit the iron golem\". You are asked to approve; up to "
							+ "5 nearby villagers, iron golems or copper golems become smart.\n\n"
							+ "relay_order: a commander villager can pass your orders on to others, but only with your "
							+ "approval."),
			new Page("Moving and looking",
					"say: it talks back in character.\n"
							+ "follow_me and stop: it follows you until you tell it to stop.\n"
							+ "go_to: walk to a spot.\n"
							+ "look_around, look_at, check_inventory: it tells you what it sees and carries.\n"
							+ "sit and sleep: it sits still, or lies down in a bed at night."),
			new Page("Mining and building",
					"mine_block: break one block.\n"
							+ "break_area: clear a box (up to 16 blocks a side).\n"
							+ "place_block: place a block it carries.\n"
							+ "use_block: use doors, levers, buttons, beds.\n"
							+ "chop_tree: fell a whole tree.\n\n"
							+ "run_script and stop_script: for long jobs (\"get 20 logs\") it writes a safe step-by-step plan "
							+ "and runs it, so one order can do a lot."),
			new Page("Items and chests",
					"Every villager has its own inventory and can only use what it carries.\n\n"
							+ "pick_up, drop: collect or drop items.\n"
							+ "give_to_king: hand you things.\n"
							+ "take_from_king: only when you tell it to, and only what you name.\n"
							+ "equip, use_item, eat: wear, use or eat things.\n"
							+ "open_container, put_in, take_out: work with chests and barrels."),
			new Page("Making and farming",
					"craft: make things (a crafting table nearby for big recipes).\n"
							+ "smelt: cook or smelt in a furnace.\n"
							+ "brew: make potions in a brewing stand.\n"
							+ "till, plant, harvest: farm crops.\n"
							+ "fish: catch fish near water.\n"
							+ "shear, milk, breed, tame: look after animals."),
			new Page("Fighting",
					"attack: fight a creature.\n"
							+ "shoot: use a bow.\n"
							+ "guard: protect you and fight what threatens you.\n"
							+ "flee: run away from danger.\n\n"
							+ "Villagers and golems never hurt players or pets, whatever they are told. Iron golems hit hard; "
							+ "copper golems are small and tidy."),
			new Page("Staying safe",
					"Risky acts (lava, fire, TNT, wrecking big areas) always make the villager ask you first in a pop-up. "
							+ "Say \"stop asking about fire\" and it uses set_ask_preference to remember.\n\n"
							+ "ask: it can also ask you a question whenever it truly needs a decision.\n\n"
							+ "Villagers obey spawn protection and protection mods. Breaking bedrock is off unless you "
							+ "turn it on in config/villaging.json."),
			new Page("Commands and settings",
					"/villaging key: change your AI key or AI choice.\n"
							+ "/villaging guide: open this guide again.\n"
							+ "/announcements <message>: speak to every helper near you at once.\n"
							+ "/villaging summon <name>: call one helper to your side.\n\n"
							+ "Settings live in config/villaging.json: call limits, how far orders reach, protection and "
							+ "script limits.\n\n"
							+ "Coming later: building from blueprints and ranks. Have fun, King!"));

	private GuideContent() {
	}

	public static List<Page> pages() {
		return PAGES;
	}
}
