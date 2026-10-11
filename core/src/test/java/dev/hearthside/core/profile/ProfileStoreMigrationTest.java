package dev.hearthside.core.profile;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.input.ControlScheme;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileStoreMigrationTest {
	@TempDir
	Path dir;

	private static final String V2 = """
			{"schemaVersion":2,"profiles":[
			  {"id":"0b5f2c3e-8a7d-4c1e-9f00-123456789abc","name":"Alex","skin":{"kind":"DEFAULT","value":"alex"},
			   "preferredControllerGuid":"guid-1","controls":{"lookSensitivity":1.5},"someFutureField":42},
			  {"id":"1b5f2c3e-8a7d-4c1e-9f00-123456789abc","name":"Sam","skin":{"kind":"DEFAULT","value":"steve"},
			   "preferredControllerGuid":null,"controls":{}}
			],"hostControls":{"invertY":true},"anotherUnknown":"x"}
			""";

	@Test
	void v3RoundTripsEveryField() throws Exception {
		Path file = dir.resolve("profiles.json");
		PersonalSettings settings = new PersonalSettings(90, 3, false, true, true, "Headset (Xbox Controller)", 0.5f);
		GuestProfile guest = new GuestProfile(UUID.randomUUID(), "Jordan", SkinRef.borrowed("Notch", false), "guid-9",
				ControlScheme.BEDROCK, 0x9a5fd0, settings);
		HostProfile host = new HostProfile(ControlScheme.BEDROCK, new PersonalSettings(80, 2, true, false, false, null, 0.8f));
		new ProfileStore(file).saveFile(new ProfileFile(List.of(guest), host));

		ProfileFile loaded = new ProfileStore(file).loadFile();
		assertThat(loaded.profiles()).containsExactly(guest);
		assertThat(loaded.host()).isEqualTo(host);
		assertThat(Files.readString(file)).contains("\"schemaVersion\": 3");
	}

	@Test
	void savingGuestsKeepsTheHostsSettings() throws Exception {
		Path file = dir.resolve("profiles.json");
		ProfileStore store = new ProfileStore(file);
		HostProfile host = new HostProfile(ControlScheme.BEDROCK, new PersonalSettings(95, 2, false, true, false, null, 0.3f));
		store.saveFile(new ProfileFile(List.of(), host));
		store.save(List.of(new GuestProfile(UUID.randomUUID(), "Sam", SkinRef.defaultSkin("steve"), null)));
		assertThat(store.loadFile().host()).isEqualTo(host);
	}

	@Test
	void v2FileMigratesWithDefaults() throws Exception {
		Path file = dir.resolve("profiles.json");
		Files.writeString(file, V2);
		ProfileFile loaded = new ProfileStore(file).loadFile();
		assertThat(loaded.profiles()).extracting(GuestProfile::name).containsExactly("Alex", "Sam");
		assertThat(loaded.profiles()).extracting(GuestProfile::color)
				.containsExactly(PlayerColors.DEFAULTS[1], PlayerColors.DEFAULTS[2]); // blue is player 1's
		assertThat(loaded.profiles()).extracting(GuestProfile::settings)
				.containsOnly(PersonalSettings.DEFAULTS);
		assertThat(loaded.profiles().getFirst().skin().slim()).isTrue();
		assertThat(loaded.profiles().getFirst().controls().lookSensitivity()).isEqualTo(1.5f);
		assertThat(loaded.host().controls().invertY()).isTrue();
		assertThat(loaded.host().settings()).isEqualTo(PersonalSettings.DEFAULTS);
	}

	@Test
	void v2FileWithUnknownFieldsStillLoads() throws Exception {
		Path file = dir.resolve("profiles.json");
		Files.writeString(file, V2);
		assertThat(new ProfileStore(file).load()).hasSize(2);
		assertThat(Files.exists(file)).as("not moved aside as corrupt").isTrue();
	}

	@Test
	void missingSettingsFieldsTakeDefaults() throws Exception {
		Path file = dir.resolve("profiles.json");
		Files.writeString(file, """
				{"schemaVersion":3,"profiles":[
				  {"id":"0b5f2c3e-8a7d-4c1e-9f00-123456789abc","name":"Alex","skin":{"kind":"DEFAULT","value":"alex"},
				   "preferredControllerGuid":null,"controls":{},"color":4158672,"settings":{"fov":95}}
				],"host":{"controls":{},"settings":{}}}
				""");
		GuestProfile alex = new ProfileStore(file).load().getFirst();
		assertThat(alex.settings().fov()).isEqualTo(95);
		assertThat(alex.settings().volume()).isEqualTo(PersonalSettings.DEFAULTS.volume());
		assertThat(alex.settings().buttonHints()).isTrue();
		assertThat(alex.color()).isEqualTo(4158672);
	}
}
