package dev.hearthside.fabric.client.compositor.backend;

/** Shared memory could not be imported as a texture here; the caller copies frames instead. */
public final class ImportFailed extends Exception {
	public ImportFailed(String message) {
		super(message);
	}

	public ImportFailed(String message, Throwable cause) {
		super(message, cause);
	}
}
