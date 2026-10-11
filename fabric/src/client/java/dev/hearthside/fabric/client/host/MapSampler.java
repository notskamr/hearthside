package dev.hearthside.fabric.client.host;

import dev.hearthside.core.map.PartyMapView;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * What the party map shows, gathered on the server thread (Spec A section 6): every player's position and facing with
 * the time and weather, each tick; and the ground's colours under the map from chunks that are already loaded (never
 * loading any), shaded like a vanilla map, a few rows a tick so it costs almost nothing. The render thread takes the
 * rows from a queue and the rest from a snapshot.
 */
final class MapSampler {
	static final int SIZE = 128;
	private static final int ROWS_PER_TICK = 16;
	private static final long IDLE_AFTER_NANOS = 1_000_000_000L;

	/** One row of the map's pixels (ARGB; 0 where nothing is loaded), for {@code area}. */
	record Row(int y, int[] argb, PartyMapView area) {}

	record PlayerAt(String name, double x, double z, float yaw, ResourceKey<Level> dimension) {}

	record Snapshot(List<PlayerAt> players, ResourceKey<Level> hostDimension, long dayTime, boolean raining,
			boolean thundering) {}

	final ConcurrentLinkedQueue<Row> rows = new ConcurrentLinkedQueue<>();
	volatile Snapshot snapshot;
	private final String hostName;
	private volatile PartyMapView wanted;
	private volatile ResourceKey<Level> wantedDimension = Level.OVERWORLD;
	private volatile long wantedAt;
	private PartyMapView sampling;
	private int nextRow;

	MapSampler(String hostName) {
		this.hostName = hostName;
	}

	/** The panel is showing this area (render thread, each frame it draws). */
	void want(PartyMapView view, ResourceKey<Level> dimension) {
		wanted = view;
		wantedDimension = dimension;
		wantedAt = System.nanoTime();
	}

	/** Server thread, every tick. */
	void tick(MinecraftServer server) {
		List<PlayerAt> players = server.getPlayerList().getPlayers().stream()
				.map(p -> new PlayerAt(p.getGameProfile().name(), p.getX(), p.getZ(), p.getYRot(), p.level().dimension()))
				.toList();
		ServerPlayer host = server.getPlayerList().getPlayerByName(hostName);
		ServerLevel overworld = server.overworld();
		snapshot = new Snapshot(players, host != null ? host.level().dimension() : Level.OVERWORLD, overworld.getOverworldClockTime(),
				overworld.isRaining(), overworld.isThundering());
		PartyMapView view = wanted;
		if (view == null || System.nanoTime() - wantedAt > IDLE_AFTER_NANOS) {
			return; // the panel isn't showing: no sampling
		}
		if (!view.sameArea(sampling)) {
			sampling = view;
			nextRow = 0;
		}
		ServerLevel level = server.getLevel(wantedDimension);
		if (level == null) {
			return;
		}
		for (int i = 0; i < ROWS_PER_TICK; i++) {
			rows.add(sampleRow(level, sampling, nextRow));
			// Round and round: the map stays live as the world changes.
			nextRow = (nextRow + 1) % SIZE;
		}
	}

	private static Row sampleRow(ServerLevel level, PartyMapView view, int y) {
		double blocksPerPixel = 2.0 * view.radius() / SIZE;
		double z = view.centreZ() - view.radius() + (y + 0.5) * blocksPerPixel;
		int bz = Mth.floor(z);
		int northZ = Mth.floor(z - blocksPerPixel);
		int[] argb = new int[SIZE];
		for (int x = 0; x < SIZE; x++) {
			int bx = Mth.floor(view.centreX() - view.radius() + (x + 0.5) * blocksPerPixel);
			LevelChunk chunk = level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
			if (chunk == null) {
				continue; // not loaded: the panel's parchment shows through
			}
			int height = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, bx & 15, bz & 15);
			BlockPos pos = new BlockPos(bx, height, bz);
			BlockState state = chunk.getBlockState(pos);
			MapColor colour = state.getMapColor(level, pos);
			if (colour == MapColor.NONE) {
				continue;
			}
			// Shaded by the slope to the north, as vanilla maps are.
			LevelChunk north = level.getChunkSource().getChunkNow(bx >> 4, northZ >> 4);
			MapColor.Brightness brightness = MapColor.Brightness.NORMAL;
			if (north != null) {
				int northHeight = north.getHeight(Heightmap.Types.WORLD_SURFACE, bx & 15, northZ & 15);
				brightness = height > northHeight ? MapColor.Brightness.HIGH
						: height < northHeight ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
			}
			argb[x] = colour.calculateARGBColor(brightness);
		}
		return new Row(y, argb, view);
	}
}
