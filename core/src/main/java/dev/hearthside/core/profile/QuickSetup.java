package dev.hearthside.core.profile;

import dev.hearthside.core.input.ControlScheme;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Making a new profile in about 20 seconds (Spec A section 5.2): a name (with a suggestion ready), one of Minecraft's
 * built-in skins, and a colour (the first free one pre-picked). Not thread-safe.
 */
public final class QuickSetup {
	public enum Step { NAME, SKIN, COLOR, DONE }

	/** Cosy names offered first, before "Player N". */
	public static final List<String> SUGGESTIONS = List.of("Ember", "Hazel", "Cinder", "Moss", "Birch", "Clover",
			"Pebble", "Fern", "Maple", "Sparrow", "Juniper", "Willow");

	/** Minecraft's built-in skins: the nine wide (classic arms), then the same nine slim. */
	public static final List<SkinRef> SKINS;

	static {
		List<String> names = List.of("steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri");
		List<SkinRef> skins = new ArrayList<>();
		for (boolean slim : new boolean[] {false, true}) {
			for (String name : names) {
				skins.add(new SkinRef(SkinRef.Kind.DEFAULT, name, slim));
			}
		}
		SKINS = List.copyOf(skins);
	}

	private final Set<String> takenNames;
	private final Set<Integer> takenColors;
	private final String suggestedName;
	private Step step = Step.NAME;
	private int skin;
	private int colorIndex;

	/**
	 * @param takenNames names of existing profiles
	 * @param hostName player 1's name
	 * @param takenColors colours other players in the party are using
	 * @param seat the seat being set up (0 = player 1)
	 */
	public QuickSetup(Collection<String> takenNames, String hostName, Collection<Integer> takenColors, int seat) {
		this.takenNames = takenNames.stream().map(n -> n.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
		if (hostName != null) {
			this.takenNames.add(hostName.toLowerCase(Locale.ROOT));
		}
		this.takenColors = Set.copyOf(takenColors);
		this.suggestedName = suggest(seat);
		this.skin = Math.floorMod(seat, SKINS.size());
		int first = PlayerColors.firstFree(takenColors);
		for (int i = 0; i < PlayerColors.DEFAULTS.length; i++) {
			if (PlayerColors.DEFAULTS[i] == first) {
				colorIndex = i;
			}
		}
	}

	public Step step() {
		return step;
	}

	public void next() {
		if (step != Step.DONE) {
			step = Step.values()[step.ordinal() + 1];
		}
	}

	/** One step back; false at the first step (the caller leaves quick setup). */
	public boolean back() {
		if (step == Step.NAME) {
			return false;
		}
		step = Step.values()[step.ordinal() - 1];
		return true;
	}

	public String suggestedName() {
		return suggestedName;
	}

	/** Whether a new profile may use this name: valid, and not another profile's or player 1's. */
	public boolean nameAvailable(String name) {
		return GuestNames.isValid(name) && !takenNames.contains(name.toLowerCase(Locale.ROOT));
	}

	public SkinRef skin() {
		return SKINS.get(skin);
	}

	public int skinIndex() {
		return skin;
	}

	public void nextSkin() {
		skin = Math.floorMod(skin + 1, SKINS.size());
	}

	public void previousSkin() {
		skin = Math.floorMod(skin - 1, SKINS.size());
	}

	public int color() {
		return PlayerColors.DEFAULTS[colorIndex];
	}

	/** The next colour nobody else has (all of them if every one is taken). */
	public void nextColor() {
		cycleColor(1);
	}

	public void previousColor() {
		cycleColor(-1);
	}

	private void cycleColor(int direction) {
		int n = PlayerColors.DEFAULTS.length;
		for (int step = 1; step <= n; step++) {
			int candidate = Math.floorMod(colorIndex + direction * step, n);
			if (!takenColors.contains(PlayerColors.DEFAULTS[candidate]) || step == n) {
				colorIndex = candidate;
				return;
			}
		}
	}

	public GuestProfile build(UUID id, String name, String controllerGuid) {
		return new GuestProfile(id, name, skin(), controllerGuid, ControlScheme.BEDROCK, color(),
				PersonalSettings.DEFAULTS);
	}

	private String suggest(int seat) {
		for (String name : SUGGESTIONS) {
			if (nameAvailable(name)) {
				return name;
			}
		}
		String base = "Player" + (seat + 1);
		if (nameAvailable(base)) {
			return base;
		}
		for (int n = 2; ; n++) {
			if (nameAvailable(base + "_" + n)) {
				return base + "_" + n;
			}
		}
	}
}
