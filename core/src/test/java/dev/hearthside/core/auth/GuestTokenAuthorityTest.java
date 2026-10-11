package dev.hearthside.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.hearthside.core.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GuestTokenAuthorityTest {
	final MutableClock clock = new MutableClock(Instant.parse("2026-10-10T12:00:00Z"));
	final GuestTokenAuthority auth = new GuestTokenAuthority(new byte[32], clock);
	final UUID guest = UUID.randomUUID();

	@Test
	void validTokenRedeemsOnce() {
		String t = auth.issue(guest);
		assertThat(auth.redeem(t)).contains(guest);
		assertThat(auth.redeem(t)).isEmpty();
	}

	@Test
	void tokenIsValidJustBeforeExpiry() {
		String t = auth.issue(guest);
		clock.advance(Duration.ofSeconds(119));
		assertThat(auth.redeem(t)).contains(guest);
	}

	@Test
	void expiredTokenIsRejected() {
		String t = auth.issue(guest);
		clock.advance(Duration.ofSeconds(120));
		assertThat(auth.redeem(t)).isEmpty();
	}

	@Test
	void eachIssueIsIndependent() {
		String first = auth.issue(guest);
		String second = auth.issue(guest);
		assertThat(first).isNotEqualTo(second);
		assertThat(auth.redeem(first)).contains(guest);
		assertThat(auth.redeem(second)).contains(guest);
	}

	@Test
	void tokenFromAnotherSecretIsRejected() {
		byte[] other = new byte[32];
		other[0] = 1;
		assertThat(auth.redeem(new GuestTokenAuthority(other, clock).issue(guest))).isEmpty();
	}

	@Test
	void tamperedPayloadIsRejected() {
		String t = auth.issue(guest);
		char c = t.charAt(0);
		assertThat(auth.redeem((c == 'A' ? 'B' : 'A') + t.substring(1))).isEmpty();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {".", "abc", "abc.def", "!!!.???", "a.b.c"})
	void garbageIsRejectedWithoutThrowing(String t) {
		assertThat(auth.redeem(t)).isEmpty();
	}

	@Test
	void shortSecretIsRefused() {
		assertThatThrownBy(() -> new GuestTokenAuthority(new byte[16], clock))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
