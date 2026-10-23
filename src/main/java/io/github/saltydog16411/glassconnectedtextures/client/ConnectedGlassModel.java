package io.github.saltydog16411.glassconnectedtextures.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.IQuadTransformer;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReferenceArray;

final class ConnectedGlassModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Integer> CONNECTIONS = new ModelProperty<>();

    private static final AtomicReferenceArray<ModelData> CONNECTION_DATA =
            new AtomicReferenceArray<>(ConnectionMask.PACKED_VARIANT_COUNT);

    private static final float SURFACE_INSET = 1.0F;
    private static final float SURFACE_SCALE = (16.0F - SURFACE_INSET * 2.0F) / 16.0F;
    private static final float BORDER_OFFSET = 1.0F / 4096.0F;
    private static final float POSITION_EPSILON = 1.0F / 2048.0F;
    private static final float UV_EPSILON = 1.0F / 64.0F;

    private final GlassTargets.TextureFamily family;
    private final boolean pane;
    private final ConcurrentMap<BakedQuad, QuadVariants> quadCache = new ConcurrentHashMap<>();

    ConnectedGlassModel(BakedModel originalModel, GlassTargets.TextureFamily family, boolean pane) {
        super(originalModel);
        this.family = family;
        this.pane = pane;
    }

    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level,
                                           @NotNull BlockPos pos,
                                           @NotNull BlockState state,
                                           @NotNull ModelData modelData) {
        ModelData parent = originalModel.getModelData(level, pos, state, modelData);
        int packed = ConnectionMask.pack(level, pos, state);

        if (parent.getProperties().isEmpty()) {
            return cachedConnectionData(packed);
        }
        return parent.derive().with(CONNECTIONS, packed).build();
    }

    private static ModelData cachedConnectionData(int packed) {
        ModelData cached = CONNECTION_DATA.get(packed);
        if (cached != null) {
            return cached;
        }

        ModelData created = ModelData.builder().with(CONNECTIONS, packed).build();
        if (CONNECTION_DATA.compareAndSet(packed, null, created)) {
            return created;
        }
        return CONNECTION_DATA.get(packed);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState state,
                                              @Nullable Direction side,
                                              @NotNull RandomSource rand,
                                              @NotNull ModelData data,
                                              @Nullable RenderType renderType) {
        List<BakedQuad> original = originalModel.getQuads(state, side, rand, data, renderType);
        if (state == null || original.isEmpty()) {
            return original;
        }

        Integer packedObject = data.get(CONNECTIONS);
        if (packedObject == null) {
            return original;
        }
        int packed = packedObject;

        if (original.size() == 1) {
            BakedQuad quad = original.get(0);
            QuadVariants variants = quadCache.computeIfAbsent(quad, this::analyzeQuad);
            return variants.singleOutput(this, packed);
        }

        List<BakedQuad> result = new ArrayList<>(original.size() * 2);
        for (BakedQuad quad : original) {
            QuadVariants variants = quadCache.computeIfAbsent(quad, this::analyzeQuad);

            if (variants.paneEdge) {
                if (!variants.shouldCullPaneCap(packed)) {
                    result.add(quad);
                }
                continue;
            }

            result.add(variants.surface);
            int borderMask = variants.borderMask(packed);
            if (borderMask != 0xF) {
                result.add(variants.border(this, borderMask));
            }
        }
        return result;
    }

    private QuadVariants analyzeQuad(BakedQuad quad) {
        if (pane && isPaneEdgeTexture(quad.getSprite().contents().name())) {
            return QuadVariants.paneEdge(quad, paneCullRule(quad, capSegment(quad)));
        }

        QuadEdgeDirections edges = resolveQuadEdgeDirections(quad);
        return QuadVariants.surface(quad, makeSurfaceQuad(quad), precomputeBorderMasks(edges));
    }

    private static byte[] precomputeBorderMasks(QuadEdgeDirections edges) {
        byte[] masks = new byte[ConnectionMask.DIRECT_MASK + 1];
        int topBit = bitFor(edges.top());
        int rightBit = bitFor(edges.right());
        int bottomBit = bitFor(edges.bottom());
        int leftBit = bitFor(edges.left());

        for (int direct = 0; direct <= ConnectionMask.DIRECT_MASK; direct++) {
            int mask = 0;
            if (topBit != 0 && (direct & topBit) != 0) mask |= ConnectionMask.TOP;
            if (rightBit != 0 && (direct & rightBit) != 0) mask |= ConnectionMask.RIGHT;
            if (bottomBit != 0 && (direct & bottomBit) != 0) mask |= ConnectionMask.BOTTOM;
            if (leftBit != 0 && (direct & leftBit) != 0) mask |= ConnectionMask.LEFT;
            masks[direct] = (byte) mask;
        }
        return masks;
    }

    private static int bitFor(@Nullable Direction direction) {
        return direction == null ? 0 : ConnectionMask.directBit(direction);
    }

    private QuadEdgeDirections resolveQuadEdgeDirections(BakedQuad quad) {
        TextureAtlasSprite sprite = quad.getSprite();
        int[] data = quad.getVertices();
        Vertex[] vertices = new Vertex[4];

        for (int i = 0; i < 4; i++) {
            int base = i * IQuadTransformer.STRIDE;
            int pos = base + IQuadTransformer.POSITION;
            int uv = base + IQuadTransformer.UV0;
            vertices[i] = new Vertex(
                    Float.intBitsToFloat(data[pos]),
                    Float.intBitsToFloat(data[pos + 1]),
                    Float.intBitsToFloat(data[pos + 2]),
                    sprite.getUOffset(Float.intBitsToFloat(data[uv])),
                    sprite.getVOffset(Float.intBitsToFloat(data[uv + 1])));
        }

        return new QuadEdgeDirections(
                directionForUvEdge(vertices, quad.getDirection(), AxisUv.V, false),
                directionForUvEdge(vertices, quad.getDirection(), AxisUv.U, true),
                directionForUvEdge(vertices, quad.getDirection(), AxisUv.V, true),
                directionForUvEdge(vertices, quad.getDirection(), AxisUv.U, false));
    }

    private static Direction directionForUvEdge(Vertex[] vertices,
                                                Direction face,
                                                AxisUv uvAxis,
                                                boolean maximum) {
        float target = maximum ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        for (Vertex vertex : vertices) {
            float value = uvAxis == AxisUv.U ? vertex.u() : vertex.v();
            target = maximum ? Math.max(target, value) : Math.min(target, value);
        }

        Vertex first = null;
        Vertex second = null;
        for (Vertex vertex : vertices) {
            float value = uvAxis == AxisUv.U ? vertex.u() : vertex.v();
            if (Math.abs(value - target) <= UV_EPSILON) {
                if (first == null) first = vertex;
                else if (second == null) second = vertex;
            }
        }
        if (first == null || second == null) {
            return null;
        }
        return sharedBlockBoundary(first, second, face.getAxis());
    }

    private static Direction sharedBlockBoundary(Vertex a, Vertex b, Direction.Axis faceAxis) {
        if (faceAxis != Direction.Axis.X) {
            if (near(a.x(), 0.0F) && near(b.x(), 0.0F)) return Direction.WEST;
            if (near(a.x(), 1.0F) && near(b.x(), 1.0F)) return Direction.EAST;
        }
        if (faceAxis != Direction.Axis.Y) {
            if (near(a.y(), 0.0F) && near(b.y(), 0.0F)) return Direction.DOWN;
            if (near(a.y(), 1.0F) && near(b.y(), 1.0F)) return Direction.UP;
        }
        if (faceAxis != Direction.Axis.Z) {
            if (near(a.z(), 0.0F) && near(b.z(), 0.0F)) return Direction.NORTH;
            if (near(a.z(), 1.0F) && near(b.z(), 1.0F)) return Direction.SOUTH;
        }
        return null;
    }

    private static PaneCullRule paneCullRule(BakedQuad quad, CapSegment segment) {
        Direction vertical = quad.getDirection();
        if (vertical != Direction.UP && vertical != Direction.DOWN) {
            return PaneCullRule.NONE;
        }

        int directBit = ConnectionMask.directBit(vertical);
        if (segment == CapSegment.CENTER) {
            return new PaneCullRule(directBit, 0);
        }

        Direction horizontal = segment.direction();
        if (horizontal == null) {
            return PaneCullRule.NONE;
        }
        return new PaneCullRule(
                directBit,
                ConnectionMask.verticalSegmentPackedBit(vertical, horizontal));
    }

    private static CapSegment capSegment(BakedQuad quad) {
        int[] data = quad.getVertices();
        float minX = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < 4; i++) {
            int pos = i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION;
            float x = Float.intBitsToFloat(data[pos]);
            float z = Float.intBitsToFloat(data[pos + 2]);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        if (near(minZ, 0.0F)) return CapSegment.NORTH;
        if (near(maxZ, 1.0F)) return CapSegment.SOUTH;
        if (near(minX, 0.0F)) return CapSegment.WEST;
        if (near(maxX, 1.0F)) return CapSegment.EAST;

        if (minX > POSITION_EPSILON && maxX < 1.0F - POSITION_EPSILON
                && minZ > POSITION_EPSILON && maxZ < 1.0F - POSITION_EPSILON) {
            return CapSegment.CENTER;
        }
        return CapSegment.UNKNOWN;
    }

    private BakedQuad makeSurfaceQuad(BakedQuad quad) {
        TextureAtlasSprite source = quad.getSprite();
        int[] vertices = quad.getVertices().clone();

        for (int vertex = 0; vertex < 4; vertex++) {
            int base = vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
            float oldU = Float.intBitsToFloat(vertices[base]);
            float oldV = Float.intBitsToFloat(vertices[base + 1]);
            float localU = source.getUOffset(oldU);
            float localV = source.getVOffset(oldV);

            float newLocalU = SURFACE_INSET + localU * SURFACE_SCALE;
            float newLocalV = SURFACE_INSET + localV * SURFACE_SCALE;
            vertices[base] = Float.floatToRawIntBits(source.getU(newLocalU));
            vertices[base + 1] = Float.floatToRawIntBits(source.getV(newLocalV));
        }

        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), source,
                quad.isShade(), quad.hasAmbientOcclusion());
    }

    private BakedQuad makeBorderQuad(BakedQuad quad, int mask) {
        TextureAtlasSprite source = quad.getSprite();
        TextureAtlasSprite target = SpriteSet.get(family, mask);
        int[] vertices = quad.getVertices().clone();
        Direction face = quad.getDirection();

        for (int vertex = 0; vertex < 4; vertex++) {
            int vertexBase = vertex * IQuadTransformer.STRIDE;

            int position = vertexBase + IQuadTransformer.POSITION;
            float x = Float.intBitsToFloat(vertices[position]);
            float y = Float.intBitsToFloat(vertices[position + 1]);
            float z = Float.intBitsToFloat(vertices[position + 2]);
            x += face.getStepX() * BORDER_OFFSET;
            y += face.getStepY() * BORDER_OFFSET;
            z += face.getStepZ() * BORDER_OFFSET;
            vertices[position] = Float.floatToRawIntBits(x);
            vertices[position + 1] = Float.floatToRawIntBits(y);
            vertices[position + 2] = Float.floatToRawIntBits(z);

            int uv = vertexBase + IQuadTransformer.UV0;
            float oldU = Float.intBitsToFloat(vertices[uv]);
            float oldV = Float.intBitsToFloat(vertices[uv + 1]);
            float localU = source.getUOffset(oldU);
            float localV = source.getVOffset(oldV);
            vertices[uv] = Float.floatToRawIntBits(target.getU(localU));
            vertices[uv + 1] = Float.floatToRawIntBits(target.getV(localV));
        }

        return new BakedQuad(vertices, 0, face, target, quad.isShade(), quad.hasAmbientOcclusion());
    }

    private static boolean isPaneEdgeTexture(ResourceLocation texture) {
        String path = texture.getPath();
        return path.equals("block/glass_pane_top") || path.endsWith("_stained_glass_pane_top");
    }

    private static boolean near(float value, float target) {
        return Math.abs(value - target) <= POSITION_EPSILON;
    }

    private enum AxisUv { U, V }

    private enum CapSegment {
        CENTER(null),
        NORTH(Direction.NORTH),
        EAST(Direction.EAST),
        SOUTH(Direction.SOUTH),
        WEST(Direction.WEST),
        UNKNOWN(null);

        private final Direction direction;

        CapSegment(Direction direction) {
            this.direction = direction;
        }

        Direction direction() {
            return direction;
        }
    }

    private record Vertex(float x, float y, float z, float u, float v) { }

    private record QuadEdgeDirections(Direction top, Direction right, Direction bottom, Direction left) { }

    private record PaneCullRule(int directBit, int segmentBit) {
        private static final PaneCullRule NONE = new PaneCullRule(0, 0);

        boolean shouldCull(int packed) {
            if (directBit == 0 || (packed & directBit) == 0) {
                return false;
            }
            return segmentBit == 0 || (packed & segmentBit) != 0;
        }
    }

    private static final class QuadVariants {
        private static final List<BakedQuad> EMPTY = List.of();

        private final BakedQuad source;
        private final boolean paneEdge;
        private final BakedQuad surface;
        private final PaneCullRule paneCullRule;
        private final byte[] borderMasks;
        private final BakedQuad[] borders;
        private final List<BakedQuad>[] singleOutputs;
        private final List<BakedQuad> paneKept;

        @SuppressWarnings("unchecked")
        private QuadVariants(BakedQuad source,
                             boolean paneEdge,
                             BakedQuad surface,
                             PaneCullRule paneCullRule,
                             byte[] borderMasks) {
            this.source = source;
            this.paneEdge = paneEdge;
            this.surface = surface;
            this.paneCullRule = paneCullRule;
            this.borderMasks = borderMasks;
            this.borders = paneEdge ? null : new BakedQuad[0xF];
            this.singleOutputs = paneEdge ? null : (List<BakedQuad>[]) new List<?>[ConnectionMask.DIRECT_MASK + 1];
            this.paneKept = paneEdge ? List.of(source) : null;
        }

        static QuadVariants paneEdge(BakedQuad source, PaneCullRule rule) {
            return new QuadVariants(source, true, null, rule, null);
        }

        static QuadVariants surface(BakedQuad source, BakedQuad surface, byte[] borderMasks) {
            return new QuadVariants(source, false, surface, PaneCullRule.NONE, borderMasks);
        }

        boolean shouldCullPaneCap(int packed) {
            return paneEdge && paneCullRule.shouldCull(packed);
        }

        int borderMask(int packed) {
            return borderMasks[ConnectionMask.directBits(packed)] & 0xFF;
        }

        List<BakedQuad> singleOutput(ConnectedGlassModel owner, int packed) {
            if (paneEdge) {
                return paneCullRule.shouldCull(packed) ? EMPTY : paneKept;
            }

            int direct = ConnectionMask.directBits(packed);
            List<BakedQuad> cached = singleOutputs[direct];
            if (cached != null) {
                return cached;
            }

            synchronized (this) {
                cached = singleOutputs[direct];
                if (cached == null) {
                    int mask = borderMasks[direct] & 0xFF;
                    cached = mask == 0xF
                            ? List.of(surface)
                            : List.of(surface, border(owner, mask));
                    singleOutputs[direct] = cached;
                }
                return cached;
            }
        }

        BakedQuad border(ConnectedGlassModel owner, int mask) {
            if (mask < 0 || mask >= 0xF) {
                throw new IllegalArgumentException("Border mask must be 0..14, got " + mask);
            }

            BakedQuad cached = borders[mask];
            if (cached != null) {
                return cached;
            }

            synchronized (this) {
                cached = borders[mask];
                if (cached == null) {
                    cached = owner.makeBorderQuad(source, mask);
                    borders[mask] = cached;
                }
                return cached;
            }
        }
    }
}
