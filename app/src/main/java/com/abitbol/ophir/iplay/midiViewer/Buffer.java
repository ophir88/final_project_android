package com.abitbol.ophir.iplay.midiViewer;

/**
 * Created by Negan on 6/3/2015.
 */
public class Buffer {

    private float[][] extraNotes;
    boolean containsExtra;
    boolean foundAll;
    int extraCount;

    public Buffer(float[][] extraNotes, boolean foundAll, boolean containsExtra, int extraCount)
    {
        this.extraNotes = extraNotes;
        this.foundAll = foundAll;
        this.containsExtra = containsExtra;
        this.extraCount = extraCount;

    }

    public float[][] getExtraNotes() {
        return extraNotes;
    }

    public boolean isFoundAll() {
        return foundAll;
    }

    public boolean isContainsExtra() {
        return containsExtra;
    }

    public int getExtraCount() {
        return extraCount;
    }
}
