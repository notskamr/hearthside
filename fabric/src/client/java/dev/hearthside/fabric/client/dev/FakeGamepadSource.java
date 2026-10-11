package dev.hearthside.fabric.client.dev;

import dev.hearthside.core.input.GamepadSource;
import dev.hearthside.core.input.InputFrame;
import dev.hearthside.core.input.PadSnapshot;
import dev.hearthside.core.input.PadStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/** Scripted controllers for the E2E driver ({@code -Dhearthside.e2e.fakePads=true}). Thread-safe. */
public final class FakeGamepadSource implements GamepadSource {
	public static final FakeGamepadSource INSTANCE = new FakeGamepadSource();

	private record Pad(String guid, PadStyle style, InputFrame frame) {}

	private final Map<Integer, Pad> pads = new ConcurrentSkipListMap<>();

	private FakeGamepadSource() {}

	public void plug(int id, String guid, PadStyle style) {
		pads.put(id, new Pad(guid, style, InputFrame.NEUTRAL));
	}

	public void unplug(int id) {
		pads.remove(id);
	}

	public void set(int id, InputFrame frame) {
		pads.computeIfPresent(id, (k, pad) -> new Pad(pad.guid(), pad.style(), frame));
	}

	/** What each pad was last marked with: {player, rgb}. */
	private final Map<Integer, int[]> indicated = new java.util.concurrent.ConcurrentHashMap<>();

	@Override
	public void indicate(int instanceId, int player, int rgb) {
		indicated.put(instanceId, new int[] {player, rgb});
	}

	/** The player and colour a pad was last marked with, or null. */
	public int[] indicated(int instanceId) {
		return indicated.get(instanceId);
	}

	@Override
	public List<PadSnapshot> poll() {
		List<PadSnapshot> snapshots = new ArrayList<>();
		pads.forEach((id, pad) -> snapshots.add(new PadSnapshot(id, pad.guid(), "Fake pad " + id, pad.style(), pad.frame())));
		return snapshots;
	}
}
