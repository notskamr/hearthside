package dev.hearthside.core.compositor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegmentSlotTableTest {
	@TempDir
	Path dir;

	@Test
	void createThenOpenSeesTheSameState() throws IOException {
		Path file = dir.resolve("frames.slots");
		try (SegmentSlotTable created = SegmentSlotTable.create(file, 4, 7);
				SegmentSlotTable opened = SegmentSlotTable.open(file)) {
			assertThat(opened.slots()).isEqualTo(4);
			assertThat(opened.generation()).isEqualTo(7);
			assertThat(created.compareAndSet(2, SlotState.FREE, SlotState.WRITING)).isTrue();
			created.setFrame(2, 42);
			created.setMemorySize(2, 4_718_592L);
			assertThat(opened.state(2)).isEqualTo(SlotState.WRITING);
			assertThat(opened.frame(2)).isEqualTo(42);
			assertThat(opened.memorySize(2)).isEqualTo(4_718_592L);
			assertThat(opened.state(0)).isEqualTo(SlotState.FREE);
		}
	}

	@Test
	void openRejectsWrongMagic() throws IOException {
		Path file = dir.resolve("junk.slots");
		Files.write(file, new byte[256]);
		assertThatThrownBy(() -> SegmentSlotTable.open(file)).isInstanceOf(IOException.class).hasMessageContaining("not a slot table");
	}

	@Test
	void casFromTwoThreads() throws Exception {
		Path file = dir.resolve("race.slots");
		try (SegmentSlotTable a = SegmentSlotTable.create(file, 1, 1); SegmentSlotTable b = SegmentSlotTable.open(file)) {
			AtomicInteger wins = new AtomicInteger();
			Runnable toggler = () -> {
				SegmentSlotTable t = Thread.currentThread().getName().equals("a") ? a : b;
				for (int i = 0; i < 10_000; i++) {
					while (!t.compareAndSet(0, SlotState.FREE, SlotState.WRITING)) {
						Thread.onSpinWait();
					}
					wins.incrementAndGet();
					assertThat(t.compareAndSet(0, SlotState.WRITING, SlotState.FREE)).isTrue();
				}
			};
			Thread ta = Thread.ofPlatform().name("a").start(toggler);
			Thread tb = Thread.ofPlatform().name("b").start(toggler);
			ta.join();
			tb.join();
			assertThat(wins.get()).isEqualTo(20_000);
			assertThat(a.state(0)).isEqualTo(SlotState.FREE);
		}
	}
}
