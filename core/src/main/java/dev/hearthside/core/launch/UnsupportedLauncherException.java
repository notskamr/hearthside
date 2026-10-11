package dev.hearthside.core.launch;

/** The host was started in a way Hearthside can't reproduce. The message is shown to the player. */
public final class UnsupportedLauncherException extends Exception {
	public UnsupportedLauncherException(String message) {
		super(message);
	}
}
