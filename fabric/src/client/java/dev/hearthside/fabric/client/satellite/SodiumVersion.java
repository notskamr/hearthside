package dev.hearthside.fabric.client.satellite;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

/**
 * The Sodium versions Hearthside was tested with: {@value #TESTED}. Greying Sodium's Fullscreen on a guest
 * ({@link SodiumGuestOptions}) names one of Sodium's options and one of its internal classes, and Sodium crashes the
 * game when such an overlay fails, so it is only done on these. Sodium changes its options and internals between
 * minor versions (0.8 to 0.9, say); the patch releases after 0.9.2 are fixes for the same Minecraft, and a renamed
 * option there would break every mod that overlays it. Earlier 0.9 releases weren't tested. Kept free of Sodium's
 * classes.
 */
public final class SodiumVersion {
	/** As the install guide quotes it. */
	public static final String TESTED = "0.9.2 and later 0.9.x releases (below 0.10)";
	/** E2E (Q4b): the guest takes its Sodium for this version (a host's {@code -Dhearthside.e2e.pretendSodiumVersion}). */
	public static final String PRETEND_ENV = "HEARTHSIDE_E2E_PRETEND_SODIUM_VERSION";

	private SodiumVersion() {}

	/** The installed Sodium's version (the E2E's pretend one if set), or null without Sodium. */
	public static Version installed() throws VersionParsingException {
		String pretend = System.getenv(PRETEND_ENV);
		if (pretend != null) {
			return Version.parse(pretend);
		}
		return FabricLoader.getInstance().getModContainer("sodium").map(ModContainer::getMetadata)
				.map(metadata -> metadata.getVersion()).orElse(null);
	}

	/** Whether Hearthside was tested with this Sodium: 0.9.2 up to, not including, 0.10. */
	public static boolean tested(Version version) {
		return version instanceof SemanticVersion semantic && semantic.getVersionComponent(0) == 0
				&& semantic.getVersionComponent(1) == 9 && semantic.getVersionComponent(2) >= 2;
	}
}
