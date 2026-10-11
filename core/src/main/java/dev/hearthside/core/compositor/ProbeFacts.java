package dev.hearthside.core.compositor;

/**
 * What the host's GPU supports for sharing frames between processes.
 *
 * @param glMemoryObjectWin32 OpenGL has {@code GL_EXT_memory_object_win32}
 * @param vulkanDeviceMatchesGl a Vulkan device has the same device UUID as the OpenGL context
 * @param vulkanExternalMemoryWin32 that Vulkan device has {@code VK_KHR_external_memory_win32}
 * @param forceCopy the user (or a test) asked for the copy transport
 */
public record ProbeFacts(boolean glMemoryObjectWin32, boolean vulkanDeviceMatchesGl, boolean vulkanExternalMemoryWin32,
		boolean forceCopy) {}
