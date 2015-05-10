package com.abitbol.ophir.iplay.midiViewer;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Negan on 5/1/2015.
 */
public class NoteMultiple {
    public int note;
    public float amplitude;
    public int multiple;
    public float power;
    public List<NoteMultiple> supportingHarmonics = new ArrayList<NoteMultiple>();
    public int supportCount;
    public NoteMultiple(int note, int multiple, float amplitude)
    {
        this.note = note;
        this.multiple = multiple;
        this.amplitude = amplitude;
    }

    public void addSupport(NoteMultiple nm)
    {
        supportingHarmonics.add(nm);
    }
}
