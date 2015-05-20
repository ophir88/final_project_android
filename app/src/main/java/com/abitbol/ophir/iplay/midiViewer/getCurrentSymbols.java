package com.abitbol.ophir.iplay.midiViewer;

import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import java.util.ArrayList;

/**
 * Created by Negan on 5/20/2015.
 */
public class getCurrentSymbols {

    public static ArrayList<Integer> getSymbols(int currentPulseTime, int prevPulseTime, Staff staff) {

        ArrayList<Integer> currentSymbols = new ArrayList<Integer>();
        if (((currentPulseTime == prevPulseTime) && (currentPulseTime == 0))) {
            prevPulseTime = -10;
        }

        /* If there's nothing to unshade, or shade, return */
        if ((staff.starttime > prevPulseTime || staff.endtime < prevPulseTime) &&
                (staff.starttime > currentPulseTime || staff.endtime < currentPulseTime)) {
            return null;
        }

        /* Skip the left side Clef symbol and key signature */
        int xpos = staff.keysigWidth;

        MusicSymbol curr = null;

        /* Loop through the symbols.
         * Unshade symbols where start <= prevPulseTime < end
         * Shade symbols where start <= currentPulseTime < end
         */
        for (int i = 0; i < staff.symbols.size(); i++) {
            boolean isChord;
            boolean correct = false;
            curr = staff.symbols.get(i);
            if (curr instanceof BarSymbol) {
                xpos += curr.getWidth();
                continue;
            }

            int start = curr.getStartTime();
            int end = 0;
            if (i + 2 < staff.symbols.size() && staff.symbols.get(i + 1) instanceof BarSymbol) {
                end = staff.symbols.get(i + 2).getStartTime();
            } else if (i + 1 < staff.symbols.size()) {
                end = staff.symbols.get(i + 1).getStartTime();
            } else {
                end = staff.endtime;
            }



            /* If we've past the previous and current times, we're done. */
            if ((start > prevPulseTime) && (start > currentPulseTime)) {
                return currentSymbols;
            }

            isChord = (curr instanceof ChordSymbol) ? true : false;

            boolean redrawLines = false;

            if ((start <= prevPulseTime) && (prevPulseTime < end)) {
                if (isChord) {
                    NoteData[] notes = ((ChordSymbol) curr).getNotedata();
                    for (NoteData note : notes) {
                        currentSymbols.add(note.number);
                    }
                }
            }

            /* If symbol is in the current time, draw a shaded background */
            if ((start <= currentPulseTime) && (currentPulseTime < end)) {
                if (isChord) {
                    NoteData[] notes = ((ChordSymbol) curr).getNotedata();
                    for (NoteData note : notes) {
                        currentSymbols.add(note.number);
                    }
                }
            }
        }
        return currentSymbols;

    }
}
