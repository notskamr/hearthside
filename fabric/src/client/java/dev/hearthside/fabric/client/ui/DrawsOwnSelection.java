package dev.hearthside.fabric.client.ui;

/**
 * A widget that lights itself up when it is the selection (the pause menu's glass buttons), or a screen whose widgets
 * all do (the pause menu's Leave confirm): the controller's focus ring ({@code MenuNavigator}) isn't drawn around them
 * as well, so the selection shows one ring, never two. Shared screens such as the lobby keep the ring: its colour says
 * whose controller moved the selection.
 */
public interface DrawsOwnSelection {
}
