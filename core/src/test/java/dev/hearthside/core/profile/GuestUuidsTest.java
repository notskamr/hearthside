package dev.hearthside.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import org.junit.jupiter.api.Test;

class GuestUuidsTest {
	@Test
	void isDeterministic() {
		UUID profile = UUID.fromString("0b5f2c3e-8a7d-4c1e-9f00-123456789abc");
		assertThat(GuestUuids.forProfile(profile)).isEqualTo(GuestUuids.forProfile(profile));
	}

	@Test
	void isVersion3SoNeverCollidesWithMojangVersion4() {
		assertThat(GuestUuids.forProfile(UUID.randomUUID()).version()).isEqualTo(3);
	}

	@Test
	void differsFromVanillaOfflineUuid() {
		UUID profile = UUID.randomUUID();
		UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + profile).getBytes(StandardCharsets.UTF_8));
		assertThat(GuestUuids.forProfile(profile)).isNotEqualTo(offline);
	}

	@Property
	void distinctProfilesGetDistinctUuids(
			@ForAll long aHigh, @ForAll long aLow, @ForAll long bHigh, @ForAll long bLow) {
		UUID a = new UUID(aHigh, aLow);
		UUID b = new UUID(bHigh, bLow);
		Assume.that(!a.equals(b));
		assertThat(GuestUuids.forProfile(a)).isNotEqualTo(GuestUuids.forProfile(b));
	}
}
