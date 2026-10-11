package dev.hearthside.core.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The profiles a seated player can pick on their lobby card (Spec A section 3.1), ending with New. Profiles another
 * seat holds are left out, checked live: one confirmed on another card while this one is open disappears from it at
 * once. The profile last used on this controller model is pre-selected: profiles are stored least recently used
 * first, so that is the last match. Not thread-safe.
 */
public final class ProfileCarousel {
	public sealed interface Entry {
		record Profile(GuestProfile profile) implements Entry {}

		record New() implements Entry {}
	}

	private final List<Entry> all = new ArrayList<>();
	private final Predicate<UUID> heldElsewhere;
	private int index;

	/**
	 * @param heldElsewhere whether another seat is using a profile (asked each time, so it can change)
	 * @param controllerGuid this controller's model GUID, or null
	 */
	public ProfileCarousel(List<GuestProfile> profiles, Predicate<UUID> heldElsewhere, String controllerGuid) {
		this.heldElsewhere = heldElsewhere;
		for (GuestProfile profile : profiles) {
			all.add(new Entry.Profile(profile));
		}
		all.add(new Entry.New());
		index = -1;
		for (int i = 0; i < all.size(); i++) {
			if (available(i) && all.get(i) instanceof Entry.Profile(GuestProfile p) && controllerGuid != null
					&& Objects.equals(p.preferredControllerGuid(), controllerGuid)) {
				index = i;
			}
		}
		if (index < 0) {
			index = 0;
			settle(1);
		}
	}

	public Entry current() {
		settle(1);
		return all.get(index);
	}

	public void next() {
		settle(1);
		step(1);
	}

	public void previous() {
		settle(1);
		step(-1);
	}

	/** Swaps in a changed copy of a profile (same id), keeping its place; unknown profiles are ignored. */
	public void replace(GuestProfile updated) {
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i) instanceof Entry.Profile(GuestProfile p) && p.id().equals(updated.id())) {
				all.set(i, new Entry.Profile(updated));
			}
		}
	}

	/** Drops a deleted profile; the carousel moves on to what followed it. */
	public void remove(UUID profileId) {
		for (int i = all.size() - 1; i >= 0; i--) {
			if (all.get(i) instanceof Entry.Profile(GuestProfile p) && p.id().equals(profileId)) {
				all.remove(i);
				if (i < index) {
					index--;
				}
			}
		}
		index = Math.min(index, all.size() - 1);
	}

	/** What the carousel offers now, in order. */
	public List<Entry> entries() {
		List<Entry> out = new ArrayList<>();
		for (int i = 0; i < all.size(); i++) {
			if (available(i)) {
				out.add(all.get(i));
			}
		}
		return out;
	}

	private void step(int direction) {
		index = Math.floorMod(index + direction, all.size());
		settle(direction);
	}

	/** Moves off an entry another seat has taken (New, last, is always available). */
	private void settle(int direction) {
		while (!available(index)) {
			index = Math.floorMod(index + direction, all.size());
		}
	}

	private boolean available(int i) {
		return !(all.get(i) instanceof Entry.Profile(GuestProfile p)) || !heldElsewhere.test(p.id());
	}
}
