package com.abitbol.ophir.iplay;

import android.util.Log;

public class getNote {

	private static final int NUMBER_OF_KEYS = 88;
	private static final float CI = (float) 493.883;
	private static final float LA = (float) 440.000;
	private static final float SOL = (float) 391.995;
	private static final float FA = (float) 349.228;
	private static final float MI = (float) 329.628;
	private static final float RE = (float) 293.665;
	private static final float DO = (float) 261.626;
	private static final float eps = 3;
	private double[] freqs = { 261, 293, 329, 349 , 391,
			440, 493 };

    private int[] notes;
	public getNote(int bufferSize, double coeff) {
        Log.d("NOTE MAP", "creaing DB with buffSize:  " + bufferSize + ", and coeeff: " + coeff);

        notes = createNoteDb(bufferSize , coeff);
	};

    public int[] getNotes()
    {
        return notes;
    }

	public String freqToNote(double freq)

	{
		if (Math.abs(DO - freq) < eps) {
			return "DO";
		}
		if (Math.abs(RE - freq) < eps) {
			return "RE";
		}
		if (Math.abs(MI - freq) < eps) {
			return "MI";
		}
		if (Math.abs(FA - freq) < eps) {
			return "FA";
		}
		if (Math.abs(SOL - freq) < eps) {
			return "SOL";
		}
		if (Math.abs(LA - freq) < eps) {
			return "LA";
		}
		if (Math.abs(CI - freq) < eps) {
			return "CI";
		}
		return null;
	}

    private int[] createNoteDb(int bufferSize, double coeff)
    {

        int[] notes = new int[bufferSize];
        for (double i = 1; i < NUMBER_OF_KEYS; i++) {
            double exp = (i-49.0)/12.0;
            double note = Math.pow(2,exp)*440;
            int index = (int)Math.round(note/coeff);
            notes[index]= notes[index+1]=notes[index-1]=1;

            Log.d("NOTE MAP", "created note at: "+index + " : " + note );
        }

        return notes;

    }

	public double[] getFreqs() {
		return freqs;
	}
}
