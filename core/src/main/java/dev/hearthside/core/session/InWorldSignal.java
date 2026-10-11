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

	/** True once when a game that was in the world has lost its connection to it (it quit or was disconnected). */
	public boolean observeLeft(boolean connected) {
		if (in && !connected) {
			in = false;
			return true;
		}
		return false;
	}
}
