package dev.hearthside.core.profile;

import java.io.IOException;

/** The profiles file was written by a newer Hearthside; it is left untouched rather than overwritten. */
public final class UnsupportedSchemaException extends IOException {
	public UnsupportedSchemaException(String message) {
		super(message);
	}
}
