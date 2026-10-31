package com.villaging.team;

/** A team rule was broken; the message is plain words the king can read. */
public class TeamException extends Exception {
	public TeamException(String message) {
		super(message);
	}
}
