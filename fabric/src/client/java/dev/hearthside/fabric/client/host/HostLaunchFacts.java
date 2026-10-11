package dev.hearthside.fabric.client.host;

import dev.hearthside.core.launch.LaunchFacts;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** Reads the facts {@code HostJvm.capture} needs from the running host JVM. */
final class HostLaunchFacts {
	private static final List<String> PROPERTY_KEYS =
			List.of("org.prismlauncher.launch.mainclass", "org.prismlauncher.launch.gameargs");

	private HostLaunchFacts() {}

	static LaunchFacts read() {
		Map<String, String> properties = new HashMap<>();
		for (String key : PROPERTY_KEYS) {
			String value = System.getProperty(key);
			if (value != null) {
				properties.put(key, value);
			}
		}
		FabricLoader loader = FabricLoader.getInstance();
		return new LaunchFacts(
				Path.of(System.getProperty("java.home")),
				System.getProperty("sun.java.command"),
				System.getProperty("java.class.path"),
				ManagementFactory.getRuntimeMXBean().getInputArguments(),
				properties,
				List.of(loader.getLaunchArguments(false)),
				loader.getGameDir().toAbsolutePath(),
				Runtime.getRuntime().maxMemory());
	}
}
