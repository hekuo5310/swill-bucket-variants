package com.hekuo.swillvariants;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.block.enums.NoteBlockInstrument;
import net.minecraft.item.BlockItem;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SwillBucketBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");
    private static final VoxelShape SHAPE = VoxelShapes.union(
            createCuboidShape(0, 0, 0, 16, 2, 16), createCuboidShape(0, 0, 0, 2, 16, 16),
            createCuboidShape(14, 0, 0, 16, 16, 16), createCuboidShape(2, 0, 0, 14, 16, 2),
            createCuboidShape(2, 0, 14, 14, 16, 16));

    private final SwillBucketVariants.Oxidation oxidation;
    private final boolean waxed;

    public SwillBucketBlock(Settings settings, SwillBucketVariants.Oxidation oxidation, boolean waxed) {
        super(settings);
        this.oxidation = oxidation;
        this.waxed = waxed;
        setDefaultState(getStateManager().getDefaultState().with(POWERED, false));
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return SHAPE; }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos,
                                               PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isOf(Items.HONEYCOMB) && !waxed) {
            if (!world.isClient) {
                replaceKeepingContents(world, pos, SwillBucketVariants.version(oxidation, true).getDefaultState().with(POWERED, state.get(POWERED)));
                if (!player.isCreative()) stack.decrement(1);
                world.syncWorldEvent(player, 3003, pos, 0);
            }
            return ItemActionResult.success(world.isClient);
        }
        if (stack.getItem() instanceof AxeItem) {
            SwillBucketBlock target = waxed ? SwillBucketVariants.version(oxidation, false) : previousOxidation();
            if (target != null) {
                if (!world.isClient) {
                    replaceKeepingContents(world, pos, target.getDefaultState().with(POWERED, state.get(POWERED)));
                    stack.damage(1, player, hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
                    world.playSound(null, pos, waxed ? SoundEvents.ITEM_AXE_WAX_OFF : SoundEvents.ITEM_AXE_SCRAPE,
                            SoundCategory.BLOCKS, 1.0F, 1.0F);
                    world.syncWorldEvent(player, waxed ? 3004 : 3005, pos, 0);
                }
                return ItemActionResult.success(world.isClient);
            }
        }
        if (world.getBlockEntity(pos) instanceof SwillBucketBlockEntity bucket && bucket.useItem(stack, player, hand))
            return ItemActionResult.success(world.isClient);
        return ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.getBlockEntity(pos) instanceof SwillBucketBlockEntity bucket && bucket.useEmptyHand(player))
            return ActionResult.success(world.isClient);
        return ActionResult.PASS;
    }

    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (waxed || oxidation == SwillBucketVariants.Oxidation.OXIDIZED || random.nextFloat() >= 0.05688889F) return;
        var next = SwillBucketVariants.Oxidation.values()[oxidation.ordinal() + 1];
        replaceKeepingContents(world, pos, SwillBucketVariants.version(next, false).getDefaultState().with(POWERED, state.get(POWERED)));
    }

    @Override protected boolean hasComparatorOutput(BlockState state) { return true; }

    @Override protected boolean emitsRedstonePower(BlockState state) { return true; }

    @Override protected int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, net.minecraft.util.math.Direction direction) {
        return state.get(POWERED) ? 15 : 0;
    }

    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (state.get(POWERED)) {
            world.setBlockState(pos, state.with(POWERED, false), NOTIFY_ALL);
            world.updateNeighbors(pos, this);
        }
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(POWERED); }

    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        ItemStack dropped = new ItemStack(this);
        BlockEntity blockEntity = builder.getOptional(LootContextParameters.BLOCK_ENTITY);
        if (blockEntity instanceof SwillBucketBlockEntity bucket && !bucket.isEmpty()) {
            BlockItem.setBlockEntityData(dropped, SwillBucketVariants.BLOCK_ENTITY,
                    bucket.createNbtWithId(builder.getWorld().getRegistryManager()));
        }
        return List.of(dropped);
    }

    @Override
    protected int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof SwillBucketBlockEntity bucket ? bucket.getComparatorOutput() : 0;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && newState.getBlock() instanceof SwillBucketBlock) {
            return;
        }
        if (!state.isOf(newState.getBlock())) {
            world.updateComparators(pos, this);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    private @Nullable SwillBucketBlock previousOxidation() {
        if (oxidation == SwillBucketVariants.Oxidation.UNAFFECTED) return null;
        return SwillBucketVariants.version(SwillBucketVariants.Oxidation.values()[oxidation.ordinal() - 1], false);
    }

    private static void replaceKeepingContents(World world, BlockPos pos, BlockState replacement) {
        SwillBucketBlockEntity oldBucket = world.getBlockEntity(pos) instanceof SwillBucketBlockEntity bucket ? bucket : null;
        world.setBlockState(pos, replacement, NOTIFY_ALL);
        if (oldBucket != null && world.getBlockEntity(pos) instanceof SwillBucketBlockEntity newBucket && newBucket != oldBucket) {
            oldBucket.copyContentsTo(newBucket);
        }
    }

    @Override public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new SwillBucketBlockEntity(pos, state); }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : validateTicker(type, SwillBucketVariants.BLOCK_ENTITY, SwillBucketBlockEntity::tick);
    }

    @SuppressWarnings("unchecked")
    private static <E extends BlockEntity, A extends BlockEntity> BlockEntityTicker<A> validateTicker(
            BlockEntityType<A> given, BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return expected == given ? (BlockEntityTicker<A>) ticker : null;
    }
}
