package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.auth.GuestTokenAuthority;
import dev.hearthside.core.profile.GuestProfile;
import dev.hearthside.core.profile.GuestUuids;
import dev.hearthside.core.profile.SkinRef;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuestAdmissionTest {
	final GuestRoster roster = new GuestRoster("Steve");
	final GuestProfile alice = new GuestProfile(UUID.randomUUID(), "Alice", SkinRef.defaultSkin("alex"), null);
	final GuestTokenAuthority tokens = GuestTokenAuthority.withRandomSecret(Clock.systemUTC());

	GuestAdmissionTest() {
		roster.register(1, alice);
	}

	@Test
	void loopbackExpectedGuestUsesGuestPath() {
		assertThat(GuestAdmission.pathFor("alice", true, roster)).isEqualTo(GuestAdmission.Path.GUEST);
	}

	@Test
	void nonLoopbackNeverUsesGuestPath() {
		assertThat(GuestAdmission.pathFor("Alice", false, roster)).isEqualTo(GuestAdmission.Path.VANILLA);
	}

	@Test
	void unknownNameUsesVanillaPath() {
		assertThat(GuestAdmission.pathFor("Mallory", true, roster)).isEqualTo(GuestAdmission.Path.VANILLA);
	}

	@Test
	void tokenForTheExpectedGuestMatches() {
		UUID guest = GuestUuids.forProfile(alice.id());
		assertThat(GuestAdmission.tokenMatches(tokens.issue(guest), guest, tokens)).isTrue();
	}

	@Test
	void tokenForAnotherGuestDoesNotMatch() {
		UUID guest = GuestUuids.forProfile(alice.id());
		String bobsToken = tokens.issue(GuestUuids.forProfile(UUID.randomUUID()));
		assertThat(GuestAdmission.tokenMatches(bobsToken, guest, tokens)).isFalse();
	}

	@Test
	void garbageTokenDoesNotMatch() {
		UUID guest = GuestUuids.forProfile(alice.id());
		assertThat(GuestAdmission.tokenMatches(null, guest, tokens)).isFalse();
		assertThat(GuestAdmission.tokenMatches("x.y", guest, tokens)).isFalse();
	}
}
