package com.villaging.ai.script;

/** A yes/no question about the villager's situation, used by {@code until} and {@code if}. */
public sealed interface Condition {
	boolean test(Facts facts);

	record Have(String item, int count) implements Condition {
		@Override
		public boolean test(Facts facts) {
			return facts.count(item) >= count;
		}
	}

	record HasBlock(int x, int y, int z) implements Condition {
		@Override
		public boolean test(Facts facts) {
			return facts.hasBlock(x, y, z);
		}
	}

	record HealthBelow(double value) implements Condition {
		@Override
		public boolean test(Facts facts) {
			return facts.health() < value;
		}
	}

	record Night() implements Condition {
		@Override
		public boolean test(Facts facts) {
			return facts.isNight();
		}
	}
}
