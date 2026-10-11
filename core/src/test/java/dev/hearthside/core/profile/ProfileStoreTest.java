package dev.hearthside.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProfileStoreTest {
	@TempDir
	Path dir;

	Path file;
	ProfileStore store;

	@BeforeEach
	void setUp() {
		file = dir.resolve("profiles.json");
		store = new ProfileStore(file);
	}

	@Test
	void missingFileLoadsEmpty() throws Exception {
		assertThat(store.load()).isEmpty();
	}

	@Test
	void roundTripsProfiles() throws Exception {
		var a = new GuestProfile(UUID.randomUUID(), "Alice", SkinRef.defaultSkin("alex"), "03000000de280000ff11000001000000");
		var b = new GuestProfile(UUID.randomUUID(), "Bob_2", SkinRef.file("C:\\Users\\Some One\\skin.png"), null);
		store.save(List.of(a, b));
		assertThat(new ProfileStore(file).load()).containsExactly(a, b);
	}

	@Test
	void corruptFileIsBackedUpAndYieldsEmpty() throws Exception {
		Files.writeString(file, "{ not json");
		assertThat(store.load()).isEmpty();
		assertThat(file).doesNotExist();
		try (var s = Files.list(dir)) {
			assertThat(s.map(p -> p.getFileName().toString()))
					.singleElement().asString().startsWith("profiles.json.corrupt-");
		}
	}

	@Test
	void invalidProfileNameCountsAsCorrupt() throws Exception {
		Files.writeString(file, "{\"schemaVersion\":1,\"profiles\":[{\"id\":\"" + UUID.randomUUID()
				+ "\",\"name\":\"x\",\"skin\":{\"kind\":\"DEFAULT\",\"value\":\"steve\"}}]}");
		assertThat(store.load()).isEmpty();
		assertThat(file).doesNotExist();
	}

	@Test
	void newerSchemaIsRefusedAndFileUntouched() throws Exception {
		String newer = "{\"schemaVersion\":2,\"profiles\":[]}";
		Files.writeString(file, newer);
		assertThatThrownBy(store::load).isInstanceOf(UnsupportedSchemaException.class);
		assertThat(Files.readString(file)).isEqualTo(newer);
	}

	@Test
	void rejectsDuplicateNamesIgnoringCase() {
		var a = new GuestProfile(UUID.randomUUID(), "Alice", SkinRef.defaultSkin("alex"), null);
		var b = new GuestProfile(UUID.randomUUID(), "ALICE", SkinRef.defaultSkin("steve"), null);
		assertThatThrownBy(() -> store.save(List.of(a, b))).isInstanceOf(IllegalArgumentException.class);
		assertThat(file).doesNotExist();
	}

	@Test
	void rejectsDuplicateIds() {
		UUID id = UUID.randomUUID();
		var a = new GuestProfile(id, "Alice", SkinRef.defaultSkin("alex"), null);
		var b = new GuestProfile(id, "Bob", SkinRef.defaultSkin("steve"), null);
		assertThatThrownBy(() -> store.save(List.of(a, b))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void createsParentDirectories() throws Exception {
		var nested = new ProfileStore(dir.resolve("hearthside/profiles.json"));
		nested.save(List.of());
		assertThat(dir.resolve("hearthside/profiles.json")).exists();
	}

	@Test
	void undecodableBytesCountAsCorrupt() throws Exception {
		Files.write(file, new byte[] {(byte) 0xFF, (byte) 0xFE, '{'});
		assertThat(store.load()).isEmpty();
		assertThat(file).doesNotExist();
	}

	@Test
	void duplicateNamesInFileCountAsCorrupt() throws Exception {
		Files.writeString(file, "{\"schemaVersion\":1,\"profiles\":["
				+ "{\"id\":\"" + UUID.randomUUID() + "\",\"name\":\"Alice\",\"skin\":{\"kind\":\"DEFAULT\",\"value\":\"alex\"}},"
				+ "{\"id\":\"" + UUID.randomUUID() + "\",\"name\":\"alice\",\"skin\":{\"kind\":\"DEFAULT\",\"value\":\"alex\"}}]}");
		assertThat(store.load()).isEmpty();
		assertThat(file).doesNotExist();
	}

	@Test
	void saveRefusesToOverwriteNewerSchema() throws Exception {
		String newer = "{\"schemaVersion\":2,\"profiles\":[]}";
		Files.writeString(file, newer);
		var a = new GuestProfile(UUID.randomUUID(), "Alice", SkinRef.defaultSkin("alex"), null);
		assertThatThrownBy(() -> store.save(List.of(a))).isInstanceOf(UnsupportedSchemaException.class);
		assertThat(Files.readString(file)).isEqualTo(newer);
	}

	@Test
	void saveOverwritesCorruptLeftoversOfCurrentSchema() throws Exception {
		Files.writeString(file, "{\"schemaVersion\":1,\"profiles\":[]}");
		var a = new GuestProfile(UUID.randomUUID(), "Alice", SkinRef.defaultSkin("alex"), null);
		store.save(List.of(a));
		assertThat(store.load()).containsExactly(a);
	}
}
