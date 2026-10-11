package dev.hearthside.core.profile;

import java.util.Objects;

/**
 * A guest's skin: one of Minecraft's default skins, a PNG file on disk, or a Java player's skin borrowed by username.
 *
 * @param slim whether the skin uses the slim ("Alex") arm model
 */
public record SkinRef(Kind kind, String value, boolean slim) {
	public enum Kind { DEFAULT, FILE, BORROWED }

	public SkinRef {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(value, "value");
	}

	public SkinRef(Kind kind, String value) {
		this(kind, value, kind == Kind.DEFAULT && "alex".equals(value));
	}

	/** @param name a Minecraft default skin name, e.g. {@code "steve"} or {@code "alex"} */
	public static SkinRef defaultSkin(String name) {
		return new SkinRef(Kind.DEFAULT, name);
	}

	public static SkinRef file(String path) {
		return new SkinRef(Kind.FILE, path, false);
	}

	public static SkinRef file(String path, boolean slim) {
		return new SkinRef(Kind.FILE, path, slim);
	}

	/** @param username the Java player whose skin to use; the guest still plays as a local guest */
	public static SkinRef borrowed(String username, boolean slim) {
		return new SkinRef(Kind.BORROWED, username, slim);
	}
}
