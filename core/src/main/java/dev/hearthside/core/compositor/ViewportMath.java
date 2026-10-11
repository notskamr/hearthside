package dev.hearthside.core.compositor;

import dev.hearthside.core.layout.Rect;

/**
 * Geometry for drawing several games into one window. Viewports are in framebuffer pixels with a top-left origin;
 * {@code screenPerFb} converts framebuffer pixels to window coordinates (1.0 normally, 0.5 on a 2x display).
 */
public final class ViewportMath {
	private ViewportMath() {}

	/** A point in window coordinates (or viewport-local window coordinates). */
	public record Point(double x, double y) {}

	/** The size a game drawing into {@code viewportFb} should render at. */
	public static VirtualSize virtualSize(Rect viewportFb, double screenPerFb) {
		return new VirtualSize(viewportFb.width(), viewportFb.height(),
				Math.max(1, (int) Math.round(viewportFb.width() * screenPerFb)),
				Math.max(1, (int) Math.round(viewportFb.height() * screenPerFb)));
	}

	/** Maps a window position into the viewport's own coordinates, clamped to its edges. */
	public static Point windowToViewport(Point window, Rect viewportFb, double screenPerFb) {
		double left = viewportFb.x() * screenPerFb, top = viewportFb.y() * screenPerFb;
		double width = viewportFb.width() * screenPerFb, height = viewportFb.height() * screenPerFb;
		return new Point(Math.clamp(window.x() - left, 0, width), Math.clamp(window.y() - top, 0, height));
	}

	/** Whether a window position falls inside the viewport (left and top edges inclusive, right and bottom exclusive). */
	public static boolean contains(Point window, Rect viewportFb, double screenPerFb) {
		double left = viewportFb.x() * screenPerFb, top = viewportFb.y() * screenPerFb;
		double right = left + viewportFb.width() * screenPerFb, bottom = top + viewportFb.height() * screenPerFb;
		return window.x() >= left && window.x() < right && window.y() >= top && window.y() < bottom;
	}

	/** The largest rectangle with the frame's aspect ratio that fits in {@code target}, centred. */
	public static Rect letterbox(int frameWidth, int frameHeight, Rect target) {
		if (frameWidth < 1 || frameHeight < 1) {
			return target;
		}
		int width, height;
		if ((long) frameWidth * target.height() >= (long) frameHeight * target.width()) {
			width = target.width();
			height = (int) ((long) frameHeight * target.width() / frameWidth);
		} else {
			height = target.height();
			width = (int) ((long) frameWidth * target.height() / frameHeight);
		}
		return new Rect(target.x() + (target.width() - width) / 2, target.y() + (target.height() - height) / 2, width, height);
	}

	/** Converts a top-left-origin rectangle into OpenGL's bottom-left origin for a framebuffer {@code fbHeight} tall. */
	public static Rect glFlip(Rect topLeftOrigin, int fbHeight) {
		return new Rect(topLeftOrigin.x(), fbHeight - topLeftOrigin.y() - topLeftOrigin.height(),
				topLeftOrigin.width(), topLeftOrigin.height());
	}
}
