package dev.hearthside.core.ui;

import dev.hearthside.core.profile.GuestNames;
import java.util.List;

/**
 * The on-screen keyboard (Spec A section 5.4): a grid of keys a controller moves over. A types, X deletes, Y adds a
 * space, LB/RB switch layers, Menu confirms. In name mode only valid name characters are offered (Y's space is "_").
 * Not thread-safe.
 */
public final class KeyboardModel {
	public enum Mode { NAME, TEXT }

	public enum Layer { LOWER, UPPER, SYMBOLS }

	static final List<String> LOWER = List.of("1234567890", "qwertyuiop", "asdfghjkl_", "zxcvbnm");
	static final List<String> UPPER = List.of("1234567890", "QWERTYUIOP", "ASDFGHJKL_", "ZXCVBNM");
	static final List<String> SYMBOLS = List.of("!?.,:;'\"-+", "()[]{}<>/\\", "@#$%^&*=~|");
	public static final int NAME_MAX = 16;

	private final Mode mode;
	private final int max;
	private final StringBuilder text;
	private boolean suggested;
	private Layer layer = Layer.LOWER;
	private int row = 1;
	private int column;

	private KeyboardModel(Mode mode, String initial, int max, boolean suggested) {
		this.mode = mode;
		this.max = max;
		this.text = new StringBuilder(initial.length() > max ? initial.substring(0, max) : initial);
		this.suggested = suggested && !initial.isEmpty();
	}

	/** For a player name: the suggestion is ready, so confirming at once keeps it. */
	public static KeyboardModel forName(String suggestion) {
		return new KeyboardModel(Mode.NAME, suggestion, NAME_MAX, true);
	}

	public static KeyboardModel forText(String initial, int max) {
		return new KeyboardModel(Mode.TEXT, initial, max, false);
	}

	public void move(int dx, int dy) {
		List<String> rows = rows(layer);
		row = Math.floorMod(row + dy, rows.size());
		column = Math.floorMod(Math.min(column, rows.get(row).length() - 1) + dx, rows.get(row).length());
	}

	public void type() {
		replaceSuggestion();
		if (text.length() < max) {
			text.append(keyAt(row, column));
		}
	}

	public void delete() {
		if (suggested) {
			replaceSuggestion();
		} else if (!text.isEmpty()) {
			text.setLength(text.length() - 1);
		}
	}

	/** A space; names can't have spaces, so in a name it is "_" (as a name writes one). */
	public void space() {
		replaceSuggestion();
		if (text.length() < max) {
			text.append(mode == Mode.NAME ? '_' : ' ');
		}
	}

	/**
	 * A character typed on a PC keyboard (Spec E section 3.6). It types only what this keyboard's keys could type (a
	 * name: letters, digits and "_", a space being "_"), and only while there's room; false means nothing changed.
	 */
	public boolean typeChar(char c) {
		char typed = c == ' ' && mode == Mode.NAME ? '_' : c;
		if (!offers(typed)) {
			return false;
		}
		replaceSuggestion();
		if (text.length() >= max) {
			return false;
		}
		text.append(typed);
		return true;
	}

	public void nextLayer() {
		setLayer(cycle(1));
	}

	public void previousLayer() {
		setLayer(cycle(-1));
	}

	public String text() {
		return text.toString();
	}

	/** Whether the text is still the untouched suggestion. */
	public boolean suggested() {
		return suggested;
	}

	public boolean canConfirm() {
		return mode == Mode.TEXT || GuestNames.isValid(text.toString());
	}

	public Mode mode() {
		return mode;
	}

	public Layer layer() {
		return layer;
	}

	public int cursorRow() {
		return row;
	}

	public int cursorColumn() {
		return column;
	}

	public int rowCount() {
		return rows(layer).size();
	}

	public int rowLength(int row) {
		return rows(layer).get(row).length();
	}

	public char keyAt(int row, int column) {
		return rows(layer).get(row).charAt(column);
	}

	private void replaceSuggestion() {
		if (suggested) {
			text.setLength(0);
			suggested = false;
		}
	}

	private Layer cycle(int direction) {
		Layer[] layers = layers();
		int index = 0;
		for (int i = 0; i < layers.length; i++) {
			if (layers[i] == layer) {
				index = i;
			}
		}
		return layers[Math.floorMod(index + direction, layers.length)];
	}

	private void setLayer(Layer next) {
		layer = next;
		List<String> rows = rows(layer);
		row = Math.min(row, rows.size() - 1);
		column = Math.min(column, rows.get(row).length() - 1);
	}

	/** Whether one of this keyboard's layers has the key (text has a space bar too). */
	private boolean offers(char c) {
		if (c == ' ') {
			return mode == Mode.TEXT;
		}
		for (Layer each : layers()) {
			for (String keys : rows(each)) {
				if (keys.indexOf(c) >= 0) {
					return true;
				}
			}
		}
		return false;
	}

	/** A name has no symbols layer. */
	private Layer[] layers() {
		return mode == Mode.NAME ? new Layer[] {Layer.LOWER, Layer.UPPER} : Layer.values();
	}

	private static List<String> rows(Layer layer) {
		return switch (layer) {
			case LOWER -> LOWER;
			case UPPER -> UPPER;
			case SYMBOLS -> SYMBOLS;
		};
	}
}
