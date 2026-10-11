package dev.hearthside.core.session;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Where one controller is in joining a game (Spec A §3). */
public sealed interface SeatState {
	/** In game: pressed A once; a second A before {@code until} joins. Holds no seat yet. */
	record Prompted(Instant until) implements SeatState {}

	/** Has a seat; picking or creating a profile. */
	record ChoosingProfile() implements SeatState {}

	/** Profile chosen; their game is loading or waiting to enter the world. */
	record Ready(UUID profile) implements SeatState {}

	/** In the world. */
	record Playing(UUID profile) implements SeatState {}

	/** The controller dropped; the seat is kept. */
	record Disconnected(SeatState before, Instant since) implements SeatState {
		public Disconnected {
			Objects.requireNonNull(before, "before");
		}
	}

	/** Disconnected long enough that another controller may take the player over. */
	record TakeoverOffered(SeatState before) implements SeatState {
		public TakeoverOffered {
			Objects.requireNonNull(before, "before");
		}
	}
}
