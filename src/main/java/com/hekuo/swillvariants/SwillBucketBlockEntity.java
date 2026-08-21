package com.hekuo.swillvariants;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ComposterBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.SidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.BlockItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public final class SwillBucketBlockEntity extends BlockEntity implements SidedInventory {
    public static final int CAPACITY = 20;
    private static final int[] SLOTS = java.util.stream.IntStream.range(0, CAPACITY).toArray();
    private final DefaultedList<ItemStack> foods = DefaultedList.ofSize(CAPACITY, ItemStack.EMPTY);
    private int heatTicks;
    private int nutritionPool;
    private float saturationPool;

    public SwillBucketBlockEntity(BlockPos pos, BlockState state) {
        super(SwillBucketVariants.BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, SwillBucketBlockEntity bucket) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (isHot(world.getBlockState(pos.down()))) {
            bucket.heatTicks++;
            if (bucket.heatTicks % 40 == 0 && !bucket.isEmpty()) {
                serverWorld.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        pos.getX() + .5, pos.getY() + 1.05, pos.getZ() + .5, 1, .12, .03, .12, .002);
            }
            if (bucket.heatTicks >= 600) {
                bucket.heatTicks = 0;
                bucket.cookOne(serverWorld);
            }
        } else bucket.heatTicks = 0;
    }

    private static boolean isHot(BlockState state) {
        return state.isOf(Blocks.FIRE) || state.isOf(Blocks.SOUL_FIRE) || state.isOf(Blocks.LAVA)
                || state.isOf(Blocks.MAGMA_BLOCK) || state.isIn(BlockTags.CAMPFIRES);
    }

    private void cookOne(ServerWorld world) {
        for (int i = 0; i < CAPACITY; i++) {
            ItemStack input = foods.get(i);
            if (input.isEmpty()) continue;
            var match = world.getRecipeManager().getFirstMatch(net.minecraft.recipe.RecipeType.SMELTING,
                    new net.minecraft.recipe.input.SingleStackRecipeInput(input), world);
            if (match.isPresent()) {
                ItemStack result = match.get().value().craft(new net.minecraft.recipe.input.SingleStackRecipeInput(input),
                        world.getRegistryManager());
                if (!result.isEmpty()) {
                    foods.set(i, result.copyWithCount(1));
                    markChanged();
                    world.playSound(null, pos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, .3F, 1.8F);
                    return;
                }
            }
        }
    }

    public boolean useItem(ItemStack stack, PlayerEntity player, Hand hand) {
        if (world == null || stack.isEmpty()) return false;
        if (isFoodPoolLocked()) {
            if (player.isSneaking()) return produceBoneMeal(player);
            return feedPlayer(player);
        }
        if (player.isSneaking()) return takeTop(player);
        if (isFull() || !canCompost(stack)) return false;
        if (!world.isClient) {
            ItemStack inserted = stack.copyWithCount(1);
            foods.set(firstEmptySlot(), inserted);
            if (!player.isCreative()) stack.decrement(1);
            resetPool();
            changedWithPulse();
            world.playSound(null, pos, SoundEvents.BLOCK_COMPOSTER_FILL_SUCCESS, SoundCategory.BLOCKS, .8F, 1.0F);
        }
        return true;
    }

    public boolean useEmptyHand(PlayerEntity player) {
        if (world == null || isEmpty()) return false;
        if (isFoodPoolLocked()) {
            if (player.isSneaking()) return produceBoneMeal(player);
            return feedPlayer(player);
        }
        if (player.isSneaking()) {
            return takeTop(player);
        }
        return feedPlayer(player);
    }

    private boolean produceBoneMeal(PlayerEntity player) {
        if (!world.isClient) {
            player.getInventory().offerOrDrop(new ItemStack(Items.BONE_MEAL));
            clear();
            world.playSound(null, pos, SoundEvents.BLOCK_COMPOSTER_EMPTY, SoundCategory.BLOCKS, 1F, 1F);
            if (world instanceof ServerWorld serverWorld) serverWorld.syncWorldEvent(1500, pos, 0);
        }
        return true;
    }

    private boolean feedPlayer(PlayerEntity player) {
        if (!isFoodPoolLocked()) return false;
        if (!world.isClient) {
            ensurePool();
            int missing = Math.max(0, 20 - player.getHungerManager().getFoodLevel());
            if (missing == 0) return consumeTop(player);
            Pool before = calculatePool();
            int remaining = sizeUsed();
            Pool after = before;
            while (remaining > 0 && before.nutrition - after.nutrition < missing) {
                after = calculatePool(--remaining);
            }
            int eaten = Math.max(0, before.nutrition - after.nutrition);
            if (eaten <= 0) return false;
            float saturation = Math.max(0, before.saturation - after.saturation);
            int foodLevel = Math.min(20, player.getHungerManager().getFoodLevel() + Math.min(missing, eaten));
            player.getHungerManager().setFoodLevel(foodLevel);
            player.getHungerManager().setSaturationLevel(
                    Math.min(foodLevel, player.getHungerManager().getSaturationLevel() + saturation));
            for (int i = remaining; i < CAPACITY; i++) foods.set(i, ItemStack.EMPTY);
            if (remaining == 0) clear(); else { resetPool(); changedWithPulse(); }
            world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, .8F, .95F);
        }
        return true;
    }

    private boolean consumeTop(PlayerEntity player) {
        int slot = lastOccupiedSlot();
        if (slot < 0) return false;
        ItemStack consumed = foods.get(slot);
        FoodComponent food = consumed.get(DataComponentTypes.FOOD);
        if (food != null) {
            player.getHungerManager().setSaturationLevel(
                    Math.min(player.getHungerManager().getFoodLevel(),
                            player.getHungerManager().getSaturationLevel() + food.saturation()));
        }
        foods.set(slot, ItemStack.EMPTY);
        resetPool();
        if (sizeUsed() == 0) clear(); else changedWithPulse();
        world.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.PLAYERS, .8F, .95F);
        return true;
    }

    private boolean takeTop(PlayerEntity player) {
        if (world == null || isEmpty()) return false;
        if (!world.isClient) {
            int slot = lastOccupiedSlot();
            player.getInventory().offerOrDrop(removeStack(slot));
            resetPool();
            changedWithPulse();
            world.playSound(null, pos, SoundEvents.BLOCK_COMPOSTER_EMPTY, SoundCategory.BLOCKS, .8F, 1F);
        }
        return true;
    }

    private void ensurePool() {
        if (nutritionPool > 0) return;
        Pool pool = calculatePool();
        nutritionPool = pool.nutrition;
        saturationPool = pool.saturation;
    }

    private boolean isFoodPoolLocked() { return isFull() && calculatePool().nutrition > 0; }

    private static boolean canCompost(ItemStack stack) {
        return stack.contains(DataComponentTypes.FOOD)
                || ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE.getFloat(stack.getItem()) > 0.0F;
    }

    private Pool calculatePool() {
        return calculatePool(sizeUsed());
    }

    private Pool calculatePool(int layers) {
        int foodNutrition = 0;
        float foodSaturation = 0.0F;
        int compostableItems = 0;
        int compostableBlocks = 0;
        for (int i = 0; i < Math.min(layers, CAPACITY); i++) {
            ItemStack stack = foods.get(i);
            if (stack.isEmpty()) continue;
            FoodComponent food = stack.get(DataComponentTypes.FOOD);
            if (food != null) {
                foodNutrition += food.nutrition();
                foodSaturation += food.saturation();
            } else if (stack.getItem() instanceof BlockItem) {
                compostableBlocks++;
            } else {
                compostableItems++;
            }
        }
        int servings = (compostableItems + 3) / 4 + (compostableBlocks + 2) / 3;
        return new Pool(foodNutrition + servings, foodSaturation + servings * 0.5F);
    }

    private record Pool(int nutrition, float saturation) {}
    private boolean isFull() { return sizeUsed() >= CAPACITY; }
    private int sizeUsed() { int count = 0; for (ItemStack stack : foods) if (!stack.isEmpty()) count++; return count; }
    private int firstEmptySlot() { for (int i = 0; i < CAPACITY; i++) if (foods.get(i).isEmpty()) return i; return -1; }
    private int lastOccupiedSlot() { for (int i = CAPACITY - 1; i >= 0; i--) if (!foods.get(i).isEmpty()) return i; return -1; }
    private void resetPool() { nutritionPool = 0; saturationPool = 0; }
    public int getComparatorOutput() { return (int) Math.floor(15.0 * sizeUsed() / CAPACITY); }

    private void changedWithPulse() {
        markDirty();
        if (world != null) {
            BlockState state = world.getBlockState(pos);
            if (state.contains(SwillBucketBlock.POWERED) && !state.get(SwillBucketBlock.POWERED)) {
                world.setBlockState(pos, state.with(SwillBucketBlock.POWERED, true), 3);
                world.scheduleBlockTick(pos, state.getBlock(), 2);
            }
            world.updateComparators(pos, getCachedState().getBlock());
            world.updateNeighbors(pos, getCachedState().getBlock());
            world.updateNeighbors(pos.down(), getCachedState().getBlock());
            if (world instanceof ServerWorld serverWorld) serverWorld.getChunkManager().markForUpdate(pos);
        }
    }

    private void markChanged() { changedWithPulse(); }

    public void copyContentsTo(SwillBucketBlockEntity target) {
        for (int i = 0; i < CAPACITY; i++) target.foods.set(i, foods.get(i).copy());
        target.heatTicks = heatTicks;
        target.nutritionPool = nutritionPool;
        target.saturationPool = saturationPool;
        target.markDirty();
    }

    public DefaultedList<ItemStack> getRenderedFoods() { return foods; }

    @Override public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    @Override public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        Inventories.writeNbt(nbt, foods, registries);
        nbt.putInt("HeatTicks", heatTicks);
        nbt.putInt("NutritionPool", nutritionPool);
        nbt.putFloat("SaturationPool", saturationPool);
    }

    @Override protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        Inventories.readNbt(nbt, foods, registries);
        heatTicks = nbt.getInt("HeatTicks");
        nutritionPool = nbt.getInt("NutritionPool");
        saturationPool = nbt.getFloat("SaturationPool");
    }

    @Override public int[] getAvailableSlots(Direction side) { return SLOTS; }
    @Override public boolean canInsert(int slot, ItemStack stack, Direction dir) { return !isFull() && canCompost(stack); }
    @Override public boolean canExtract(int slot, ItemStack stack, Direction dir) { return dir != Direction.UP && slot == lastOccupiedSlot(); }
    @Override public int size() { return CAPACITY; }
    @Override public boolean isEmpty() { return sizeUsed() == 0; }
    @Override public ItemStack getStack(int slot) { return foods.get(slot); }
    @Override public ItemStack removeStack(int slot, int amount) { ItemStack result = Inventories.splitStack(foods, slot, amount); if (!result.isEmpty()) changedWithPulse(); return result; }
    @Override public ItemStack removeStack(int slot) { ItemStack result = Inventories.removeStack(foods, slot); if (!result.isEmpty()) changedWithPulse(); return result; }
    @Override public void setStack(int slot, ItemStack stack) { foods.set(slot, stack.copyWithCount(Math.min(1, stack.getCount()))); resetPool(); changedWithPulse(); }
    @Override public boolean canPlayerUse(PlayerEntity player) { return InventoryUtil.canPlayerUse(this, player); }
    @Override public void clear() { foods.clear(); resetPool(); changedWithPulse(); }
}
