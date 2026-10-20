package org.mtr.mod.screen;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import org.mtr.mapping.holder.*;
import org.mtr.mapping.mapper.MinecraftClientHelper;
import org.mtr.mod.Init;

import java.util.function.Consumer;

/**
 * Represents a World Map that is drawn as the background in the WidgetMap.
 */
public class WorldMap {

	private final Object2ObjectOpenHashMap<IntIntImmutablePair, MapImage> mapImages = new Object2ObjectOpenHashMap<>();
	private MapOverlayMode mapOverlayMode;
	private double updateMapTimer;
    private final java.util.ArrayDeque<IntIntImmutablePair> pending = new java.util.ArrayDeque<>();
    private final java.util.Set<IntIntImmutablePair> queued = new java.util.HashSet<>();

	public static final int CHUNK_SIZE = 16;
	private static final int UPDATE_FREQUENCY = 40;
	private static final double LIGHT_DIM_FACTOR = 4;

	public WorldMap() {
		updateMapTimer = -1;
		mapOverlayMode = MapOverlayMode.TOP_VIEW;
	}

	public void tick(World world, ClientPlayerEntity player, float delta) {
		if (updateMapTimer == -1 || updateMapTimer >= UPDATE_FREQUENCY) {
			updateMapTimer = 0;
			updateMap(World.cast(world), player);
		}

		for (final MapImage mapImage : new Object2ObjectOpenHashMap<>(mapImages).values()) {
			if (!world.getChunkManager().isChunkLoaded(mapImage.chunkX, mapImage.chunkZ)) {
				mapImage.dispose();
				mapImages.remove(new IntIntImmutablePair(mapImage.chunkX, mapImage.chunkZ));
			}
		}

		long budget = System.nanoTime() + 2_000_000L;
        for (int n=0;n<2&&!pending.isEmpty()&&System.nanoTime()<budget;n++){var at=pending.removeFirst();queued.remove(at);if(world.getChunkManager().isChunkLoaded(at.leftInt(),at.rightInt()))updateTile(world,player,at.leftInt(),at.rightInt());}
        updateMapTimer += delta;
	}

	public void forEachTile(Consumer<MapImage> callback) {
		mapImages.values().forEach(mapImage -> {
			if (!mapImage.disposed) {
				callback.accept(mapImage);
			}
		});
	}

    public void updateMap(World world, ClientPlayerEntity player) {
        if(player==null)return;int radius=MinecraftClientHelper.getRenderDistance()+1,cx=Math.floorDiv(player.getBlockPos().getX(),16),cz=Math.floorDiv(player.getBlockPos().getZ(),16);
        // Fill near the player first; never scan/upload the entire loaded world in one GUI frame.
        for(int ring=0;ring<=radius;ring++)for(int dx=-ring;dx<=ring;dx++)for(int dz=-ring;dz<=ring;dz++)if(Math.max(Math.abs(dx),Math.abs(dz))==ring){var at=new IntIntImmutablePair(cx+dx,cz+dz);if(world.getChunkManager().isChunkLoaded(at.leftInt(),at.rightInt())&&queued.add(at))pending.addLast(at);}
    }
    private void updateTile(World world,ClientPlayerEntity player,int chunkX,int chunkZ) {
				final int chunkXStart = chunkX * CHUNK_SIZE;
				final int chunkZStart = chunkZ * CHUNK_SIZE;
				final IntIntImmutablePair chunkXZ = new IntIntImmutablePair(chunkX, chunkZ);

				MapImage mapTexture = mapImages.get(chunkXZ);

				if (mapTexture == null) {
					final NativeImage nativeImage = new NativeImage(CHUNK_SIZE, CHUNK_SIZE, false);
					mapTexture = new MapImage(chunkX, chunkZ, new NativeImageBackedTexture(nativeImage));
					mapImages.put(chunkXZ, mapTexture);
				}

				// Loop each block in chunk
				for (int k = 0; k < CHUNK_SIZE; k++) {
					for (int l = 0; l < CHUNK_SIZE; l++) {
						final int blockX = chunkXStart + k;
						final int blockZ = chunkZStart + l;
						// The Y position of the highest block in this coordinates
						final int topY = world.getTopY(HeightMapType.getMotionBlockingMapped(), blockX, blockZ) - 1;
						final int blockY;
						if (mapOverlayMode == MapOverlayMode.TOP_VIEW) {
							blockY = topY;
						} else {
							int currentY = Math.min(topY, player.getBlockPos().getY());

							if (currentY != topY) {
								// Find lowest Y level so it can be projected
								while (true) {
									if (currentY < -64 || !world.getBlockState(Init.newBlockPos(blockX, currentY, blockZ)).isAir()) {
										break;
									} else {
										currentY--;
									}
								}
							}

							blockY = currentY;
						}

						final BlockPos finalPos = Init.newBlockPos(blockX, blockY, blockZ);
						final int lightLevel;
						if (mapOverlayMode.calculateLight) {
							final BlockPos lightReferencePos = finalPos.up();
							lightLevel = Math.max(world.getLightLevel(LightType.getBlockMapped(), lightReferencePos), world.getLightLevel(LightType.getSkyMapped(), lightReferencePos));
						} else {
							lightLevel = 15;
						}

						final NativeImage nativeImage = mapTexture.texture.getImage();
						if (nativeImage != null) {
							nativeImage.setPixelColor(k, l, convertColorABGR(divideColorRGB(world.getBlockState(finalPos).getBlock().getDefaultMapColor().getColorMapped(), mapOverlayMode == MapOverlayMode.TOP_VIEW ? 1.5 : 1 + ((15 - lightLevel) / LIGHT_DIM_FACTOR))));
						}
					}
				}

				mapTexture.texture.upload();
    }

	private static int convertColorABGR(int rgb) {
		final int a = 255;
		final int r = (rgb >> 16) & 255;
		final int g = (rgb >> 8) & 255;
		final int b = (rgb) & 255;
		return a << 24 | b << 16 | g << 8 | r;
	}

	private static int divideColorRGB(int color, double amount) {
		final int r = (int) (((color >> 16) & 0xFF) / amount);
		final int g = (int) (((color >> 8) & 0xFF) / amount);
		final int b = (int) ((color & 0xFF) / amount);
		return (r << 16) + (g << 8) + b;
	}

	public void setMapOverlayMode(MapOverlayMode overlayMode) {
		this.mapOverlayMode = overlayMode;pending.clear();queued.clear();updateMapTimer=-1;
	}

	public boolean isMapOverlayMode(MapOverlayMode mapOverlayMode) {
		return this.mapOverlayMode == mapOverlayMode;
	}

	public void disposeImages() {
		for (MapImage image : mapImages.values()) {
			image.dispose();
		}
		mapImages.clear();pending.clear();queued.clear();
	}

	public static class MapImage {

		public final Identifier textureId;
		public final NativeImageBackedTexture texture;
		public final int chunkX;
		public final int chunkZ;
		private boolean disposed;

		private MapImage(int chunkX, int chunkZ, NativeImageBackedTexture texture) {
			this.textureId = MinecraftClient.getInstance().getTextureManager().registerDynamicTexture("mtr_dashboard_map", texture);
			this.texture = texture;
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;
		}

		public void dispose() {
			this.disposed = true;
			MinecraftClient.getInstance().getTextureManager().destroyTexture(this.textureId);
		}

		public boolean disposed() {
			return this.disposed;
		}
	}

	public enum MapOverlayMode {
		TOP_VIEW(false),
		CURRENT_Y(true);

		public final boolean calculateLight;

		MapOverlayMode(boolean calculateLight) {
			this.calculateLight = calculateLight;
		}
	}
}
