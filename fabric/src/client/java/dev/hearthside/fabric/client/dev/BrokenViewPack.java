package dev.hearthside.fabric.client.dev;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * E2E step V8's resource pack: it overrides {@code hearthside:core/view}'s fragment shader with GLSL that can't compile,
 * as a badly made pack would (final review M2). Written into the game's {@code resourcepacks} folder, selected for one
 * reload and deselected after, without touching the saved options.
 */
public final class BrokenViewPack {
	static final String FOLDER = "hearthside-broken-view";
	static final String ID = "file/" + FOLDER;
	private static final String SHADERS = "assets/hearthside/shaders/core/";

	private BrokenViewPack() {}

	/** Writes the pack (a {@code pack.mcmeta}, our own {@code view.vsh}, a broken {@code view.fsh}) under {@code gameDir}. */
	public static void write(Path gameDir) throws IOException {
		Path pack = gameDir.resolve("resourcepacks").resolve(FOLDER);
		Path shaders = pack.resolve(SHADERS);
		Files.createDirectories(shaders);
		// Minecraft 26.3's pack format: min and max as [major, minor].
		String format = "[" + SharedConstants.RESOURCE_PACK_FORMAT_MAJOR + ", " + SharedConstants.RESOURCE_PACK_FORMAT_MINOR + "]";
		Files.writeString(pack.resolve("pack.mcmeta"), "{\"pack\": {\"description\": \"Hearthside E2E: a view shader that"
				+ " doesn't compile\", \"min_format\": " + format + ", \"max_format\": " + format + "}}\n", StandardCharsets.UTF_8);
		try (InputStream vertex = BrokenViewPack.class.getResourceAsStream("/" + SHADERS + "view.vsh")) {
			if (vertex == null) {
				throw new IOException("Hearthside's own view.vsh is missing");
			}
			Files.write(shaders.resolve("view.vsh"), vertex.readAllBytes());
		}
		Files.writeString(shaders.resolve("view.fsh"), """
				#version 330

				layout(location = 0) out vec4 fragColor;

				void main() {
				    fragColor = this is not GLSL;
				}
				""", StandardCharsets.UTF_8);
	}

	/** Adds the pack on top of the selected ones ({@code on}) or removes it, and reloads. Render thread. */
	public static CompletableFuture<Void> select(Minecraft minecraft, boolean on) {
		PackRepository repository = minecraft.getResourcePackRepository();
		repository.reload();
		List<String> ids = new ArrayList<>(repository.getSelectedIds());
		ids.remove(ID);
		if (on) {
			if (!repository.getAvailableIds().contains(ID)) {
				throw new UncheckedIOException(new IOException("Minecraft doesn't list the pack " + ID));
			}
			ids.add(ID);
		}
		repository.setSelected(ids);
		return minecraft.reloadResourcePacks();
	}

	/** Puts back the packs selected before ({@code before}) and reloads. Render thread. */
	public static CompletableFuture<Void> restore(Minecraft minecraft, Collection<String> before) {
		minecraft.getResourcePackRepository().setSelected(before);
		return minecraft.reloadResourcePacks();
	}
}
