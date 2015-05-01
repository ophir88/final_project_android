package com.abitbol.ophir.iplay.midiViewer;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Negan on 5/1/2015.
 */
public class Peak {


    private List<Integer> nm = new ArrayList<Integer>();

    public void add(int nm)
    {
        this.nm.add(nm);
    }

    public List<Integer> getList ()
    {
        return nm;
    }
}
