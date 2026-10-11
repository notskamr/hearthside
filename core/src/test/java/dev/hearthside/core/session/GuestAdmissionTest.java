package dev.hearthside.core.session;

import static dev.hearthside.core.session.GuestAdmission.Decision.GUEST;
import static dev.hearthside.core.session.GuestAdmission.Decision.REFUSE;
import static dev.hearthside.core.session.GuestAdmission.Decision.VANILLA;
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

	/** A first Hello over loopback TCP to a world that is not open to LAN, unless overridden. */
	static GuestAdmission.LoginAttempt attempt(String name) {
		return new GuestAdmission.LoginAttempt(name, true, false, true, false);
	}

	@Test
	void loopbackExpectedGuestUsesGuestPath() {
		assertThat(GuestAdmission.decide(attempt("alice"), roster)).isEqualTo(GUEST);
	}

	@Test
	void secondHelloNeverUsesGuestPath() {
		// Vanilla's own state check must see a repeated Hello, so it can disconnect the client.
		var repeated = new GuestAdmission.LoginAttempt("Alice", true, false, false, false);
		assertThat(GuestAdmission.decide(repeated, roster)).isEqualTo(VANILLA);
	}

	@Test
	void inMemoryConnectionIsTheHostItself() {
		var host = new GuestAdmission.LoginAttempt("Steve", false, true, true, false);
		assertThat(GuestAdmission.decide(host, roster)).isEqualTo(VANILLA);
	}

	@Test
	void hostNameOverTheNetworkIsRefused() {
		assertThat(GuestAdmission.decide(attempt("STEVE"), roster)).isEqualTo(REFUSE);
		var overLan = new GuestAdmission.LoginAttempt("steve", false, false, true, true);
		assertThat(GuestAdmission.decide(overLan, roster)).isEqualTo(REFUSE);
	}

	@Test
	void worldNotOpenToLanRefusesEveryoneButGuests() {
		assertThat(GuestAdmission.decide(attempt("Mallory"), roster)).isEqualTo(REFUSE);
	}

	@Test
	void worldOpenToLanLeavesOthersToVanilla() {
		var lanFriend = new GuestAdmission.LoginAttempt("Mallory", false, false, true, true);
		assertThat(GuestAdmission.decide(lanFriend, roster)).isEqualTo(VANILLA);
	}

	@Test
	void nonLoopbackNeverUsesGuestPath() {
		var lanImpostor = new GuestAdmission.LoginAttempt("Alice", false, false, true, true);
		assertThat(GuestAdmission.decide(lanImpostor, roster)).isEqualTo(VANILLA);
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
