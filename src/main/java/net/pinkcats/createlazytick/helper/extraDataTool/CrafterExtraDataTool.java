package net.pinkcats.createlazytick.helper.extraDataTool;

public class CrafterExtraDataTool {
    public static int packCrafterData(boolean isPowered, boolean isInWindow, boolean isDelayForced) {
        int data = 0;               
        if (isPowered)  data += 1;  
        if (isInWindow) data += 2;
        if (isDelayForced) data += 4;
        return data;
    }

    public static boolean unpackIsPowered(int data) {

        return (data & 1) != 0;
    }

    public static boolean unpackInWindow(int data) {

        return (data & 2) != 0;
    }

    public static boolean unpackIsDelayForced(int data) {
        return (data & 4) != 0;
    }
}