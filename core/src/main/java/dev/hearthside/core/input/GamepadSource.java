package dev.hearthside.core.input;

import java.util.List;

/** Reads every connected controller. Called once per rendered frame on the render thread. */
public interface GamepadSource {
	List<PadSnapshot> poll();
}
