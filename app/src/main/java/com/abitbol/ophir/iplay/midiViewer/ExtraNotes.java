package com.abitbol.ophir.iplay.midiViewer;

import android.util.Log;

import java.util.ArrayList;

/**
 * Created by Negan on 6/4/2015.
 */
public class ExtraNotes {

    ArrayList<Integer> currNotes;
    ArrayList<Integer> prevNotes;

    public ExtraNotes() {
       currNotes = new ArrayList<>();
        prevNotes = new ArrayList<>();
    }

    public boolean addNotes (ArrayList<Integer> newNotes)
    {
        Log.d("classXtra", " starting search!");

        Log.d("classXtra", "prev is empty: "+ currNotes.isEmpty());
        if(newNotes!=null)
        {
            Log.d("classXtra", "curr is empty: "+ newNotes.isEmpty());

        }
        else
        {
            Log.d("classXtra", "curr is null");

        }

        if(currNotes!=null && !currNotes.isEmpty())
        {
            prevNotes = new ArrayList<>(currNotes);

        }
        else
        {
            prevNotes.clear();
        }
        if (newNotes!=null && !newNotes.isEmpty())
        {
            currNotes = new ArrayList<>(newNotes);

        }
        else
        {
            currNotes.clear();
        }

        for (int newNote : currNotes)
        {
            for (int prevNote : prevNotes)
            {
                if(prevNote == newNote)
                {
                    return true;
                }
            }
        }

        return false;

    }

}
