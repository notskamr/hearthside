package dev.hearthside.core.input;

/** Menu and inventory actions a control can perform. Names are stored in profile files. */
public enum MenuAction {
	/** Pick up or place (left click). */
	PICK_PLACE,
	/** Take half or place one (right click). */
	TAKE_HALF,
	/** Move between containers (shift-click). */
	QUICK_MOVE,
	/** Close the screen (Escape). */
	CLOSE
}
