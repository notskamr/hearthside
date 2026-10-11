# Compositor feasibility spike — 2026-10-11

Question: can one Minecraft process hand its rendered frames to another fast enough to composite every player into
one window? The spike code was throwaway (two LWJGL programs with hidden windows); only these results are kept.

## Machine

Windows 11, Ryzen 5 7600X (has integrated graphics too), AMD Radeon RX 9060 XT, driver 26.8.1, OpenGL 4.5 core.
LWJGL 3.4.3 as bundled with Minecraft 26.3 (includes `lwjgl-vulkan`).

## Capabilities found

- OpenGL: `GL_EXT_memory_object`, `GL_EXT_memory_object_win32`, `GL_EXT_semaphore`, `GL_EXT_semaphore_win32`,
  `GL_ARB_buffer_storage`.
- Vulkan (same GPU): `VK_KHR_external_memory_win32`, `VK_KHR_external_semaphore_win32`, `VK_KHR_win32_keyed_mutex`.
- The GL device UUID equals the Vulkan device UUID of the RX 9060 XT. The integrated GPU has a different UUID, so
  device selection must match by UUID.

## Experiment

A producer process allocates exportable, **named** Vulkan memory for an RGBA8 image, imports it into its own GL
context (`glImportMemoryWin32HandleEXT` + `glTexStorageMem2DEXT`) and renders a different colour each frame at 60 fps.
A consumer process imports the same memory **by name** (`glImportMemoryWin32NameEXT`), blits it into its own target
(standing in for compositing) and checks the centre pixel. Sync in the spike was a crude `glFinish`.
The copy fallback reads pixels back through a PBO into a memory-mapped file; the consumer uploads them.

## Results (600 frames each)

| Path, view size | Frames correct | Host cost per frame (p50 / p95) | Frame → composited (p50 / p95) | Producer cost (p50) |
|---|---|---|---|---|
| Zero-copy, 960×1080 | 600 / 600 | 0.16 / 0.24 ms | 0.17 / 0.25 ms | 0.63 ms (incl. `glFinish`) |
| Zero-copy, 1920×1080 | 600 / 600 | 0.17 / 0.24 ms | 0.18 / 0.25 ms | 0.52 ms (incl. `glFinish`) |
| Copy fallback, 960×1080 | 600 / 600 | 0.54 / 0.59 ms | 0.54 / 0.60 ms | 0.80 ms (sync readback) |

## Minecraft 26.3 hook points

`Minecraft` renders everything (world, GUI, Sodium, Iris) into `gameRenderer.mainRenderTarget()`, then in the
`swapchainBlit` section calls `windowSurface.blitFromTexture(encoder, colorTexture)` and later
`windowSurface.present()` (`GlSurface.present` = `SDL_GL_SwapWindow`). Guests can export their finished frame at that
blit; the host can replace that one blit with composition. 26.3 also ships a Vulkan render backend; Sodium runs on the
GL backend, which is the one targeted first.

## Conclusion

Zero-copy sharing works across processes on this machine and costs well under a millisecond. The copy fallback also
fits the budget (about 1.6 ms of host time for three 960×1080 guests). Recommendation: zero-copy as the primary
transport, copy as the automatic fallback, both behind one interface (Spec B).
