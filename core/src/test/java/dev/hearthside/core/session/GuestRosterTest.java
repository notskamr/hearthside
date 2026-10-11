package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hearthside.core.profile.GuestProfile;
import dev.hearthside.core.profile.GuestUuids;
import dev.hearthside.core.profile.SkinRef;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GuestRosterTest {
	final GuestRoster roster = new GuestRoster("Steve");
	final GuestProfile alice = profile("Alice");
	final GuestProfile bob = profile("Bob");

	static GuestProfile profile(String name) {
		return new GuestProfile(UUID.randomUUID(), name, SkinRef.defaultSkin("steve"), null);
	}

	@Test
	void registeredGuestIsExpectedByNameIgnoringCase() {
		roster.register(1, alice);
		assertThat(roster.expectedUuidForName("ALICE")).contains(GuestUuids.forProfile(alice.id()));
		assertThat(roster.byIndex(1)).contains(alice);
	}

	@Test
	void guestNamedLikeHostIsRefused() {
		assertThatThrownBy(() -> roster.register(1, profile("steve"))).isInstanceOf(IllegalArgumentException.class);
		assertThat(roster.expectedUuidForName("steve")).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 4, -1})
	void indexOutsideGuestRangeIsRefused(int index) {
		assertThatThrownBy(() -> roster.register(index, alice)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void takenIndexIsRefused() {
		roster.register(1, alice);
		assertThatThrownBy(() -> roster.register(1, bob)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void duplicateNameOrIdAcrossSlotsIsRefused() {
		roster.register(1, alice);
		assertThatThrownBy(() -> roster.register(2, profile("alice"))).isInstanceOf(IllegalArgumentException.class);
		var sameId = new GuestProfile(alice.id(), "Carol", SkinRef.defaultSkin("alex"), null);
		assertThatThrownBy(() -> roster.register(2, sameId)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void freeIndexIsLowestAvailable() {
		roster.register(1, alice);
		roster.register(3, bob);
		assertThat(roster.freeIndex()).hasValue(2);
		roster.register(2, profile("Carol"));
		assertThat(roster.freeIndex()).isEmpty();
	}

	@Test
	void removedGuestIsNoLongerExpected() {
		roster.register(1, alice);
		assertThat(roster.remove(1)).contains(alice);
		assertThat(roster.expectedUuidForName("Alice")).isEmpty();
		assertThat(roster.remove(1)).isEmpty();
	}
}
