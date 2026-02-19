package net.pinkcats.createlazytick.mixin.OptElement.itemdrain;

import com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity;
import com.simibubi.create.content.fluids.transfer.GenericItemEmptying;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.pinkcats.createlazytick.config.ServerConfig;
import net.pinkcats.createlazytick.bridge.Create.ISmartBlockEntityControl;
import net.pinkcats.createlazytick.helper.util.LazyTickLogic;
import net.pinkcats.createlazytick.helper.LazyTickScrollBehaviour;
import net.pinkcats.createlazytick.helper.NetworkSyncHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = ItemDrainBlockEntity.class,remap = false)
public abstract class ItemDrainLazyTickMixin extends SmartBlockEntity {

    @Unique
    private int createLazyTick$itemDrainTick = 0;

    public ItemDrainLazyTickMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Inject(method = "addBehaviours", at = @At("RETURN"), remap = false)
    private void lazytick$addScrollBehaviour(List<BlockEntityBehaviour> behaviours, CallbackInfo ci) {
        LazyTickScrollBehaviour.addTo(this, behaviours);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lcom/simibubi/create/foundation/blockEntity/SmartBlockEntity;tick()V", shift = At.Shift.AFTER), cancellable = true, remap = false)
    public void optimizedTick(CallbackInfo ci) {

        if (!ServerConfig.getEnableLazyTick() || !ServerConfig.getEnableLazyItemDrain()) {
            return;
        }

        ISmartBlockEntityControl control = (ISmartBlockEntityControl) this;

        NetworkSyncHelper.createLazyTick$syncPacketData(control,
                this.level, this.worldPosition, control.createLazyTick$getCurrentSuperTick(), ServerConfig.getItemDrainDelayMax());

        ItemDrainAccessor accessor = (ItemDrainAccessor) this;
        TransportedItemStack heldItem = accessor.getHeldItem();
        int processingTicks = accessor.getProcessingTicks();

        if (heldItem == null) {
            accessor.setProcessingTicks(0);
            createLazyTick$resetDelayTick(); 
            ci.cancel();
            return;
        }

        createLazyTick$itemDrainTick++;
        if (createLazyTick$itemDrainTick < control.createLazyTick$getCurrentSuperTick()) {
            ci.cancel();
            return;
        }
        createLazyTick$itemDrainTick = 0;

        boolean onClient = level != null && level.isClientSide && !isVirtual();

        if (level == null) {
            ci.cancel();
            return;
        }

        if (processingTicks > 0) {
            heldItem.prevBeltPosition = .5f;
            boolean wasAtBeginning = processingTicks == ItemDrainBlockEntity.FILLING_TIME;
            if (!onClient) {

                int interval = control.createLazyTick$getCurrentSuperTick();
                boolean success = createLazyTick$performLazyDrain(accessor, interval);

                if (!success) {
                    accessor.setProcessingTicks(0);
                    notifyUpdate();
                    ci.cancel();
                    return;
                }

                if (accessor.getProcessingTicks() == ItemDrainBlockEntity.FILLING_TIME) {
                    createLazyTick$applyBackoff(); 
                    ci.cancel();
                    return;
                }

                if (accessor.getProcessingTicks() > 0) {
                    createLazyTick$resetDelayTick();
                    if (wasAtBeginning != (accessor.getProcessingTicks() == ItemDrainBlockEntity.FILLING_TIME))
                        this.sendData();
                    ci.cancel();
                    return;
                }
            }

            createLazyTick$resetDelayTick();

            if (wasAtBeginning != (accessor.getProcessingTicks() == ItemDrainBlockEntity.FILLING_TIME))
                this.sendData();

            ci.cancel();
            return;
        }

        heldItem.prevBeltPosition = heldItem.beltPosition;
        heldItem.prevSideOffset = heldItem.sideOffset;

        int currentInterval = control.createLazyTick$getCurrentSuperTick();
        float movementSpeed = 1 / 8f;
        float proposedDist = movementSpeed * currentInterval;
        float targetPos = heldItem.beltPosition + proposedDist;

        boolean crossingCenter = heldItem.beltPosition < 0.5f && targetPos >= 0.5f;

        if (crossingCenter && GenericItemEmptying.canItemBeEmptied(level, heldItem.stack)) {

            heldItem.beltPosition = 0.5f;
        } else {

            heldItem.beltPosition = targetPos;
        }

        if (heldItem.beltPosition > 1) {
            heldItem.beltPosition = 1;

            if (onClient) {
                ci.cancel();
                return;
            }

            Direction side = heldItem.insertedFrom;

            ItemStack tryExportingToBeltFunnel = getBehaviour(DirectBeltInputBehaviour.TYPE)
                    .tryExportingToBeltFunnel(heldItem.stack, side.getOpposite(), false);
            if (tryExportingToBeltFunnel != null) {
                if (tryExportingToBeltFunnel.getCount() != heldItem.stack.getCount()) {
                    if (tryExportingToBeltFunnel.isEmpty())
                        accessor.setHeldItem(null);
                    else
                        heldItem.stack = tryExportingToBeltFunnel;
                    notifyUpdate();

                    createLazyTick$resetDelayTick();

                    ci.cancel();
                    return;
                }
                if (!tryExportingToBeltFunnel.isEmpty()) {

                    createLazyTick$applyBackoff(); 
                    ci.cancel();
                    return;

                }
            }

            BlockPos nextPosition = worldPosition.relative(side);
            DirectBeltInputBehaviour directBeltInputBehaviour =
                    BlockEntityBehaviour.get(level, nextPosition, DirectBeltInputBehaviour.TYPE);

            if (directBeltInputBehaviour == null) {
                if (!BlockHelper.hasBlockSolidSide(level.getBlockState(nextPosition), level, nextPosition,
                        side.getOpposite())) {
                    ItemStack ejected = heldItem.stack;
                    Vec3 outPos = VecHelper.getCenterOf(worldPosition)
                            .add(Vec3.atLowerCornerOf(side.getNormal()).scale(.75));
                    Vec3 outMotion = Vec3.atLowerCornerOf(side.getNormal())
                            .scale(1 / 8f).add(0, 1 / 8f, 0);
                    outPos = outPos.add(outMotion.normalize());
                    ItemEntity entity = new ItemEntity(level, outPos.x, outPos.y + 6 / 16f, outPos.z, ejected);
                    entity.setDeltaMovement(outMotion);
                    entity.setDefaultPickUpDelay();
                    entity.hurtMarked = true;
                    level.addFreshEntity(entity);

                    accessor.setHeldItem(null);
                    notifyUpdate();

                    createLazyTick$resetDelayTick();
                } else {
                    createLazyTick$applyBackoff(); 
                }
                ci.cancel();
                return;
            }

            if (!directBeltInputBehaviour.canInsertFromSide(side)) {
                createLazyTick$applyBackoff(); 
                ci.cancel();
                return;
            }

            ItemStack returned = directBeltInputBehaviour.handleInsertion(heldItem.copy(), side, false);

            if (returned.isEmpty()) {
                if (level.getBlockEntity(nextPosition) instanceof ItemDrainBlockEntity)
                    award(AllAdvancements.CHAINED_DRAIN);
                accessor.setHeldItem(null);
                notifyUpdate();

                createLazyTick$resetDelayTick();

                ci.cancel();
                return;
            }

            if (returned.getCount() == heldItem.stack.getCount()) {
                createLazyTick$applyBackoff(); 
                ci.cancel();
                return;
            }

            if (returned.getCount() != heldItem.stack.getCount()) {
                heldItem.stack = returned;
                notifyUpdate();

                createLazyTick$resetDelayTick();

                ci.cancel();
                return;
            }

            ci.cancel();
            return;
        }

        if (heldItem.prevBeltPosition < .5f && heldItem.beltPosition >= .5f) {
            if (!GenericItemEmptying.canItemBeEmptied(level, heldItem.stack)) {
                ci.cancel();
                return;
            }
            heldItem.beltPosition = .5f;
            if (onClient) {
                ci.cancel();
                return;
            }
            accessor.setProcessingTicks(ItemDrainBlockEntity.FILLING_TIME);
            this.sendData();
        }

        ci.cancel();
    }

    @Unique
    private void createLazyTick$applyBackoff() {
        ISmartBlockEntityControl control = (ISmartBlockEntityControl) this;
        createLazyTick$itemDrainTick = 0;

        int currentInterval = control.createLazyTick$getCurrentSuperTick();
        int newInterval = LazyTickLogic.computeNextInterval(control, currentInterval, ServerConfig.getItemDrainDelayMax());
        if (newInterval != currentInterval) {
            LazyTickLogic.setIntervalSafe(control, newInterval);
        }
    }

    @Unique
    private void createLazyTick$resetDelayTick() {
        ISmartBlockEntityControl control = (ISmartBlockEntityControl) this;
        createLazyTick$itemDrainTick = 0;

        LazyTickLogic.setIntervalSafe(control, 1);
    }

    @Unique
    private static boolean createLazyTick$performLazyDrain(ItemDrainAccessor accessor, int interval) {
        int currentTicks = accessor.getProcessingTicks();
        int targetTicks = Math.max(0, currentTicks - interval);

        boolean crossingThreshold = currentTicks > 5 && targetTicks <= 5;

        if (crossingThreshold) {

            accessor.setProcessingTicks(6);
            if (!accessor.invokeContinueProcessing()) {
                return false; 
            }

            if (accessor.getProcessingTicks() == ItemDrainBlockEntity.FILLING_TIME) {
                return true; 
            }

            accessor.setProcessingTicks(5);
            if (!accessor.invokeContinueProcessing()) {
                return false; 
            }

        }

        accessor.setProcessingTicks(targetTicks);

        return accessor.invokeContinueProcessing();
    }
}