package dev.hearthside.fabric.client.compositor;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/** The one Win32 call the GPU transport needs that LWJGL does not bind: closing an exported handle. */
final class Kernel32 {
	private static final MethodHandle CLOSE_HANDLE = Linker.nativeLinker().downcallHandle(
			SymbolLookup.libraryLookup("kernel32", Arena.global()).find("CloseHandle").orElseThrow(),
			FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));

	private Kernel32() {}

	static void closeHandle(long handle) {
		if (handle == 0) {
			return;
		}
		try {
			int ignored = (int) CLOSE_HANDLE.invokeExact(handle);
		} catch (Throwable t) {
			throw new IllegalStateException("CloseHandle failed", t);
		}
	}
}
