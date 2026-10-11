package dev.hearthside.core.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InWorldSignalTest {
	@Test
	void inWorldIsSentOncePerJoin() {
		var signal = new InWorldSignal();
		assertThat(signal.observe(true, false, true)).as("not before JoinWorld").isFalse();
		signal.joinRequested();
		assertThat(signal.observe(true, true, true)).as("still on a loading screen").isFalse();
		assertThat(signal.observe(true, false, true)).isTrue();
		assertThat(signal.observe(true, false, true)).isFalse();
		// A portal: a loading screen, then the world again. Not a new join.
		assertThat(signal.observe(true, true, true)).isFalse();
		assertThat(signal.observe(true, false, true)).isFalse();
		signal.joinRequested();
		assertThat(signal.observe(true, false, true)).isTrue();
	}

	@Test
	void notBeforeAFrame() {
		var signal = new InWorldSignal();
		signal.joinRequested();
		assertThat(signal.observe(true, false, false)).isFalse();
		assertThat(signal.observe(false, false, true)).isFalse();
		assertThat(signal.observe(true, false, true)).isTrue();
	}

	@Test
	void leftWorldAfterBeingIn() {
		var signal = new InWorldSignal();
		assertThat(signal.observeLeft(false)).as("never in").isFalse();
		signal.joinRequested();
		assertThat(signal.observeLeft(false)).as("joining, not yet connected").isFalse();
		signal.observe(true, false, true);
		assertThat(signal.observeLeft(true)).isFalse();
		assertThat(signal.observeLeft(false)).isTrue();
		assertThat(signal.observeLeft(false)).isFalse();
	}
}
