package dev.hearthside.fabric.client.host;

import com.mojang.blaze3d.platform.NativeImage;
import dev.hearthside.core.map.PartyMapView;
import dev.hearthside.fabric.HearthsideMod;
import java.util.Queue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** The party map as a texture, filled row by row from {@link MapSampler}; cleared when the map moves. Render thread. */
final class PartyMapTexture {
	static final Identifier ID = Identifier.fromNamespaceAndPath(HearthsideMod.MOD_ID, "party_map");

	private DynamicTexture texture;
	private PartyMapView area;

	/** Takes the rows sampled so far into the texture. */
	void drain(Queue<MapSampler.Row> rows) {
		MapSampler.Row row = rows.poll();
		if (row == null) {
			return;
		}
		if (texture == null) {
			texture = new DynamicTexture(() -> "Hearthside party map", MapSampler.SIZE, MapSampler.SIZE, true);
			Minecraft.getInstance().getTextureManager().register(ID, texture);
		}
		NativeImage image = texture.getPixels();
		for (; row != null; row = rows.poll()) {
			if (!row.area().sameArea(area)) {
				area = row.area();
				image.fillRect(0, 0, MapSampler.SIZE, MapSampler.SIZE, 0);
			}
			for (int x = 0; x < MapSampler.SIZE; x++) {
				image.setPixel(x, row.y(), row.argb()[x]);
			}
		}
		texture.upload();
	}

	boolean ready() {
		return texture != null;
	}

	/** How much of the map has ground on it (0 to 1). For the E2E. */
	float filled() {
		if (texture == null) {
			return 0;
		}
		NativeImage image = texture.getPixels();
		int count = 0;
		for (int y = 0; y < MapSampler.SIZE; y++) {
			for (int x = 0; x < MapSampler.SIZE; x++) {
				if ((image.getPixel(x, y) >>> 24) != 0) {
					count++;
				}
			}
		}
		return count / (float) (MapSampler.SIZE * MapSampler.SIZE);
	}

	void close() {
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(ID);
			texture = null;
			area = null;
		}
	}
}
