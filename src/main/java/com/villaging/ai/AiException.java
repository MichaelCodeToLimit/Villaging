package com.villaging.ai;

public class AiException extends Exception {
	private final int status;

	public AiException(String message) {
		this(message, 0);
	}

	/** @param status the HTTP status the AI service answered with, or 0 when there was no answer */
	public AiException(String message, int status) {
		super(message);
		this.status = status;
	}

	public AiException(String message, Throwable cause) {
		super(message, cause);
		this.status = 0;
	}

	public int status() {
		return status;
	}
}
