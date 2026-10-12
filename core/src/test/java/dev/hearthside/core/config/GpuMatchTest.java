package dev.hearthside.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.hearthside.core.config.GpuMatch.Device;
import dev.hearthside.core.config.PresetChooser.GpuKind;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * On OpenGL Minecraft can't tell an AMD or NVIDIA card from an unknown GPU, so Vulkan is asked which kind the GPU
 * OpenGL runs on is (plan ruling T5-6): by name, else the only real GPU, else the only one from OpenGL's vendor.
 */
class GpuMatchTest {
	static final Device RADEON = new Device("AMD Radeon RX 9060 XT", 0x1002, GpuKind.DISCRETE);
	static final Device RYZEN_IGPU = new Device("AMD Radeon(TM) Graphics", 0x1002, GpuKind.INTEGRATED);
	static final Device RTX = new Device("NVIDIA GeForce RTX 4060 Laptop GPU", 0x10DE, GpuKind.DISCRETE);
	static final Device IRIS = new Device("Intel(R) Iris(R) Xe Graphics", 0x8086, GpuKind.INTEGRATED);
	static final Device LLVMPIPE = new Device("llvmpipe (LLVM 17.0.6, 256 bits)", 0x10005, GpuKind.UNKNOWN);

	@Test
	void theDeviceNamedLikeOpenGlsRenderer() {
		var match = GpuMatch.choose(List.of(RYZEN_IGPU, RADEON), "AMD Radeon RX 9060 XT", "ATI Technologies Inc.");
		assertThat(match.kind()).isEqualTo(GpuKind.DISCRETE);
		assertThat(match.device()).isEqualTo("AMD Radeon RX 9060 XT");
	}

	@Test
	void namesMatchIgnoringCaseEitherWayRound() {
		// NVIDIA's OpenGL renderer string carries a suffix Vulkan's device name doesn't.
		assertThat(GpuMatch.choose(List.of(IRIS, RTX), "NVIDIA GeForce RTX 4060 Laptop GPU/PCIe/SSE2", "NVIDIA Corporation")
				.kind()).isEqualTo(GpuKind.DISCRETE);
		assertThat(GpuMatch.choose(List.of(IRIS, RTX), "intel(r) iris(r) xe", "Intel").kind()).isEqualTo(GpuKind.INTEGRATED);
	}

	@Test
	void otherwiseTheOnlyRealGpu() {
		var match = GpuMatch.choose(List.of(LLVMPIPE, RADEON), "Radeon RX 9060 XT (driver 26.8)", "Mystery Inc.");
		assertThat(match.kind()).isEqualTo(GpuKind.DISCRETE);
		assertThat(match.device()).isEqualTo("AMD Radeon RX 9060 XT");
	}

	@Test
	void otherwiseTheOnlyOneFromOpenGlsVendor() {
		assertThat(GpuMatch.choose(List.of(IRIS, RTX), "GeForce something", "NVIDIA Corporation").kind())
				.isEqualTo(GpuKind.DISCRETE);
		assertThat(GpuMatch.choose(List.of(IRIS, RTX), "Some renderer", "Intel").kind()).isEqualTo(GpuKind.INTEGRATED);
		assertThat(GpuMatch.choose(List.of(RTX, RYZEN_IGPU), "Some renderer", "ATI Technologies Inc.").kind())
				.isEqualTo(GpuKind.INTEGRATED);
		assertThat(GpuMatch.choose(List.of(RTX, RYZEN_IGPU), "Some renderer", "Advanced Micro Devices, Inc.").kind())
				.isEqualTo(GpuKind.INTEGRATED);
	}

	@Test
	void corporationIsNotAti() {
		// "NVIDIA Corporation" contains the letters "ati"; it is NVIDIA's.
		assertThat(GpuMatch.choose(List.of(RTX, RYZEN_IGPU), "Some renderer", "NVIDIA Corporation").kind())
				.isEqualTo(GpuKind.DISCRETE);
	}

	@Test
	void unknownWhenNothingSettlesIt() {
		// Two AMD GPUs, neither named like the renderer.
		var match = GpuMatch.choose(List.of(RYZEN_IGPU, RADEON), "Some renderer", "ATI Technologies Inc.");
		assertThat(match.kind()).isEqualTo(GpuKind.UNKNOWN);
		assertThat(match.device()).isNull();
		assertThat(match.why()).isNotBlank();
		assertThat(GpuMatch.choose(List.of(), "AMD Radeon RX 9060 XT", "AMD").kind()).isEqualTo(GpuKind.UNKNOWN);
	}

	@Test
	void aMatchedSoftwareRendererIsUnknown() {
		assertThat(GpuMatch.choose(List.of(LLVMPIPE, RADEON), "llvmpipe (LLVM 17.0.6, 256 bits)", "Mesa").kind())
				.isEqualTo(GpuKind.UNKNOWN);
	}

	@Test
	void blankNamesMatchNothing() {
		var blank = new Device("", 0x1002, GpuKind.INTEGRATED);
		assertThat(GpuMatch.choose(List.of(blank, RADEON, RTX), "AMD Radeon RX 9060 XT", "").device())
				.isEqualTo("AMD Radeon RX 9060 XT");
		assertThat(GpuMatch.choose(List.of(RYZEN_IGPU, RADEON), "", "").kind()).isEqualTo(GpuKind.UNKNOWN);
	}
}
