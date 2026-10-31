package com.villaging.blueprint;

/** A blueprint, shape plan or template that cannot be used; the message says exactly why so the AI can fix it. */
public class BlueprintException extends Exception {
	public BlueprintException(String message) {
		super(message);
	}
}
