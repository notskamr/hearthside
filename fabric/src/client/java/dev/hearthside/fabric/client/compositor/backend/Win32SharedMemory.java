package dev.hearthside.fabric.client.compositor.backend;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;

/**
 * Named shared memory backed by the page file, never by a file on disk (Spec D section 3.2): a Windows section made with
 * {@code CreateFileMappingW(INVALID_HANDLE_VALUE, …)}. The guest creates it; the host only opens it, so a section that
 * has gone is an error rather than fresh memory (R11). Windows keeps a section alive while any process holds a handle
 * to it or a view of it.
 */
public final class Win32SharedMemory {
	private static final long INVALID_HANDLE_VALUE = -1;
	private static final int PAGE_READWRITE = 0x04;
	private static final int FILE_MAP_ALL_ACCESS = 0xF001F;

	private static final StructLayout CALL_STATE = Linker.Option.captureStateLayout();
	private static final VarHandle LAST_ERROR =
			CALL_STATE.varHandle(MemoryLayout.PathElement.groupElement("GetLastError"));

	private record Kernel32(MethodHandle createFileMapping, MethodHandle openFileMapping, MethodHandle mapViewOfFile,
			MethodHandle unmapViewOfFile) {}

	private static Kernel32 kernel32;

	private Win32SharedMemory() {}

	/**
	 * Creates the section {@code name} of {@code size} bytes and maps all of it. Closing {@code arena} unmaps the view and
	 * closes the handle.
	 */
	public static MemorySegment create(String name, long size, Arena arena) throws IOException {
		long handle;
		try (Arena call = Arena.ofConfined()) {
			MemorySegment state = call.allocate(CALL_STATE);
			handle = (long) handles().createFileMapping.invokeExact(state, INVALID_HANDLE_VALUE, MemorySegment.NULL,
					PAGE_READWRITE, (int) (size >>> 32), (int) size, call.allocateFrom(name, StandardCharsets.UTF_16LE));
			// A section that already has this name (ERROR_ALREADY_EXISTS) comes back as it is: names differ by generation,
			// and one too small for size fails to map below.
			if (handle == 0) {
				throw failure("CreateFileMappingW", name, state);
			}
		} catch (IOException e) {
			throw e;
		} catch (Throwable t) {
			throw new IOException("CreateFileMappingW " + name + " failed", t);
		}
		return map(handle, name, size, arena);
	}

	/**
	 * Opens the existing section {@code name} and maps its first {@code size} bytes. Closing {@code arena} unmaps the view
	 * and closes the handle.
	 */
	public static MemorySegment open(String name, long size, Arena arena) throws IOException {
		long handle;
		try (Arena call = Arena.ofConfined()) {
			MemorySegment state = call.allocate(CALL_STATE);
			handle = (long) handles().openFileMapping.invokeExact(state, FILE_MAP_ALL_ACCESS, 0,
					call.allocateFrom(name, StandardCharsets.UTF_16LE));
			if (handle == 0) {
				throw failure("OpenFileMappingW", name, state);
			}
		} catch (IOException e) {
			throw e;
		} catch (Throwable t) {
			throw new IOException("OpenFileMappingW " + name + " failed", t);
		}
		return map(handle, name, size, arena);
	}

	/** Maps the whole of {@code size} bytes of an open section, which the returned segment then owns. */
	private static MemorySegment map(long handle, String name, long size, Arena arena) throws IOException {
		MemorySegment view;
		try (Arena call = Arena.ofConfined()) {
			MemorySegment state = call.allocate(CALL_STATE);
			view = (MemorySegment) handles().mapViewOfFile.invokeExact(state, handle, FILE_MAP_ALL_ACCESS, 0, 0, size);
			if (view.address() == 0) {
				// A section smaller than size (a stale one, or the wrong size) fails here too.
				throw failure("MapViewOfFile", name, state);
			}
		} catch (Throwable t) {
			Win32Handles.close(handle);
			if (t instanceof IOException e) {
				throw e;
			}
			throw new IOException("MapViewOfFile " + name + " failed", t);
		}
		return view.reinterpret(size, arena, mapped -> {
			try {
				int ignored = (int) handles().unmapViewOfFile.invokeExact(mapped);
			} catch (Throwable t) {
				// Nothing more can be done for it; the handle still goes.
			}
			Win32Handles.close(handle);
		});
	}

	private static IOException failure(String call, String name, MemorySegment state) {
		return new IOException(call + " " + name + " failed: GetLastError=" + (int) LAST_ERROR.get(state, 0L));
	}

	/** Looked up on first use, so merely loading this class works off Windows. */
	private static synchronized Kernel32 handles() {
		if (kernel32 == null) {
			Linker linker = Linker.nativeLinker();
			SymbolLookup library = SymbolLookup.libraryLookup("kernel32", Arena.global());
			Linker.Option lastError = Linker.Option.captureCallState("GetLastError");
			kernel32 = new Kernel32(
					linker.downcallHandle(library.find("CreateFileMappingW").orElseThrow(),
							FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS,
									ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
							lastError),
					linker.downcallHandle(library.find("OpenFileMappingW").orElseThrow(),
							FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
									ValueLayout.ADDRESS),
							lastError),
					linker.downcallHandle(library.find("MapViewOfFile").orElseThrow(),
							FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
									ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG),
							lastError),
					linker.downcallHandle(library.find("UnmapViewOfFile").orElseThrow(),
							FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS)));
		}
		return kernel32;
	}
}
