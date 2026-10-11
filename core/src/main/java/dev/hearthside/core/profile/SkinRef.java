package dev.hearthside.core.profile;

import java.util.Objects;

/** A guest's skin: one of Minecraft's default skins, or a PNG file on disk. */
public record SkinRef(Kind kind, String value) {
	public enum Kind { DEFAULT, FILE }

	public SkinRef {
		Objects.requireNonNull(kind, "kind");
		Objects.requireNonNull(value, "value");
	}

	/** @param name a Minecraft default skin name, e.g. {@code "steve"} or {@code "alex"} */
	public static SkinRef defaultSkin(String name) {
		return new SkinRef(Kind.DEFAULT, name);
	}

	public static SkinRef file(String path) {
		return new SkinRef(Kind.FILE, path);
	}
}
