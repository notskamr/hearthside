package dev.hearthside.core.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.hearthside.core.map.PartyMapView.Player;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartyMapViewTest {
	static Player at(String name, double x, double z, float yaw) {
		return new Player(name, 0x3f7fd0, x, z, yaw);
	}

	@Test
	void closePlayersUseTheMinimumRadius() {
		PartyMapView view = PartyMapView.frame(List.of(at("a", 0, 0, 0), at("b", 10, 0, 0)));
		assertThat(view.radius()).isEqualTo(PartyMapView.MIN_RADIUS);
		assertThat(view.centreX() % PartyMapView.STEP).isZero();
		assertThat(Math.abs(view.centreX() - 5)).isLessThanOrEqualTo(PartyMapView.STEP / 2);
	}

	@Test
	void radiusFitsEveryonePlusMargin() {
		PartyMapView view = PartyMapView.frame(List.of(at("a", 0, 0, 0), at("b", 300, 0, 0)));
		assertThat(view.radius()).isGreaterThanOrEqualTo(166);
		assertThat(view.radius() % PartyMapView.STEP).isZero();
		assertThat(view.marker(at("a", 0, 0, 0)).atEdge()).isFalse();
		assertThat(view.marker(at("b", 300, 0, 0)).atEdge()).isFalse();
	}

	@Test
	void farApartIsClampedAndEdgeMarkersPointOutward() {
		// Too far apart to fit: the map stays on player 1 and points to the others from its edge.
		Player host = at("host", 0, 0, 0);
		Player far = at("far", 2000, 0, 0);
		PartyMapView view = PartyMapView.frame(List.of(host, far));
		assertThat(view.radius()).isEqualTo(PartyMapView.MAX_RADIUS);
		assertThat(view.marker(host).atEdge()).isFalse();
		PartyMapView.Marker edge = view.marker(far);
		assertThat(edge.atEdge()).isTrue();
		assertThat(edge.u()).isEqualTo(1f);
		assertThat(edge.angleDegrees()).isCloseTo(90f, within(0.5f));
	}

	@Test
	void markersFaceTheWayThePlayerLooks() {
		PartyMapView view = PartyMapView.frame(List.of(at("a", 0, 0, 0)));
		assertThat(view.marker(at("a", 0, 0, 0)).angleDegrees()).isCloseTo(180f, within(0.01f)); // south
		assertThat(view.marker(at("a", 0, 0, 90)).angleDegrees()).isCloseTo(270f, within(0.01f)); // west
		assertThat(view.marker(at("a", 0, 0, -90)).angleDegrees()).isCloseTo(90f, within(0.01f)); // east
		assertThat(view.marker(at("a", 0, 0, 180)).angleDegrees()).isCloseTo(0f, within(0.01f)); // north
		PartyMapView.Marker centre = view.marker(at("a", view.centreX(), view.centreZ(), 0));
		assertThat(centre.u()).isCloseTo(0.5f, within(0.001f));
		assertThat(centre.v()).isCloseTo(0.5f, within(0.001f));
	}

	@Test
	void northIsUp() {
		PartyMapView view = PartyMapView.frame(List.of(at("a", 0, 0, 0)));
		assertThat(view.marker(at("a", 0, -20, 0)).v()).isLessThan(0.5f);
		assertThat(view.marker(at("a", 20, 0, 0)).u()).isGreaterThan(0.5f);
	}

	@Test
	void smallMovesKeepTheSameArea() {
		PartyMapView before = PartyMapView.frame(List.of(at("a", 0, 0, 0), at("b", 10, 0, 0)));
		PartyMapView after = PartyMapView.frame(List.of(at("a", 3, 2, 0), at("b", 12, 1, 0)));
		assertThat(after.sameArea(before)).isTrue();
		PartyMapView moved = PartyMapView.frame(List.of(at("a", 200, 0, 0), at("b", 210, 0, 0)));
		assertThat(moved.sameArea(before)).isFalse();
	}
}
