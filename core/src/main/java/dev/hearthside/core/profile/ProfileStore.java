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
	public static final int SCHEMA_VERSION = 3;

	private static final System.Logger LOG = System.getLogger(ProfileStore.class.getName());
	private static final Gson GSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

	private final Path file;

	public ProfileStore(Path file) {
		this.file = file;
	}

	public List<GuestProfile> load() throws IOException {
		return loadFile().profiles();
	}

	/** Replaces the guest profiles, keeping the stored host profile. */
	public void save(List<GuestProfile> profiles) throws IOException {
		HostProfile host = Files.exists(file) ? loadFile().host() : HostProfile.DEFAULTS;
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
				// v2 files have no colours: hand out the defaults in order.
				profiles.add(p.toProfile(PlayerColors.DEFAULTS[profiles.size() % PlayerColors.DEFAULTS.length]));
			}
			requireUnique(profiles);
			HostProfile host = dto.host() != null
					? dto.host().toHost()
					: new HostProfile(ControlSchemeJson.fromTree(dto.hostControls()), PersonalSettings.DEFAULTS); // v2
			return new ProfileFile(profiles, host);
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
		WrittenFileDto dto = new WrittenFileDto(SCHEMA_VERSION, dtos, HostDto.from(contents.host()));
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

	/** What is read: v3 has {@code host}; v2 had {@code hostControls}. */
	private record FileDto(int schemaVersion, List<ProfileDto> profiles, JsonElement hostControls, HostDto host) {}

	/** What is written (always the current schema). */
	private record WrittenFileDto(int schemaVersion, List<ProfileDto> profiles, HostDto host) {}

	private record HostDto(JsonElement controls, SettingsDto settings) {
		static HostDto from(HostProfile h) {
			return new HostDto(ControlSchemeJson.toTree(h.controls()), SettingsDto.from(h.settings()));
		}

		HostProfile toHost() {
			return new HostProfile(ControlSchemeJson.fromTree(controls), SettingsDto.toSettings(settings));
		}
	}

	/** Boxed so a missing member reads as null and takes the default. */
	private record SettingsDto(Integer fov, Integer guiScale, Boolean buttonHints, Boolean reduceMotion, Boolean subtitles,
			String audioDevice, Float volume) {
		static SettingsDto from(PersonalSettings s) {
			return new SettingsDto(s.fov(), s.guiScale(), s.buttonHints(), s.reduceMotion(), s.subtitles(), s.audioDevice(),
					s.volume());
		}

		static PersonalSettings toSettings(SettingsDto dto) {
			PersonalSettings d = PersonalSettings.DEFAULTS;
			if (dto == null) {
				return d;
			}
			return new PersonalSettings(or(dto.fov, d.fov()), or(dto.guiScale, d.guiScale()), or(dto.buttonHints, d.buttonHints()),
					or(dto.reduceMotion, d.reduceMotion()), or(dto.subtitles, d.subtitles()), dto.audioDevice,
					or(dto.volume, d.volume()));
		}

		private static <T> T or(T value, T fallback) {
			return value != null ? value : fallback;
		}
	}

	private record SkinDto(String kind, String value, Boolean slim) {}

	private record ProfileDto(String id, String name, SkinDto skin, String preferredControllerGuid, JsonElement controls,
			Integer color, SettingsDto settings) {
		static ProfileDto from(GuestProfile p) {
			return new ProfileDto(p.id().toString(), p.name(),
					new SkinDto(p.skin().kind().name(), p.skin().value(), p.skin().slim()), p.preferredControllerGuid(),
					ControlSchemeJson.toTree(p.controls()), p.color(), SettingsDto.from(p.settings()));
		}

		GuestProfile toProfile(int defaultColor) {
			SkinRef.Kind kind = SkinRef.Kind.valueOf(skin.kind());
			SkinRef skinRef = skin.slim() == null ? new SkinRef(kind, skin.value()) : new SkinRef(kind, skin.value(), skin.slim());
			return new GuestProfile(UUID.fromString(id), name, skinRef, preferredControllerGuid,
					ControlSchemeJson.fromTree(controls), color != null ? color : defaultColor, SettingsDto.toSettings(settings));
		}
	}
}
