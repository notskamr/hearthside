package dev.hearthside.core.compositor;

import dev.hearthside.core.layout.Rect;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * The push constants for drawing one view onto the canvas.
 *
 * @param dst x0, y0, x1, y1 on the canvas, normalised from its top-left (a view mid-glide may lie partly outside 0..1)
 * @param src u0, v0, u1, v1 into the view's frame; v runs 1 to 0 when the frame's rows are stored the other way up
 * @param texelW one texel's width in the frame, for the blur
 * @param texelH one texel's height in the frame, for the blur
 * @param alpha the view's opacity, 0..1
 * @param blur the blur radius in texels: {@link #STALE_BLUR_TEXELS} for a stale frame, else 0
 */
public record ViewQuad(float[] dst, float[] src, float texelW, float texelH, float alpha, float blur) {
	/** Matches the 1/8 downsample that blurs a stale frame today. */
	public static final float STALE_BLUR_TEXELS = 8f;
	/** What {@link #write} writes: 12 floats. */
	public static final int BYTES = 12 * Float.BYTES;

	/** The quad for a view at {@code dst} on the canvas, or empty when the view, canvas or frame has no area. */
	public static Optional<ViewQuad> of(Rect dst, int canvasWidth, int canvasHeight, int sourceWidth, int sourceHeight,
			boolean flipRows, float alpha, boolean stale) {
		if (dst.width() <= 0 || dst.height() <= 0 || canvasWidth <= 0 || canvasHeight <= 0
				|| sourceWidth <= 0 || sourceHeight <= 0) {
			return Optional.empty();
		}
		float[] d = {
				(float) dst.x() / canvasWidth, (float) dst.y() / canvasHeight,
				(float) (dst.x() + dst.width()) / canvasWidth, (float) (dst.y() + dst.height()) / canvasHeight};
		float[] s = flipRows ? new float[] {0f, 1f, 1f, 0f} : new float[] {0f, 0f, 1f, 1f};
		return Optional.of(new ViewQuad(d, s, 1f / sourceWidth, 1f / sourceHeight, Math.clamp(alpha, 0f, 1f),
				stale ? STALE_BLUR_TEXELS : 0f));
	}

	/** Writes the 12 floats in shader order (dst, src, texelW, texelH, alpha, blur), in the buffer's byte order. */
	public void write(ByteBuffer buffer) {
		for (float f : dst) {
			buffer.putFloat(f);
		}
		for (float f : src) {
			buffer.putFloat(f);
		}
		buffer.putFloat(texelW).putFloat(texelH).putFloat(alpha).putFloat(blur);
	}
}
