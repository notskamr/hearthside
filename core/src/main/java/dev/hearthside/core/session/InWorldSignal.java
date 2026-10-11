package dev.hearthside.core.session;

/**
 * When a guest's game is really in the world (Spec A section 3.1(5)): after it was told to join, the first frame it
 * exports with a level, a player and no screen open. Later loading screens (portals, respawns) are not a new join.
 * Also when it leaves its world on its own (its connection gone after being in). Not thread-safe.
 */
public final class InWorldSignal {
	private boolean awaiting;
	private boolean in;

	/** It was told to join a world. */
	public void joinRequested() {
		awaiting = true;
		in = false;
	}

	/** After a frame: true exactly once per join, the first time it was exported in the world with no screen open. */
	public boolean observe(boolean inLevel, boolean screenOpen, boolean frameExported) {
		if (awaiting && inLevel && !screenOpen && frameExported) {
			awaiting = false;
			in = true;
			return true;
		}
		return false;
	}

	/** Whether it is in the world: from its first frame there until it leaves. */
	public boolean inWorld() {
		return in;
	}

	/**
	 * True once when a game that was in the world has lost its connection to it (it quit or was disconnected). A server
	 * reconfiguring it ({@code reconfiguring}) drops its player for a while, so its connection reads as gone; it hasn't
	 * left.
	 */
	public boolean observeLeft(boolean connected, boolean reconfiguring) {
		if (in && !connected && !reconfiguring) {
			in = false;
			return true;
		}
		return false;
	}
}
