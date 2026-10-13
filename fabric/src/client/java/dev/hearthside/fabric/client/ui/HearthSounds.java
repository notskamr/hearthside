package dev.hearthside.fabric.client.ui;

import dev.hearthside.fabric.HearthsideMod;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;

/**
 * Hearthside's soft, warm UI sounds (Spec A section 4.5): a wooden tick as focus moves, a page turning as a carousel
 * does, key taps on the on-screen keyboard (a lower tick for a key it can't type), a knock on confirm, a crackle when
 * someone joins and a snuff when they leave, and the join prompt sliding in and out. Each is a {@code hearthside:ui.*}
 * event mapped in sounds.json to a vanilla sound, so resource packs restyle them; played on the UI channel, a little
 * differently each time. Render thread.
 */
public final class HearthSounds {
	public enum Ui {
		TICK(0.35f, 45), PAGE(0.35f, 0), KEY(0.35f, 0), KEY_DELETE(0.35f, 0), KNOCK(0.6f, 0),
		// Several players leaving at once (the lobby closing) is one snuff, not a pile of them.
		JOIN(0.6f, 100), LEAVE(0.6f, 100), PROMPT_IN(0.6f, 0), PROMPT_OUT(0.6f, 0);

		private final SoundEvent event;
		private final float volume;
		private final long gapNanos;

		Ui(float volume, long gapMillis) {
			this.event = SoundEvent.createVariableRangeEvent(
					Identifier.fromNamespaceAndPath(HearthsideMod.MOD_ID, "ui." + name().toLowerCase(Locale.ROOT)));
			this.volume = volume;
			this.gapNanos = gapMillis * 1_000_000L;
		}
	}

	private static final float PITCH_JITTER = 0.04f, REFUSE_PITCH = 0.6f;
	private static final RandomSource RANDOM = RandomSource.create();
	private static final Map<Ui, Long> lastPlayed = new EnumMap<>(Ui.class);

	private HearthSounds() {}

	public static void play(Ui sound) {
		play(sound, 1f);
	}

	/** The soft "no": the tick, lower (a character an on-screen keyboard can't type). */
	public static void refuse() {
		play(Ui.TICK, REFUSE_PITCH);
	}

	private static void play(Ui sound, float basePitch) {
		long now = System.nanoTime();
		Long last = lastPlayed.get(sound);
		if (last != null && now - last < sound.gapNanos) {
			return; // ticks at most one per 45 ms, however fast focus moves
		}
		lastPlayed.put(sound, now);
		float pitch = basePitch + (RANDOM.nextFloat() * 2f - 1f) * PITCH_JITTER;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.event, pitch, sound.volume));
	}
}
