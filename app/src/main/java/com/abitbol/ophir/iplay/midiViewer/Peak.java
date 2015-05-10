package com.abitbol.ophir.iplay.midiViewer;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Negan on 5/1/2015.
 */
public class Peak {


    private List<NoteMultiple> nm = new ArrayList<NoteMultiple>();
    public int location;
    public float amplitude;

    public Peak(int loc , float amplitude)
    {
        location = loc;

       this.amplitude = amplitude;
    }
    public void add(NoteMultiple nm)
    {
        this.nm.add(nm);
    }

    public List<NoteMultiple> getList ()
    {
        return nm;
    }
}
