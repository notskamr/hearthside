package dev.hearthside.core.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Decides which controller drives which player (0 = host, 1–3 = guests). Pads are tracked by SDL
 * instance id, so identical controllers never swap players; a player whose pad disconnects gets
 * the next unassigned pad of the same model back.
 */
public final class InputRouter {
	public static final int MAX_PLAYERS = 4;

	public record Update(Map<Integer, InputFrame> frames, List<RouterEvent> events) {}

	private final Map<Integer, Integer> padToPlayer = new HashMap<>();
	private final TreeMap<Integer, Integer> playerToPad = new TreeMap<>();
	private final Map<Integer, String> lastGuid = new HashMap<>();
	private final TreeSet<Integer> lostPlayers = new TreeSet<>();
	private Map<Integer, PadSnapshot> previous = Map.of();
	private boolean claimArmed;

	public Update update(List<PadSnapshot> pads) {
		Map<Integer, PadSnapshot> current = new TreeMap<>();
		for (PadSnapshot pad : pads) {
			current.put(pad.instanceId(), pad);
		}
		List<RouterEvent> lost = new ArrayList<>();
		List<RouterEvent> restored = new ArrayList<>();
		List<RouterEvent> requests = new ArrayList<>();

		for (Map.Entry<Integer, Integer> binding : new TreeMap<>(playerToPad).entrySet()) {
			if (!current.containsKey(binding.getValue())) {
				unbind(binding.getKey());
				lostPlayers.add(binding.getKey());
				lost.add(new RouterEvent.PadLost(binding.getKey()));
			}
		}

		for (PadSnapshot pad : current.values()) {
			if (padToPlayer.containsKey(pad.instanceId())) {
				continue;
			}
			Optional<Integer> owner = lostPlayers.stream().filter(p -> pad.guid().equals(lastGuid.get(p))).findFirst();
			if (owner.isPresent()) {
				bind(pad.instanceId(), owner.get());
				lostPlayers.remove(owner.get());
				restored.add(new RouterEvent.PadRestored(owner.get()));
			}
		}

		for (PadSnapshot pad : current.values()) {
			if (padToPlayer.containsKey(pad.instanceId())) {
				continue;
			}
			PadSnapshot before = previous.get(pad.instanceId());
			InputFrame was = before == null ? InputFrame.NEUTRAL : before.frame();
			if (claimArmed && edge(was, pad.frame(), PadButton.A)) {
				claimArmed = false;
				requests.add(new RouterEvent.ClaimRequest(pad.instanceId()));
			} else if (edge(was, pad.frame(), PadButton.START)) {
				requests.add(new RouterEvent.JoinRequest(pad.instanceId()));
			}
		}

		Map<Integer, InputFrame> frames = new TreeMap<>();
		playerToPad.forEach((player, pad) -> frames.put(player, current.get(pad).frame()));
		previous = current;

		List<RouterEvent> events = new ArrayList<>(lost);
		events.addAll(restored);
		events.addAll(requests);
		return new Update(frames, events);
	}

	/** Binds a currently connected pad to a player and remembers its model for reconnects. */
	public void assign(int instanceId, int player) {
		PadSnapshot pad = previous.get(instanceId);
		if (pad == null) {
			throw new IllegalArgumentException("Controller " + instanceId + " is not connected");
		}
		if (player < 0 || player >= MAX_PLAYERS) {
			throw new IllegalArgumentException("Player must be 0-" + (MAX_PLAYERS - 1) + ", was " + player);
		}
		if (padToPlayer.containsKey(instanceId)) {
			throw new IllegalArgumentException("Controller " + instanceId + " already belongs to player " + padToPlayer.get(instanceId));
		}
		if (playerToPad.containsKey(player)) {
			throw new IllegalArgumentException("Player " + player + " already has a controller");
		}
		bind(instanceId, player);
		lastGuid.put(player, pad.guid());
		lostPlayers.remove(player);
	}

	/** Forgets a player entirely (they left): their pad becomes unassigned and is never restored to them. */
	public void release(int player) {
		unbind(player);
		lastGuid.remove(player);
		lostPlayers.remove(player);
	}

	/** The next A press on an unassigned controller produces a {@link RouterEvent.ClaimRequest}. */
	public void armClaim() {
		claimArmed = true;
	}

	public OptionalInt playerFor(int instanceId) {
		Integer player = padToPlayer.get(instanceId);
		return player == null ? OptionalInt.empty() : OptionalInt.of(player);
	}

	public boolean hasPad(int player) {
		return playerToPad.containsKey(player);
	}

	/** The style of the controller currently bound to this player, if any. */
	public Optional<PadStyle> styleOf(int player) {
		Integer pad = playerToPad.get(player);
		return pad == null ? Optional.empty() : Optional.ofNullable(previous.get(pad)).map(PadSnapshot::style);
	}

	private void bind(int instanceId, int player) {
		padToPlayer.put(instanceId, player);
		playerToPad.put(player, instanceId);
	}

	private void unbind(int player) {
		Integer pad = playerToPad.remove(player);
		if (pad != null) {
			padToPlayer.remove(pad);
		}
	}

	private static boolean edge(InputFrame before, InputFrame now, PadButton button) {
		return now.pressed(button) && !before.pressed(button);
	}
}
