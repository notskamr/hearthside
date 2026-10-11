package dev.hearthside.core.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The profiles a seated player can pick on their lobby card (Spec A section 3.1), ending with New. Profiles another
 * seat holds are left out. The profile last used on this controller model is pre-selected. Not thread-safe.
 */
public final class ProfileCarousel {
	public sealed interface Entry {
		record Profile(GuestProfile profile) implements Entry {}

		record New() implements Entry {}
	}

	private final List<Entry> entries = new ArrayList<>();
	private int index;

	/**
	 * @param heldElsewhere ids of profiles other seats are using
	 * @param controllerGuid this controller's model GUID, or null
	 */
	public ProfileCarousel(List<GuestProfile> profiles, Set<UUID> heldElsewhere, String controllerGuid) {
		for (GuestProfile profile : profiles) {
			if (!heldElsewhere.contains(profile.id())) {
				entries.add(new Entry.Profile(profile));
			}
		}
		entries.add(new Entry.New());
		for (int i = 0; i < entries.size(); i++) {
			if (entries.get(i) instanceof Entry.Profile(GuestProfile p) && controllerGuid != null
					&& Objects.equals(p.preferredControllerGuid(), controllerGuid)) {
				index = i;
				break;
			}
		}
	}

	public Entry current() {
		return entries.get(index);
	}

	public void next() {
		index = Math.floorMod(index + 1, entries.size());
	}

	public void previous() {
		index = Math.floorMod(index - 1, entries.size());
	}

	public int index() {
		return index;
	}

	public List<Entry> entries() {
		return List.copyOf(entries);
	}
}
