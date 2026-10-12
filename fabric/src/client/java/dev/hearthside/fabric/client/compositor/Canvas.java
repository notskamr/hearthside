package dev.hearthside.fabric.client.compositor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

/**
 * A texture every view is drawn into before Minecraft presents it (Spec C section 3.3): the host's window-sized canvas,
 * or a scratch picture the fallback scales a view into. Its size only changes through {@link #ensure}, and closing it
 * goes through Minecraft, which destroys it once the GPU has finished with it. Render thread only.
 */
final class Canvas implements AutoCloseable {
	/** Drawn into, sampled, copied from (presenting, readbacks) and copied into (the fallback's placed views). */
	static final int USAGE = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING
			| GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST;

	private final String label;
	private GpuTexture texture;
	private GpuTextureView view;

	Canvas(String label) {
		this.label = label;
	}

	/**
	 * (Re)creates the texture at {@code width}×{@code height} if it isn't that size already. Returns false, and keeps
	 * whatever it had, when either side is below 1 (a minimised window): no zero-sized texture is ever created.
	 */
	boolean ensure(int width, int height) {
		if (width < 1 || height < 1) {
			return false;
		}
		if (texture != null && texture.getWidth(0) == width && texture.getHeight(0) == height) {
			return true;
		}
		close();
		GpuDevice device = RenderSystem.getDevice();
		texture = device.createTexture(label, USAGE, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
		FrameHooks.poison(texture);
		view = device.createTextureView(texture);
		return true;
	}

	GpuTexture texture() {
		return texture;
	}

	GpuTextureView view() {
		return view;
	}

	int width() {
		return texture == null ? 0 : texture.getWidth(0);
	}

	int height() {
		return texture == null ? 0 : texture.getHeight(0);
	}

	@Override
	public void close() {
		if (view != null) {
			view.close();
			view = null;
		}
		if (texture != null) {
			texture.close();
			texture = null;
		}
	}
}
