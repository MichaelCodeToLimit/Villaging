package com.villaging.ai.script;

/** A script the model sent is not acceptable; the message is written for the model to read and fix. */
public final class ScriptException extends RuntimeException {
	public ScriptException(String message) {
		super(message);
	}
}
