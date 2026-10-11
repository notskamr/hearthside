package dev.hearthside.fabric.client.menu;

/**
 * A Hearthside screen one seat opened from its lobby card (its controls, managing profiles): that seat's controller
 * drives it even though its card is still open behind it.
 */
public interface SeatScreen extends PadScreen {
	/** The seat that opened it (1-3). */
	int seat();
}
