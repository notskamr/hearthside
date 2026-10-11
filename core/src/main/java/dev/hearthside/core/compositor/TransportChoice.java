package dev.hearthside.core.compositor;

/** Picks the frame transport for a session from what the GPU supports. */
public final class TransportChoice {
	private TransportChoice() {}

	public static Transport choose(ProbeFacts facts) {
		boolean gpuSharing = facts.glMemoryObjectWin32() && facts.vulkanDeviceMatchesGl() && facts.vulkanExternalMemoryWin32();
		return gpuSharing && !facts.forceCopy() ? Transport.GPU_SHARED : Transport.COPY;
	}
}
