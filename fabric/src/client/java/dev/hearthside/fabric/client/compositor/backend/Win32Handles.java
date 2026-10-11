package dev.hearthside.fabric.client.compositor.backend;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** The one Win32 call the GPU transport needs that LWJGL does not bind: closing an exported handle. */
public final class Win32Handles {
	private static MethodHandle closeHandle;

	private Win32Handles() {}

	public static void close(long handle) {
		if (handle == 0) {
			return;
		}
		try {
			int ignored = (int) closeHandle().invokeExact(handle);
		} catch (Throwable t) {
			throw new IllegalStateException("CloseHandle failed", t);
		}
	}

	/** Looked up on first use, so merely loading this class works off Windows. */
	private static synchronized MethodHandle closeHandle() {
		if (closeHandle == null) {
			closeHandle = Linker.nativeLinker().downcallHandle(
					SymbolLookup.libraryLookup("kernel32", Arena.global()).find("CloseHandle").orElseThrow(),
					FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
		}
		return closeHandle;
	}
}
