package com.github.dcysteine.nesql.exporter.capture;

import java.util.Arrays;

/** Pixel upload math and phase only. No Minecraft or GL context is started. */
public final class NativeSamplersTest {
    public static void run() {
        // Native NotFine computes (1 - (1 - tick/duration)), not tick/duration.
        require(NativeSamplers.interpolate(0x11000000, 0xff030303, 1, 3) == 0x11000000,
                "Interpolation changed Java double rounding or interpolated alpha");
        int[][] timeline = {{2,3},{0,2}};
        require(NativeSamplers.phase(timeline,1,1)==4,"Native frame/tick counters lost timeline order");
        require(NativeSamplers.nextUpload(timeline,0,false)==3&&NativeSamplers.nextUpload(timeline,0,true)==1,
                "A stale native GPU frame was replaced before its next upload");
        try { NativeSamplers.phase(timeline,1,2); throw new AssertionError("Invalid native tick counter was accepted"); }
        catch (IllegalArgumentException expected) { }
        int[][] first={{0xff010203,0xff040506,0xff070809,0xff0a0b0c},{0x7f123456}};
        int[][] next={{0x11000000,0x22000000,0x33000000,0x44000000},{0xff030303}};
        NativeSamplers.Packed packed=NativeSamplers.pack(2,2,Arrays.asList(first,next),new int[][]{{0,3},{1,2}},true,"owned");
        require(packed.frames==5&&packed.levels.size()==2,"Native mip chain was reduced to level zero");
        require(packed.levels.get(0).pixels.getRGB(0,0)==first[0][0],"Native image rows were flipped");
        require(first[1][0]==0x7f123456&&next[1][0]==0xff030303,"Packing modified native frame storage");
        require(NativeAnimation.mipLevelLimit(0,8,8)==0,"Atlas max level zero must not probe level one");
        require(NativeAnimation.mipLevelLimit(3,8,8)==3,"Atlas max level must remain bounded by the texture parameter");
        require(NativeAnimation.mipLevelLimit(3,8,4)==2,"Atlas mip limit must stop when a sprite dimension reaches zero");
        require(NativeAnimation.mipLevelLimit(-1,8,8)==-1,"Invalid atlas max level must be rejected");
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
