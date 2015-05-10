package com.abitbol.ophir.iplay.midiViewer;

/**
 * Created by Negan on 5/1/2015.
 */
public class LowFrequencies {

    private float[] lowFreq = new float[12];
    public LowFrequencies()
    {
        lowFreq[0] = (float)130.8;
        lowFreq[1] = (float)138.6;
        lowFreq[2] = (float)146.8;
        lowFreq[3] = (float)155.6   ;
        lowFreq[4] = (float)82.4069;
        lowFreq[5] = (float)87.3071;
        lowFreq[6] = (float)92.4986;
        lowFreq[7] = (float)97.9989;
        lowFreq[8] = (float)103.826;
        lowFreq[9] = (float)110.000;
        lowFreq[10] = (float)116.541;
        lowFreq[11] = (float)123.471;


    }
    public float[] getLowFreqs()
    {
        return lowFreq;
    }
}
