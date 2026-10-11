package dev.hearthside.core.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import dev.hearthside.core.input.ControlScheme;
import dev.hearthside.core.input.ControlSchemeJson;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Loads and saves guest profiles and the host's controls as JSON.
 *
 * <p>A damaged file never crashes the game: it is renamed to {@code <name>.corrupt-<epochMillis>}
 * and nothing is returned. A damaged {@code controls} block only falls back to Bedrock controls. A
 * file from a newer schema is refused and never overwritten. Version-1 files load with Bedrock
 * controls and are written back as version 2.
 */
public final class ProfileStore {
	public static final int SCHEMA_VERSION = 2;

	private static final System.Logger LOG = System.getLogger(ProfileStore.class.getName());
	private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

	private final Path file;

	public ProfileStore(Path file) {
		this.file = file;
	}

	public List<GuestProfile> load() throws IOException {
		return loadFile().profiles();
	}

	/** Replaces the guest profiles, keeping the stored host controls. */
	public void save(List<GuestProfile> profiles) throws IOException {
		ControlScheme host = Files.exists(file) ? loadFile().hostControls() : ControlScheme.BEDROCK;
		saveFile(new ProfileFile(profiles, host));
	}

	public ProfileFile loadFile() throws IOException {
		if (!Files.exists(file)) {
			return ProfileFile.EMPTY;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			refuseIfNewer(root);
			FileDto dto = GSON.fromJson(root, FileDto.class);
			List<GuestProfile> profiles = new ArrayList<>();
			for (ProfileDto p : dto.profiles()) {
				profiles.add(p.toProfile());
			}
			requireUnique(profiles);
			return new ProfileFile(profiles, ControlSchemeJson.fromTree(dto.hostControls()));
		} catch (CharacterCodingException | JsonParseException | IllegalStateException | IllegalArgumentException
				| NullPointerException | UnsupportedOperationException e) {
			Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
			Files.move(file, backup);
			LOG.log(Level.WARNING, "Profiles file " + file + " was unreadable; moved it to " + backup, e);
			return ProfileFile.EMPTY;
		}
	}

	/**
	 * Replaces the file atomically. Refuses with {@link UnsupportedSchemaException} if the existing
	 * file is from a newer schema, so a refused file can never be overwritten by mistake.
	 */
	public void saveFile(ProfileFile contents) throws IOException {
		requireUnique(contents.profiles());
		refuseIfExistingFileIsNewer();
		List<ProfileDto> dtos = contents.profiles().stream().map(ProfileDto::from).toList();
		FileDto dto = new FileDto(SCHEMA_VERSION, dtos, ControlSchemeJson.toTree(contents.hostControls()));
		byte[] json = GSON.toJson(dto).getBytes(StandardCharsets.UTF_8);

		Path parent = file.toAbsolutePath().getParent();
		Files.createDirectories(parent);
		Path tmp = Files.createTempFile(parent, file.getFileName().toString(), ".tmp");
		try {
			try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
				ByteBuffer buffer = ByteBuffer.wrap(json);
				while (buffer.hasRemaining()) {
					channel.write(buffer);
				}
				// Reach the disk before the rename, or a crash could leave a renamed but empty file.
				channel.force(true);
			}
			Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}

	private void refuseIfExistingFileIsNewer() throws IOException {
		if (!Files.exists(file)) {
			return;
		}
		try {
			refuseIfNewer(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
		} catch (CharacterCodingException | JsonParseException | IllegalStateException | NullPointerException
				| UnsupportedOperationException e) {
			// Unreadable: not a newer schema we could lose, so it may be replaced.
		}
	}

	private void refuseIfNewer(JsonObject root) throws UnsupportedSchemaException {
		int schema = root.get("schemaVersion").getAsInt();
		if (schema > SCHEMA_VERSION) {
			throw new UnsupportedSchemaException(
					file + " has schemaVersion " + schema + "; this Hearthside supports up to " + SCHEMA_VERSION);
		}
	}

	private static void requireUnique(List<GuestProfile> profiles) {
		Set<UUID> ids = new HashSet<>();
		Set<String> names = new HashSet<>();
		for (GuestProfile p : profiles) {
			if (!ids.add(p.id())) {
				throw new IllegalArgumentException("Duplicate profile id " + p.id());
			}
			if (!names.add(p.name().toLowerCase(Locale.ROOT))) {
				throw new IllegalArgumentException("Duplicate profile name " + p.name());
			}
		}
	}

	private record FileDto(int schemaVersion, List<ProfileDto> profiles, JsonElement hostControls) {}

	private record SkinDto(String kind, String value) {}

	private record ProfileDto(String id, String name, SkinDto skin, String preferredControllerGuid, JsonElement controls) {
		static ProfileDto from(GuestProfile p) {
			return new ProfileDto(p.id().toString(), p.name(), new SkinDto(p.skin().kind().name(), p.skin().value()),
					p.preferredControllerGuid(), ControlSchemeJson.toTree(p.controls()));
		}

		GuestProfile toProfile() {
			return new GuestProfile(UUID.fromString(id), name, new SkinRef(SkinRef.Kind.valueOf(skin.kind()), skin.value()),
					preferredControllerGuid, ControlSchemeJson.fromTree(controls));
		}
	}
}
