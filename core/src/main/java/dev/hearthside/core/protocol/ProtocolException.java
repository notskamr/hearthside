package dev.hearthside.core.protocol;

import java.io.IOException;

/** A control-channel frame broke the protocol; the connection should be dropped. */
public final class ProtocolException extends IOException {
	public ProtocolException(String message) {
		super(message);
	}
}
