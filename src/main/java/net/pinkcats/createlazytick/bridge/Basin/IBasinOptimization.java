package net.pinkcats.createlazytick.bridge.Basin;

import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;

public interface IBasinOptimization {
    long getInventoryVersion();

    BlazeBurnerBlock.HeatLevel optimization$getHeatLevel();
}