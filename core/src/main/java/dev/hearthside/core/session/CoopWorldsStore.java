package dev.hearthside.core.session;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The worlds played in co-op, in {@code .minecraft/hearthside/coop-worlds.json} (schema 1). A damaged file never
 * crashes the game: it is renamed to {@code <name>.corrupt-<epochMillis>} and treated as empty.
 */
public final class CoopWorldsStore {
	private static final int SCHEMA_VERSION = 1;
	/** At most this many names are remembered per world. */
	private static final int MAX_PLAYERS = 8;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private record FileDto(int schemaVersion, List<WorldDto> worlds) {}

	private record WorldDto(String levelId, long lastPlayed, List<String> players) {}

	private final Path file;

	public CoopWorldsStore(Path file) {
		this.file = file;
	}

	/** The co-op worlds, newest first. */
	public List<CoopWorld> load() throws IOException {
		if (!Files.exists(file)) {
			return List.of();
		}
		try {
			FileDto dto = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), FileDto.class);
			List<CoopWorld> worlds = new ArrayList<>();
			if (dto != null && dto.worlds() != null) {
				for (WorldDto w : dto.worlds()) {
					if (w != null && w.levelId() != null) {
						worlds.add(new CoopWorld(w.levelId(), w.lastPlayed(), w.players() == null ? List.of() : w.players()));
					}
				}
			}
			worlds.sort(Comparator.comparingLong(CoopWorld::lastPlayed).reversed());
			return worlds;
		} catch (JsonParseException | IllegalStateException | NullPointerException e) {
			Path backup = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis());
			Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
			return List.of();
		}
	}

	/** Records that these players played this world now; names already recorded keep their place. */
	public void played(String levelId, long when, Collection<String> players) throws IOException {
		List<CoopWorld> worlds = new ArrayList<>(load());
		Set<String> names = new LinkedHashSet<>();
		long lastPlayed = when;
		for (CoopWorld world : worlds) {
			if (world.levelId().equals(levelId)) {
				names.addAll(world.players());
				lastPlayed = Math.max(when, world.lastPlayed());
			}
		}
		worlds.removeIf(world -> world.levelId().equals(levelId));
		names.addAll(players);
		List<String> kept = names.stream().limit(MAX_PLAYERS).toList();
		worlds.add(new CoopWorld(levelId, lastPlayed, kept));
		List<WorldDto> dtos = worlds.stream().map(w -> new WorldDto(w.levelId(), w.lastPlayed(), w.players())).toList();
		Files.createDirectories(file.toAbsolutePath().getParent());
		Path temp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(temp, GSON.toJson(new FileDto(SCHEMA_VERSION, dtos)), StandardCharsets.UTF_8);
		Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
