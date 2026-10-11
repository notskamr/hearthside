package dev.hearthside.core.session;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Names for new worlds: "Co-op World", then "Co-op World 2" and so on, never one already used. */
public final class WorldNames {
	public static final String DEFAULT = "Co-op World";

	private WorldNames() {}

	/** {@code wanted} (or the default when blank), counted up past names in {@code taken}, ignoring case. */
	public static String unique(String wanted, Collection<String> taken) {
		String base = wanted == null || wanted.isBlank() ? DEFAULT : wanted.strip();
		Set<String> used = taken.stream().map(n -> n.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
		if (!used.contains(base.toLowerCase(Locale.ROOT))) {
			return base;
		}
		for (int n = 2; ; n++) {
			String candidate = base + " " + n;
			if (!used.contains(candidate.toLowerCase(Locale.ROOT))) {
				return candidate;
			}
		}
	}
}
