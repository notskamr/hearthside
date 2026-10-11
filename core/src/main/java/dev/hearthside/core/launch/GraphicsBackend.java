package dev.hearthside.core.launch;

/** The graphics API a game renders with, as Minecraft's {@code --graphicsBackend} option names it. */
public enum GraphicsBackend {
	OPENGL("opengl"),
	VULKAN("vulkan");

	private final String argument;

	GraphicsBackend(String argument) {
		this.argument = argument;
	}

	/** The value for Minecraft's {@code --graphicsBackend} option. */
	public String argument() {
		return argument;
	}
}
