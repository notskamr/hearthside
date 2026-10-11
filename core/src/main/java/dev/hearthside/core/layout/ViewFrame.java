package dev.hearthside.core.layout;

/** A player's view as drawn this frame: whose it is (seat 0 = player 1), where, and how opaque. */
public record ViewFrame(int seat, Rect rect, float alpha) {}
