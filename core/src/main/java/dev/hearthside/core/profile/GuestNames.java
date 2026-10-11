package dev.hearthside.core.profile;

import java.util.regex.Pattern;

/** Guest names follow Minecraft's username rule: 3–16 characters of letters, digits and underscore. */
public final class GuestNames {
	private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_]{3,16}");

	private GuestNames() {}

	public static boolean isValid(String name) {
		return name != null && VALID.matcher(name).matches();
	}

	public static void requireValid(String name) {
		if (!isValid(name)) {
			throw new IllegalArgumentException(
					"Invalid guest name '" + name + "': use 3-16 letters, digits or underscores");
		}
	}
}
