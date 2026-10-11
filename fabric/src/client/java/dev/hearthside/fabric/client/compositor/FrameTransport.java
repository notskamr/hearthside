package dev.hearthside.fabric.client.compositor;

/**
 * One side (guest or host) of a set of shared frame slots, each a {@code width}×{@code height} RGBA8 GL texture in
 * this process. The guest draws into {@link #glTexture}, calls {@link #afterDraw}, and once its GL fence has signalled
 * calls {@link #beforePublish} before publishing the slot. The host calls {@link #beforeRead} on a slot it has just
 * acquired, then draws from {@link #glTexture}. Render thread only.
 */
public interface FrameTransport extends AutoCloseable {
	int width();

	int height();

	int slots();

	/** This process's GL texture for {@code slot}. */
	int glTexture(int slot);

	/** Guest: the frame is drawn into the slot's texture (queue any readback now). */
	void afterDraw(int slot);

	/** Guest: the GPU has finished the slot's frame; make it visible to the host. */
	void beforePublish(int slot);

	/** Host: the slot was just acquired; make its texture hold the guest's frame. */
	void beforeRead(int slot);

	@Override
	void close();
}
