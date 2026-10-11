package dev.hearthside.core.compositor;

import java.util.UUID;

/**
 * What one side of a frame transfer can do with shared GPU memory.
 *
 * @param windows this process runs on Windows (the only platform with a shared-memory transport today)
 * @param canImport this process's graphics API can export and import shared GPU memory
 * @param deviceUuid the GPU's device UUID, or {@code null} when unknown
 * @param driverUuid the GPU driver's UUID, or {@code null} when unknown
 */
public record SharingFacts(boolean windows, boolean canImport, UUID deviceUuid, UUID driverUuid) {}
