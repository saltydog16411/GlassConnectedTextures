package io.github.saltydog16411.glassconnectedtextures.client;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.state.BlockState;

final class ConnectionMask {
    static final int TOP = 1;
    static final int RIGHT = 2;
    static final int BOTTOM = 4;
    static final int LEFT = 8;

    // Bits 0..5: directly adjacent supported glass in Direction#get3DDataValue order.
    // Bits 6..9: horizontal pane segments present in the pane directly above.
    // Bits 10..13: horizontal pane segments present in the pane directly below.
    // Segment order is NORTH, EAST, SOUTH, WEST.
    static final int DIRECT_MASK = 0x3F;
    static final int PACKED_BITS = 14;
    static final int PACKED_VARIANT_COUNT = 1 << PACKED_BITS;

    private static final int ABOVE_SEGMENTS_SHIFT = 6;
    private static final int BELOW_SEGMENTS_SHIFT = 10;

    private ConnectionMask() { }

    static int pack(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        Block self = state.getBlock();
        boolean pane = GlassTargets.isPane(self);

        // Exactly six world reads, no scans. One mutable position replaces six
        // temporary BlockPos allocations. Up/down states are also reused for
        // pane-cap segment tests, so no duplicate world reads are needed.
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockState down = stateAt(level, pos, cursor, Direction.DOWN);
        BlockState up = stateAt(level, pos, cursor, Direction.UP);
        BlockState north = stateAt(level, pos, cursor, Direction.NORTH);
        BlockState south = stateAt(level, pos, cursor, Direction.SOUTH);
        BlockState west = stateAt(level, pos, cursor, Direction.WEST);
        BlockState east = stateAt(level, pos, cursor, Direction.EAST);

        int packed = 0;
        packed = setDirect(packed, Direction.DOWN, GlassTargets.connects(pane, down.getBlock()));
        packed = setDirect(packed, Direction.UP, GlassTargets.connects(pane, up.getBlock()));
        packed = setDirect(packed, Direction.NORTH, GlassTargets.connects(pane, north.getBlock()));
        packed = setDirect(packed, Direction.SOUTH, GlassTargets.connects(pane, south.getBlock()));
        packed = setDirect(packed, Direction.WEST, GlassTargets.connects(pane, west.getBlock()));
        packed = setDirect(packed, Direction.EAST, GlassTargets.connects(pane, east.getBlock()));

        if (pane) {
            packed |= (paneSegments(up) & 0xF) << ABOVE_SEGMENTS_SHIFT;
            packed |= (paneSegments(down) & 0xF) << BELOW_SEGMENTS_SHIFT;
        }
        return packed;
    }

    static int directBits(int packed) {
        return packed & DIRECT_MASK;
    }

    static int directBit(Direction direction) {
        return 1 << direction.get3DDataValue();
    }

    static int verticalSegmentPackedBit(Direction vertical, Direction horizontal) {
        if (vertical != Direction.UP && vertical != Direction.DOWN) {
            return 0;
        }
        int shift = vertical == Direction.UP ? ABOVE_SEGMENTS_SHIFT : BELOW_SEGMENTS_SHIFT;
        return segmentBit(horizontal) << shift;
    }

    private static BlockState stateAt(BlockAndTintGetter level,
                                      BlockPos origin,
                                      BlockPos.MutableBlockPos cursor,
                                      Direction direction) {
        cursor.set(
                origin.getX() + direction.getStepX(),
                origin.getY() + direction.getStepY(),
                origin.getZ() + direction.getStepZ());
        return level.getBlockState(cursor);
    }

    private static int setDirect(int packed, Direction direction, boolean connected) {
        return connected ? packed | directBit(direction) : packed;
    }

    private static int paneSegments(BlockState state) {
        if (!GlassTargets.isPane(state.getBlock())) {
            return 0;
        }

        int mask = 0;
        if (state.getValue(CrossCollisionBlock.NORTH)) mask |= 1;
        if (state.getValue(CrossCollisionBlock.EAST)) mask |= 2;
        if (state.getValue(CrossCollisionBlock.SOUTH)) mask |= 4;
        if (state.getValue(CrossCollisionBlock.WEST)) mask |= 8;
        return mask;
    }

    private static int segmentBit(Direction horizontal) {
        return switch (horizontal) {
            case NORTH -> 1;
            case EAST -> 2;
            case SOUTH -> 4;
            case WEST -> 8;
            default -> 0;
        };
    }
}
