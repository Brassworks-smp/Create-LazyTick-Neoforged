package net.pinkcats.createlazytick.mixin.OptElement.belt;

import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.BeltSlope;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.belt.transport.*;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.foundation.utility.ServerSpeedProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.pinkcats.createlazytick.bridge.Create.ISmartBlockEntityControl;
import net.pinkcats.createlazytick.config.ServerConfig;
import net.pinkcats.createlazytick.bridge.BeltEum;
import net.pinkcats.createlazytick.helper.util.LazyTickLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static com.simibubi.create.content.kinetics.belt.transport.BeltTunnelInteractionHandler.flapTunnel;

@Mixin(value = BeltInventory.class,remap = false)
public class BeltTickMixin {

    @Shadow(remap = false)
    boolean beltMovementPositive;

    @Shadow(remap = false)
    TransportedItemStack lazyClientItem;

    @Shadow(remap = false)
    private void insert(TransportedItemStack newStack) {}

    @Shadow(remap = false)
    public void eject(TransportedItemStack stack) {}

    @Shadow(remap = false)
    protected boolean handleBeltProcessingAndCheckIfRemoved(TransportedItemStack currentItem, float nextOffset,
                                                            boolean noMovement) {return false;}
    @Unique
    private void createLazyTick$applyBackoff(ISmartBlockEntityControl control) {
        int currentLazyTickInterval = control.createLazyTick$getCurrentSuperTick();
        int newLazyTickInterval = LazyTickLogic.computeNextInterval(
                control, currentLazyTickInterval, ServerConfig.getBeltDelayMax()
        );
        if (newLazyTickInterval != currentLazyTickInterval) {
            LazyTickLogic.setIntervalSafe(control, newLazyTickInterval);
        }
    }

    @Unique
    private void createLazyTick$resetDelayTick(ISmartBlockEntityControl control) {

        LazyTickLogic.setIntervalSafe(control, 1);
    }

    @Unique
    private void createLazyTick$resetDelayCounters() {
        CLT$BeltCurrentTick = 0;
        CLT$BeltSlideTick = 0;
    }

    @Unique
    private int CLT$BeltCurrentTick = 0;
    @Unique
    private int CLT$BeltSlideTick = 0;
    @Unique
    private int CLT$HasItemCount = 0;
    @Unique
    private int CLT$LastItemListSize = 0;

    @Inject(method = "tick" ,at=@At("HEAD" ),cancellable = true,remap = false)
    public void tick(CallbackInfo ci) {
        if (!ServerConfig.getEnableLazyTick() || !ServerConfig.getEnableLazyBelt()) {
            return;
        }

        BeltInventoryAccessor accessor = (BeltInventoryAccessor) this;
        BeltInventory OrginalBeltInventory = (BeltInventory) (Object) this;

        BeltBlockEntity belt = accessor.getBelt();
        List<TransportedItemStack> toInsert = accessor.getToInsert();
        List<TransportedItemStack> toRemove = accessor.getToRemove();
        List<TransportedItemStack> items = accessor.getItems();

        if (createLazyTick$drainPendingTransfers(belt, toInsert, toRemove, items)) {
            createLazyTick$resetDelayCounters();
        }

        if (items.size() != CLT$LastItemListSize) {
            CLT$LastItemListSize = items.size();
            createLazyTick$resetDelayCounters();
        }

        if (CLT$BeltCurrentTick >= CLT$BeltSlideTick){
            CLT$BeltCurrentTick = 0;
        } else {
            CLT$BeltCurrentTick++;
            if (CLT$BeltSlideTick > 20)
            {ci.cancel();
                return;}
        }

        if (lazyClientItem != null) {
            if (lazyClientItem.locked)
                lazyClientItem = null;
            else
                lazyClientItem.locked = true;
        }

        if (belt.getSpeed() == 0) {
            ci.cancel();
            return;
        }

        if (beltMovementPositive != belt.getDirectionAwareBeltMovementSpeed() > 0) {
            beltMovementPositive = !beltMovementPositive;
            Collections.reverse(items);
            belt.notifyUpdate();

        }

        TransportedItemStack stackInFront = null;
        TransportedItemStack currentItem = null;
        Iterator<TransportedItemStack> iterator = items.iterator();

        float beltSpeed = belt.getDirectionAwareBeltMovementSpeed();
        Direction movementFacing = belt.getMovementFacing();
        boolean horizontal = belt.getBlockState()
                .getValue(BeltBlock.SLOPE) == BeltSlope.HORIZONTAL;
        float spacing = 1;
        Level world = belt.getLevel();
        boolean onClient = false;
        if (world != null) {
            onClient = world.isClientSide && !belt.isVirtual();
        }

        BeltEum.Ending ending = BeltEum.Ending.UNRESOLVED;

        int count = 0;
        int stop_count = 0;
        while (iterator.hasNext()) {

            stackInFront = currentItem;
            currentItem = iterator.next();
            currentItem.prevBeltPosition = currentItem.beltPosition;
            currentItem.prevSideOffset = currentItem.sideOffset;

            count = count + currentItem.stack.getCount();

            if (currentItem.stack.isEmpty()) {
                iterator.remove();
                currentItem = null;
                continue;
            }

            float movement = beltSpeed;
            if (onClient)
                movement *= ServerSpeedProvider.get();

            if (world != null && world.isClientSide && currentItem.locked) {
                continue;
            }

            if (currentItem.lockedExternally) {
                currentItem.lockedExternally = false;
                continue;
            }

            boolean noMovement = false;
            float currentPos = currentItem.beltPosition;
            if (stackInFront != null) {
                float diff = stackInFront.beltPosition - currentPos;
                if (Math.abs(diff) <= spacing)
                    noMovement = true;

                movement =
                        beltMovementPositive ? Math.min(movement, diff - spacing) : Math.max(movement, diff + spacing);
            }

            float diffToEnd = beltMovementPositive ? belt.beltLength - currentPos : -currentPos;
            if (Math.abs(diffToEnd) < Math.abs(movement) + 1) {
                if (ending == BeltEum.Ending.UNRESOLVED)
                    ending = createLazyTick$resolveEnding();

                diffToEnd += beltMovementPositive ? -ending.margin : ending.margin;
            }

            float limitedMovement =
                    beltMovementPositive ? Math.min(movement, diffToEnd) : Math.max(movement, diffToEnd);

            float nextOffset = currentItem.beltPosition + limitedMovement;

            if (Math.abs(limitedMovement) < 0.00001){
                stop_count = stop_count + currentItem.stack.getCount();
            }

            if (!onClient && horizontal) {
                ItemStack item = currentItem.stack;
                if (handleBeltProcessingAndCheckIfRemoved(currentItem, nextOffset, noMovement)) {

                    iterator.remove();

                    belt.notifyUpdate();
                    continue;
                }
                if (item != currentItem.stack) {

                    belt.notifyUpdate();
                }
                if (currentItem.locked)
                    continue;
            }

            if (BeltFunnelInteractionHandler.checkForFunnels(OrginalBeltInventory, currentItem, nextOffset)) {

                continue;
            }

            if (noMovement)
                continue;

            if (BeltTunnelInteractionHandler.flapTunnelsAndCheckIfStuck(OrginalBeltInventory, currentItem, nextOffset)) {

                continue;
            }

            if (BeltCrusherInteractionHandler.checkForCrushers(OrginalBeltInventory, currentItem, nextOffset)) {

                continue;
            }

            currentItem.beltPosition += limitedMovement;
            float diffToMiddle = currentItem.getTargetSideOffset() - currentItem.sideOffset;
            currentItem.sideOffset += Mth.clamp(diffToMiddle * Math.abs(limitedMovement) * 6f, -Math.abs(diffToMiddle),
                    Math.abs(diffToMiddle));
            currentPos = currentItem.beltPosition;

            if (limitedMovement == movement || onClient)
                continue;

            int lastOffset = beltMovementPositive ? belt.beltLength - 1 : 0;
            BlockPos nextPosition = BeltHelper.getPositionForOffset(belt, beltMovementPositive ? belt.beltLength : -1);

            if (ending == BeltEum.Ending.FUNNEL)
                continue;

            if (ending == BeltEum.Ending.INSERT) {
                DirectBeltInputBehaviour inputBehaviour =
                        BlockEntityBehaviour.get(world, nextPosition, DirectBeltInputBehaviour.TYPE);
                if (inputBehaviour == null)
                    continue;
                if (!inputBehaviour.canInsertFromSide(movementFacing))
                    continue;

                ItemStack remainder = inputBehaviour.handleInsertion(currentItem, movementFacing, false);
                if (ItemStack.isSameItemSameComponents(remainder,currentItem.stack))
                    continue;

                currentItem.stack = remainder;
                if (remainder.isEmpty()) {
                    lazyClientItem = currentItem;
                    lazyClientItem.locked = false;
                    iterator.remove();
                } else
                    currentItem.stack = remainder;

                flapTunnel(OrginalBeltInventory, lastOffset, movementFacing, false);
                belt.notifyUpdate();

                continue;
            }

            if (ending == BeltEum.Ending.BLOCKED)
                continue;

            if (ending == BeltEum.Ending.EJECT) {
                eject(currentItem);
                iterator.remove();
                flapTunnel(OrginalBeltInventory, lastOffset, movementFacing, false);
                belt.notifyUpdate();

            }
        }

        if (stop_count == count){
                if (CLT$BeltSlideTick < ServerConfig.getBeltDelayMax()) {
                    CLT$BeltSlideTick = CLT$BeltSlideTick + Math.max(1, CLT$BeltSlideTick / 10);
                }
        }

        else {
            createLazyTick$resetDelayCounters();
        }

        if (CLT$HasItemCount != count) {
            CLT$HasItemCount = count;
            createLazyTick$resetDelayCounters();
        }

        ci.cancel();
    }

    @Unique
    private boolean createLazyTick$drainPendingTransfers(BeltBlockEntity belt, List<TransportedItemStack> toInsert,
                                                    List<TransportedItemStack> toRemove, List<TransportedItemStack> items) {
        if (toInsert.isEmpty() && toRemove.isEmpty())
            return false;

        toInsert.forEach(this::insert);
        toInsert.clear();
        if (!toRemove.isEmpty()) {
            items.removeAll(toRemove);
            toRemove.clear();
        }
        belt.notifyUpdate();
        return true;
    }

    @Unique
    private BeltEum.Ending createLazyTick$resolveEnding() {

        BeltInventoryAccessor accessor = (BeltInventoryAccessor) this;
        BeltBlockEntity belt = accessor.getBelt();

        Level world = belt.getLevel();
        BlockPos nextPosition = BeltHelper.getPositionForOffset(belt, beltMovementPositive ? belt.beltLength : -1);

        DirectBeltInputBehaviour inputBehaviour =
                BlockEntityBehaviour.get(world, nextPosition, DirectBeltInputBehaviour.TYPE);
        if (inputBehaviour != null)
            return BeltEum.Ending.INSERT;

        if (world != null && BlockHelper.hasBlockSolidSide(world.getBlockState(nextPosition), world, nextPosition,
                belt.getMovementFacing()
                        .getOpposite())) return BeltEum.Ending.BLOCKED;

        return BeltEum.Ending.EJECT;
    }

}