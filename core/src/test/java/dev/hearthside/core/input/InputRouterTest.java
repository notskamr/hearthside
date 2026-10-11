package dev.hearthside.core.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class InputRouterTest {
	final InputRouter router = new InputRouter();

	static PadSnapshot pad(int id, String guid, PadButton... held) {
		return new PadSnapshot(id, guid, "Pad " + id, PadStyle.XBOX,
				new InputFrame(id, InputFrame.buttons(held), 0, 0, 0, 0, 0, 0));
	}

	@Test
	void routesEachBoundPadToItsPlayer() {
		var a = pad(11, "xbox");
		var b = pad(12, "ps");
		router.update(List.of(a, b));
		router.assign(11, 0);
		router.assign(12, 1);
		var update = router.update(List.of(a, b));
		assertThat(update.frames()).containsEntry(0, a.frame()).containsEntry(1, b.frame()).hasSize(2);
	}

	@Test
	void unboundPadsAreNotRouted() {
		assertThat(router.update(List.of(pad(11, "xbox"))).frames()).isEmpty();
	}

	@Test
	void pressingStartOnUnboundPadRequestsJoin() {
		assertThat(router.update(List.of(pad(13, "g", PadButton.START))).events())
				.containsExactly(new RouterEvent.JoinRequest(13));
	}

	@Test
	void holdingStartRequestsOnce() {
		assertThat(router.update(List.of(pad(13, "g", PadButton.START))).events()).hasSize(1);
		assertThat(router.update(List.of(pad(13, "g", PadButton.START))).events()).isEmpty();
		assertThat(router.update(List.of(pad(13, "g", PadButton.START))).events()).isEmpty();
	}

	@Test
	void startOnBoundPadDoesNotRequestJoin() {
		router.update(List.of(pad(11, "xbox")));
		router.assign(11, 1);
		assertThat(router.update(List.of(pad(11, "xbox", PadButton.START))).events()).isEmpty();
	}

	@Test
	void unplugEmitsLostOnce() {
		router.update(List.of(pad(11, "xbox")));
		router.assign(11, 1);
		assertThat(router.update(List.of()).events()).containsExactly(new RouterEvent.PadLost(1));
		assertThat(router.update(List.of()).events()).isEmpty();
		assertThat(router.hasPad(1)).isFalse();
	}

	@Test
	void replugSameModelRestores() {
		router.update(List.of(pad(11, "xbox")));
		router.assign(11, 1);
		router.update(List.of());
		var update = router.update(List.of(pad(21, "xbox")));
		assertThat(update.events()).containsExactly(new RouterEvent.PadRestored(1));
		assertThat(update.frames()).containsKey(1);
		assertThat(router.playerFor(21)).hasValue(1);
	}

	@Test
	void identicalModelsStayWithTheirPlayers() {
		var a = pad(11, "xbox", PadButton.A);
		var b = pad(12, "xbox", PadButton.B);
		router.update(List.of(a, b));
		router.assign(11, 1);
		router.assign(12, 2);
		var update = router.update(List.of(a, b));
		assertThat(update.frames().get(1)).isEqualTo(a.frame());
		assertThat(update.frames().get(2)).isEqualTo(b.frame());
	}

	@Test
	void reconnectNeverStealsALivePad() {
		router.update(List.of(pad(11, "xbox"), pad(12, "xbox")));
		router.assign(11, 1);
		router.assign(12, 2);
		assertThat(router.update(List.of(pad(11, "xbox"))).events()).containsExactly(new RouterEvent.PadLost(2));
		var update = router.update(List.of(pad(11, "xbox"), pad(13, "xbox")));
		assertThat(update.events()).containsExactly(new RouterEvent.PadRestored(2));
		assertThat(router.playerFor(11)).hasValue(1);
		assertThat(router.playerFor(13)).hasValue(2);
	}

	@Test
	void lowestLostPlayerIsRestoredFirst() {
		router.update(List.of(pad(11, "xbox"), pad(12, "xbox")));
		router.assign(11, 3);
		router.assign(12, 1);
		router.update(List.of());
		assertThat(router.update(List.of(pad(21, "xbox"))).events()).containsExactly(new RouterEvent.PadRestored(1));
	}

	@Test
	void releasedPlayerIsNotRestored() {
		router.update(List.of(pad(11, "xbox")));
		router.assign(11, 2);
		router.update(List.of());
		router.release(2);
		assertThat(router.update(List.of(pad(21, "xbox"))).events()).isEmpty();
		assertThat(router.update(List.of(pad(21, "xbox", PadButton.START))).events())
				.containsExactly(new RouterEvent.JoinRequest(21));
	}

	@Test
	void armedClaimTakesTheNextAPress() {
		router.update(List.of(pad(11, "xbox")));
		router.armClaim();
		assertThat(router.update(List.of(pad(11, "xbox", PadButton.A))).events())
				.containsExactly(new RouterEvent.ClaimRequest(11));
		router.update(List.of(pad(11, "xbox")));
		assertThat(router.update(List.of(pad(11, "xbox", PadButton.A))).events()).isEmpty();
	}

	@Test
	void assignRejectsDoubleBinding() {
		router.update(List.of(pad(11, "xbox"), pad(12, "xbox")));
		router.assign(11, 1);
		assertThatThrownBy(() -> router.assign(11, 2)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> router.assign(12, 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> router.assign(99, 2)).isInstanceOf(IllegalArgumentException.class);
	}
}
