package dev.hearthside.core.launch;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Raw facts about how the host JVM was started, read by the Fabric layer and interpreted by
 * {@link HostJvm#capture(LaunchFacts)}.
 *
 * @param sunJavaCommand {@code sun.java.command}; may be null
 * @param properties only {@code org.prismlauncher.launch.mainclass} and {@code org.prismlauncher.launch.gameargs}, when set
 * @param loaderLaunchArgs {@code FabricLoader.getLaunchArguments(false)}
 */
public record LaunchFacts(Path javaHome, String sunJavaCommand, String classpath, List<String> inputArgs,
		Map<String, String> properties, List<String> loaderLaunchArgs, Path gameDir, long maxHeapBytes) {
	public LaunchFacts {
		inputArgs = List.copyOf(inputArgs);
		properties = Map.copyOf(properties);
		loaderLaunchArgs = List.copyOf(loaderLaunchArgs);
	}
}
