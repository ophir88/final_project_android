package com.abitbol.ophir.iplay;


import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.pitch.FastYin;
import be.tarsos.dsp.pitch.Goertzel;
import be.tarsos.dsp.pitch.PitchDetectionHandler;
import be.tarsos.dsp.pitch.PitchDetectionResult;
import be.tarsos.dsp.pitch.PitchProcessor;
import be.tarsos.dsp.pitch.Goertzel.FrequenciesDetectedHandler;
import be.tarsos.dsp.pitch.PitchProcessor.PitchEstimationAlgorithm;
import be.tarsos.dsp.util.PitchConverter;
import be.tarsos.dsp.util.fft.FFT;
import be.tarsos.dsp.util.fft.HannWindow;

import android.app.Activity;
import android.graphics.Color;
import android.os.CountDownTimer;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import java.io.*;
import java.util.ArrayList;
import java.lang.*;
import java.util.List;

import android.content.res.*;

import com.abitbol.ophir.iplay.midiViewer.LowFrequencies;
import com.abitbol.ophir.iplay.midiViewer.NoteMultiple;
import com.abitbol.ophir.iplay.midiViewer.Peak;

import org.jtransforms.fft.FloatFFT_1D;


public class MainActivity extends ActionBarActivity {


    // ------------------------------------------
    // ------------- variables -----------------
    // ------------------------------------------

    public final static int SR = 44100;
    public final static int MAX_FREQ_SIZE = 5;
    public final static int FREQ = 0;
    public final static int END_TIME = 1;
    public final static int DURATION = 2;
    public final static int PEAK_START = 0;
    public final static int PEAK_END = 1;
    public final static int PEAK_LOC = 0;
    public final static int PEAK_FREQ = 1;
    public final static int PEAK_AMP = 2;
    public final static int PEAK_NOTE = 3;
    public final static int PEAK_FUNDAMENTAL = 4;
    public final static int MAX_PEAKS = 60;

    ArrayList<MidiNote> notes; // will hold the notes of the song
    double[][] tempExpNotes = new double[5][4]; // will hold the current notes we are looking for
    double[][] expNotes = new double[5][4]; // will hold the current notes we are looking for
    int[] noteDB; // array of possible notes, according to bufferSize;

    boolean endMidi = false;
    boolean running = false; // is the record thread running

    MidiFile mfile; // the midi file to play
    FileUri midiToPlay; // uri of the midi file
    int BPM, PPQ; // the tempo of the song
    double p2s; // pulses to seconds coeff.
    double windowSizeTime, windowSize; // buffer size in seconds and bits

    Activity activity = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .add(R.id.container, new PlaceholderFragment())
                    .commit();
        }


        // set variables *-*-*-*-*-*-*-*-*-*-*-*-*-
//        final int SR = 44100;


        try {
            getMidi();
        } catch (Exception e) {
            Log.e("MIDI", "MIDI error");
        }

        // set variables *-*-*-*-*-*-*-*-*-*-*-*-*-
        // final double[] freqs = gtNt.getFreqs();
        final int bufferSize = (int) windowSize;
        final double fourierCoef = SR / (windowSize);
        Log.d("fourier", "coef: " + SR + " / " + 2 + " * " + windowSize);
        final getNote gtNt = new getNote(bufferSize, fourierCoef);
        noteDB = gtNt.getNotes();

        final float thresh = (float) 0.01;

        // *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-

//TODO try and create overlap! (last parameter:)
        final AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SR, bufferSize, 0);

        // -------------------------------------------------------------------
        // ------------------------ create Fourier process ------------------
        // -------------------------------------------------------------------

        AudioProcessor fftProcessor = new AudioProcessor() {


            // silence detector:
            SilenceDetector silenceDetector = new SilenceDetector();
            double silenceThreshold = SilenceDetector.DEFAULT_SILENCE_THRESHOLD;

//            FloatFFT_1D jft = new FloatFFT_1D(bufferSize);

            HannWindow hann = new HannWindow();
            float[] hannWindow = hann.generateCurve(bufferSize);


            float[] amplitudes = new float[bufferSize];
            float[] amplitudesDerivative = new float[bufferSize];
            float[][] possiblePeaks;
            float[][] foundPeaks;

            float[] phase = new float[bufferSize];


            FFT fft = new FFT(bufferSize);
            //            float[] amplitudes = new float[bufferSize / 2];
            float[] finalAmplitudes = new float[bufferSize];
            float[] finalAmplitudesBinary = new float[bufferSize];

            boolean firstRun = true;
            long startTime;
            int noteIdx = 0;
            int maxNotes = notes.size();

            @Override
            public void processingFinished() {
                // TODO Auto-generated method stub
            }

            @Override
            public boolean process(AudioEvent audioEvent) {


                double timeElapsed;
                if (firstRun) {
                    startTime = System.nanoTime();
                    firstRun = false;
                    timeElapsed = 0;
                } else {
                    timeElapsed = ((double) (System.nanoTime() - startTime)) / 1000000.0;
                }


                int k = 0;
                MidiNote currNote;
//                Log.d("NOTES", "currNote start time: " + notes.get(1).getStartTime() * p2s);
                if (noteIdx < notes.size()) {
                    while (noteIdx < notes.size() && notes.get(noteIdx).getStartTime() * p2s <= timeElapsed && k < 5) {
                        currNote = notes.get(noteIdx);
                        // get frequency
                        tempExpNotes[k][FREQ] = currNote.getNumber();
                        // get end Time
                        tempExpNotes[k][END_TIME] = currNote.getEndTime() * p2s;
                        // get duration
                        tempExpNotes[k][DURATION] = currNote.getDuration() * p2s;
                        noteIdx++;
                        k++;
//                      Log.d("NOTES" , "added note: " + tempExpNotes[k][FREQ] + ", duration: "+ tempExpNotes[k][2]);

                    }


                    for (int i = 0; i < MAX_FREQ_SIZE; i++) {
                        // if the note ends a 1/16 note after the current location, add it to the wanted notes
                        if (expNotes[i][FREQ] > 0 && expNotes[i][END_TIME] - timeElapsed >= windowSize / (SR * 2) && k < MAX_FREQ_SIZE) {
// get frequency
                            tempExpNotes[k][FREQ] = expNotes[i][FREQ];
                            // get end Time
                            tempExpNotes[k][DURATION] = expNotes[i][DURATION];
                            // get duration
                            tempExpNotes[k][END_TIME] = expNotes[i][END_TIME];
//                        Log.d("NOTES" , "added note: " + tempExpNotes[k][0] + ", duration: "+ tempExpNotes[k][2]);
                            k++;
                            i++;
                        }
                    }

                    // clear rest of notes from last iteration
                    for (; k < 5; k++) {
                        tempExpNotes[k][0] = tempExpNotes[k][1] = 0.0;
                    }
                    expNotes = tempExpNotes;
                }

//                Log.d("NOTES", "");
//                Log.d("NOTES", "");
//
//                Log.d("NOTES", "======current iteration frequencies:======");
//                Log.d("NOTES", "||       time: [" + (timeElapsed) + " - " + (timeElapsed + windowSizeTime * 1000) + "]             ||");
//
//                for (int i = 0; i < MAX_FREQ_SIZE; i++) {
//                    Log.d("NOTES", "||              NOTE : " + expNotes[i][FREQ] + "              ||");
//
//                }
//                Log.d("NOTES", "=========================================");

                // get event
                float[] audioFloatBuffer = audioEvent.getFloatBuffer();
//                float[] envelope = audioFloatBuffer;


                boolean silence = silenceDetector.isSilence(audioFloatBuffer);

                if (!silence) {


////               ===============================================================
////               ========================  Envelope  ===========================
////               ===============================================================
//
//                    int peakwindowsize = 206;
//                    double average = 0.0;
//                    float tempMax = 0;
//                    for (int i = 0; i < envelope.length - peakwindowsize; i++) {
//
//                        average += envelope[i];
//                        tempMax = 0;
//                        for (int j = i; j < i + peakwindowsize; j++) {
//                            tempMax = (tempMax < envelope[j]) ? envelope[j] : tempMax;
//                        }
//                        envelope[i] = tempMax;
//                    }
//                    for (int i = envelope.length - peakwindowsize; i < envelope.length; i++) {
//                        average += envelope[i];
//                        tempMax = 0;
//                        for (int j = i - peakwindowsize; j < i; j++) {
//                            tempMax = (tempMax < audioFloatBuffer[j]) ? audioFloatBuffer[j] : tempMax;
//                        }
//                        envelope[i] = tempMax;
//                    }
//                    average /= envelope.length;
//
////               ===============================================================
////               ========================  find peaks  =========================
////               ===============================================================
//
//
////               *-*-*-*-*-*-*-*- variables *-*-*-*-*-*-*-*-
//                    int[]ampPeak = new int[2]; // up to two peaks in a buffer:
//                    int peakIdx = 0;
//                    boolean inPeak = false, continuousNote = false;
//
//                    int peakStartLoc = 0;
//                    int peakEndLoc = 0;
//
//                    double thresh = average;
//                    for (int i = 0; i < envelope.length; i++) {
//
//                        if (!inPeak) { // if we aren't in a peaks
//                            if (envelope[i] > thresh) { // and the amplitude is higher than average
//                                inPeak = true; // then we start a peak
//                                peakStartLoc = i;
//                            }
//                        } else {
//                            if (envelope[i] < thresh) { // and the amplitude is higher than average
//                                inPeak = false; // then we start a peak
//                                peakEndLoc = i;
//
//                                if (peakEndLoc - peakStartLoc > windowSize / 4) {
//                                    ampPeak[PEAK_START] = peakStartLoc;
//                                    ampPeak[PEAK_END] = peakStartLoc;
////                                    peakIdx++;
//                                }
//
//                            }
//                        }
//
//
//                    }
//
//                    if(inPeak && peakStartLoc<windowSize*7/8)
//                    {
//                        ampPeak[PEAK_START] = peakStartLoc;
//                        ampPeak[1] = (int)windowSize-1;
//                        continuousNote = true;
//                    }

//
//
//                for i = 1:length(maxenv)
//                        % if not in peak and higher than thresh, set to "in peak" and save
//                %location of begining of peak
//                if (inPeak == 0)
//                    if (maxenv(i) > thresh)
//                        inPeak = 1;
//                peakStartLoc = i;
//                end
//                else
//                %if already in peak, and reached the end of peak, set inpeak
//                        % to false and save location of end of peak
//                if (maxenv(i) < thresh)
//                    inPeak = 0;
//                peakEndLoc = i;
//                %if peak is at least the size of an eight note, save it
//                if (peakEndLoc - peakStartLoc > windowSize / 8)
//                    peaksLoc(peakInd, 1) = peakStartLoc;
//                peaksLoc(peakInd, 2) = peakEndLoc;
//                peakInd = peakInd + 1;
//                end
//                        end
//                end
//                        end
//                %if we finish the window and are still in peak, set as continuos note:
//                if (inPeak == 1 && peakStartLoc < length(y) * 7 / 8)
//                    peaksLoc(peakInd, 1) = peakStartLoc;
//                peaksLoc(peakInd, 2) = length(y);
//                continuousNote = 1;
//                end


//                    int length = ampPeak[1]-ampPeak[0];
                    float maxAmp = 0;
//                    for (int j = 0; j < audioFloatBuffer.length; j++) {
//                        maxAmp = (maxAmp < audioFloatBuffer[j]) ? audioFloatBuffer[j] : maxAmp;
//
//                    }

//                    if (maxAmp > 0.1) {
                    Log.d("max amp", "max: " + maxAmp);
                    // set variables
//                    float SR = audioEvent.getSampleRate();
//                    int BS = audioEvent.getBufferSize();


//                     apply hanning:
//                    for (int i = 0; i < audioFloatBuffer.length; i++) {
//                        audioFloatBuffer[i] = audioFloatBuffer[i] * hannWindow[i];
//                    }

                    float[] transformbuffer = new float[bufferSize * 2];

                    System.arraycopy(audioFloatBuffer, 0, transformbuffer, 0,
                            audioFloatBuffer.length);


                    fft.powerPhaseFFT(transformbuffer, amplitudes, phase);




                    float max = 0;

//                    first filter
                    for (int i = 0; i < amplitudes.length / 2; i++) {
                        amplitudes[i] = (amplitudes[i]) * noteDB[i];
                    }


                    // Log used as debug
                    File log = new File(Environment.getExternalStorageDirectory(), "buffer.txt");
                    Log.d("buffer", "path: " + log.getAbsolutePath());
                    try {
                        BufferedWriter out = new BufferedWriter(new FileWriter(log.getAbsolutePath(), true));
                        out.write("buffer: \n");

                        for (int i = 1; i < amplitudes.length / 2; ++i) {
                            out.write(amplitudes[i] + "\n");

                        }
                        out.close();
                    } catch (Exception e) {
                        Log.e("buffer", "Error opening Log.", e);
                    }

//                    derivate:

                    for (int i = 1; i < amplitudes.length / 2; i++) {
                        amplitudesDerivative[i] = amplitudes[i] - amplitudes[i - 1];
//                        erase negative values:
                        if (amplitudesDerivative[i] < 0) {
                            amplitudesDerivative[i] = 0;
                        }
                        // remember maxium value for normalization
                        max = (max < amplitudesDerivative[i]) ? amplitudesDerivative[i] : max;

                    }
                    amplitudes = amplitudesDerivative;

//                    normalize to 1:
                    for (int i = 0; i < amplitudes.length / 2; i++) {
                        amplitudes[i] /= max;
                    }
                    /**
                     * this will hold the possible peaks:
                     * 0 = location;
                     * 1 = frequency
                     * 2 = amplitude
                     */

//                    Log.d("possiblePeaks", " ######################################################");

                    possiblePeaks = new float[MAX_PEAKS][3];
                    int peakCounter = 0;
                    boolean full = false;
                    boolean minorPeak;
                    for (int i = 1; i < amplitudes.length / 2; i++) {


                        minorPeak = false; //   restart bool:


                        // find peaks:
                        if (amplitudes[i] > thresh
                                && amplitudes[i] > amplitudes[i - 1]
                                && amplitudes[i] > amplitudes[i + 1]) {

                            // check for close range peaks:
                            if (peakCounter > 0) {
                                float lastFreq = possiblePeaks[peakCounter - 1][PEAK_FREQ];
                                double n = 12 * Math.log((lastFreq / 400)) + 49;
                                // find the next note in relation to the previous peak
                                double nextNote = Math.pow(2, (n - 48) / 12) * 440;
//                                   frequency between them:
                                double diff = nextNote - lastFreq;

//                                    if the actual difference is less than diff, find the bigger one:
                                if (Math.abs(i * fourierCoef - lastFreq) < diff * 0.7) {
                                    if (amplitudes[i] > possiblePeaks[peakCounter - 1][PEAK_AMP]) {
                                        peakCounter--;
                                    } else {
                                        minorPeak = true;
                                    }
                                }

                            }

                            if (!minorPeak) {


                                possiblePeaks[peakCounter][PEAK_LOC] = i;
                                possiblePeaks[peakCounter][PEAK_FREQ] = (float) (i * fourierCoef);
                                possiblePeaks[peakCounter][PEAK_AMP] = amplitudes[i];
//                                Log.d("possiblePeaks", " possible [ freq: " + possiblePeaks[peakCounter][PEAK_FREQ] + " ] , [ amp: "+
//                                        possiblePeaks[peakCounter][PEAK_AMP] + " ] " );
                                peakCounter++;
                            }
                        }

                        if (peakCounter >= 60) {
                            break;
                        }
                    }
//                    Log.d("possiblePeaks", "  ---------------------------------------------  ");

//                    find the peak average:
                    float peakAverage = 0;
                    int numOfPeaks = 0;
                    for (int i = 0; i < peakCounter; i++) {
                        if (possiblePeaks[i][PEAK_AMP] > 0) {
                            peakAverage += possiblePeaks[i][PEAK_AMP] * possiblePeaks[i][PEAK_AMP];
                            numOfPeaks++;
                        }
                    }
                    peakAverage /= numOfPeaks;


//                    Log.d("possiblePeaks", "Peak average: " + peakAverage );


                    float[] tempSolution = amplitudes;
                    foundPeaks = new float[peakCounter][5];
                    int foundCounter = 0;
                    boolean foundSecond;
                    for (int i = 0; i < peakCounter; i++) {

                        foundSecond = false;
                        // look for peak with amp thresh:
                        if (possiblePeaks[i][PEAK_FREQ] < 2500 && possiblePeaks[i][PEAK_AMP] > peakAverage * 0.75) {
//                            Log.d("possiblePeaks", "___found__");

//                            Log.d("possiblePeaks", "checking peak: " + possiblePeaks[i][PEAK_FREQ] );

                            // look for second harmony
                            for (int j = i + 1; j < peakCounter; j++) {
                                if (!foundSecond) {
                                    if ((PitchConverter.hertzToMidiKey((double) possiblePeaks[i][PEAK_FREQ]) == PitchConverter.hertzToMidiKey((double) (possiblePeaks[j][PEAK_FREQ] / 2))
                                            && possiblePeaks[j][PEAK_AMP] > peakAverage / 10)) {

//                                        Log.d("possiblePeaks", "found match: " + possiblePeaks[j][PEAK_FREQ] );

//                                if ((Math.abs(possiblePeaks[i][PEAK_FREQ] - possiblePeaks[j][PEAK_FREQ] / 2) < 8 && possiblePeaks[j][PEAK_AMP] > peakAverage / 10)) {

                                        //get surrounding average:
                                        float average = getAverage((int) possiblePeaks[i][PEAK_LOC], tempSolution, fourierCoef);
//                                        Log.d("possiblePeaks", "average " + average );

//                                    get ratio:
                                        float ratio = possiblePeaks[i][PEAK_AMP] / average;
//                                        Log.d("possiblePeaks", "ratio " + ratio );


                                        if (ratio >= 3) {
//                                        add to found peaks:

                                            foundPeaks[foundCounter][PEAK_LOC] = possiblePeaks[i][PEAK_LOC];
                                            foundPeaks[foundCounter][PEAK_AMP] = possiblePeaks[i][PEAK_AMP];
                                            foundPeaks[foundCounter][PEAK_FREQ] = possiblePeaks[i][PEAK_FREQ];
                                            foundPeaks[foundCounter][PEAK_NOTE] = PitchConverter.hertzToMidiKey((double) possiblePeaks[i][PEAK_FREQ]);
                                            foundPeaks[foundCounter][PEAK_FUNDAMENTAL] = 0;
//                                        Log.d("possiblePeaks", " adding : [ freq: " + foundPeaks[foundCounter][PEAK_FREQ] + " ]");


                                            foundCounter++;

//                                      erase the 2nd harmony from the solution:
                                            tempSolution[(int) (possiblePeaks[i][PEAK_LOC] * 2 - 2)] = 0;
                                            tempSolution[(int) (possiblePeaks[i][PEAK_LOC] * 2 - 1)] = 0;
                                            tempSolution[(int) (possiblePeaks[i][PEAK_LOC] * 2)] = 0;
                                            tempSolution[(int) (possiblePeaks[i][PEAK_LOC] * 2 + 1)] = 0;
                                            tempSolution[(int) (possiblePeaks[i][PEAK_LOC] * 2 + 2)] = 0;
                                            foundSecond = true;
                                        }

                                    }
                                }
                                else
                                {
                                    break;
                                }


                            }
                        }


                    }
//                    Log.d("possiblePeaks", " ######################################################");

                    // look for third harmonic
                    for (int i = 0; i < foundCounter; i++) {

                        for (int j = i + 1; j < foundCounter; j++) {
                            if (foundPeaks[i][PEAK_NOTE] % 12 == foundPeaks[j][PEAK_NOTE] % 12) {
                                foundPeaks[i][PEAK_FUNDAMENTAL]++;
                            }
                        }
                    }


//                 find peaks:
//                    float max = 0;
//                    float freq = 0;
//                    int numPeaks = 0;
//                    double[] peaks = new double[100];
//                    float fDelta = SR / amplitudes.length;
//
//                    List<Peak> peakList = new ArrayList<Peak>();
//
//                    max = 0;
////                    for (int i = 0; i < amplitudes.length / 2; i++) {
////                        amplitudes[i] = (float) (20.0 * Math.log10(((double) (amplitudes[i] + 1))));
//////                        max = (max < amplitudes[i]) ? amplitudes[i] : max;
////                    }
//
//                    float averagePeak = 0;
////                    float numOfPeaks = 0;
//
//
////                    *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-
////                    *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-
//
//                    double mx;
//                    double mn;
//                    int mx_pos = 0;
//                    int mn_pos = 0;
//                    boolean looking = true;
//                    float deltaNew = (float) 0.01;
//                    float maxPeak = 0;
//                    mx = amplitudes[0];
//                    mn = amplitudes[0];
//
//                    numOfPeaks = 0;
//                    int numOfPeaksAbsorb = 0;
//
//                    for (int i = 1; i < amplitudes.length / 2; ++i) {
//                        if (amplitudes[i] > mx) {
//                            mx_pos = i;
//                            mx = amplitudes[i];
//                        }
//                        if (amplitudes[i] < mn) {
//                            mn_pos = i;
//                            mn = amplitudes[i];
//                        }
//
//                        if (looking &&
//                                amplitudes[i] < mx - deltaNew) {
//                            maxPeak = (maxPeak < amplitudes[i]) ? amplitudes[i] : maxPeak;
//
//                            averagePeak += (amplitudes[i] * amplitudes[i]);
//                            numOfPeaks++;
//                            looking = false;
//
//                            i = mx_pos - 1;
//
//                            mn = amplitudes[mx_pos];
//                            mn_pos = mx_pos;
//                        } else if ((!looking) &&
//                                amplitudes[i] > mn + deltaNew) {
////                            absop_peaks[*num_absop_peaks] = mn_pos;
////                            ++ (*num_absop_peaks);
//
//                            looking = true;
//
//                            i = mn_pos - 1;
//
//                            mx = amplitudes[mn_pos];
//                            mx_pos = mn_pos;
//                        }
//                    }
//                    averagePeak /= numOfPeaks;
//
//
//                    mx = 0;
//                    mn = 0;
//                    mx_pos = 0;
//                    mn_pos = 0;
//                    looking = true;
//                    deltaNew = (float) (averagePeak / 3);
//                    Log.d("fundamentals", "delat: " + deltaNew);
//                    mx = amplitudes[0];
//                    mn = amplitudes[0];
//
//                    numOfPeaks = 0;
//                    numOfPeaksAbsorb = 0;
//
//                    for (int i = 1; i < amplitudes.length / 2; ++i) {
//                        if (amplitudes[i] > mx) {
//                            mx_pos = i;
//                            mx = amplitudes[i];
//                        }
//                        if (amplitudes[i] < mn) {
//                            mn_pos = i;
//                            mn = amplitudes[i];
//                        }
//
//                        if (looking &&
//                                amplitudes[i] < mx - deltaNew) {
//                            if (amplitudes[i] >= maxPeak / 3) {
//                                Peak pk = quadraticPeak(i, amplitudes);
//                                peakList.add(quadraticPeak(i, amplitudes));
//                                Log.d("fundamentals", "[for the freq: " + pk.location * fourierCoef + ", amp: " + pk.amplitude + " ]");
//
//                                looking = false;
//
//                                i = mx_pos - 1;
//
//                                mn = amplitudes[mx_pos];
//                                mn_pos = mx_pos;
//                            }
//
//                        } else if ((!looking) &&
//                                amplitudes[i] > mn + deltaNew) {
////                            absop_peaks[*num_absop_peaks] = mn_pos;
////                            ++ (*num_absop_peaks);
//
//                            looking = true;
//
//                            i = mn_pos - 1;
//
//                            mx = amplitudes[mn_pos];
//                            mx_pos = mn_pos;
//                        }
//                    }
//
//
////                    *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-
////                    *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-
//
//
////                    for (int i = 1; i < amplitudes.length / 2; i++) {
//////                        amplitudes[i] = noteDB[i] * amplitudes[i];
////                        if (amplitudes[i] > 0.01 && amplitudes[i] > amplitudes[i - 1]
////                                && amplitudes[i] > amplitudes[i + 1]) {
////                            averagePeak += ( amplitudes[i]*amplitudes[i]);
////                            numOfPeaks++;
////                        }
////
////                    }
////                    averagePeak /= numOfPeaks;
////                    float delta = averagePeak / 3;
//
//
////                    for (int i = 0; i < amplitudes.length / 2; i++) {
////                        amplitudes[i] = (amplitudes[i]) * noteDB[i];
////                    }
//
//
////                    Log.d("Delta", "spec max: " + delta);
//
////
//////                    fundamental search:
//////                    Log.d("Delta", "length is:  " + amplitudes.length);
////
////                    for (int i = 1; i < amplitudes.length / 2; i++) {
//////                        Log.d("Delta", "index is: " +i);
////
////                        if (amplitudes[i] > delta
////                                && amplitudes[i] > amplitudes[i - 1]
////                                && amplitudes[i] > amplitudes[i + 1]) {
//////                            check for close range
////                            boolean biggestPeak = true;
////                            // get start index and end index for peak checking:
//////                            int stIn = ((i - (int) (10 / fourierCoef)) < 0) ? i : (int) (10 / fourierCoef);
//////                            int endIn = ((i + (int) (10 / fourierCoef)) > amplitudes.length) ? amplitudes.length - i - 1 : (int) (10 / fourierCoef);
////                            Log.d("ADDING", "[for the freq: " +i * fDelta + ", amp: " + amplitudes[i] + " ]");
////
//////                            for (int j = -stIn; j < stIn + endIn; j++) {
//////                                  Log.d("DEBUG", "length is: + i =  " + i + " freq: " + (i-1) * fourierCoef + " amp: " + finalAmplitudes[i]);
//////                                if (j != 0) {
//////                                    Log.d("DEBUGIS", "[ "+0.1*finalAmplitudes[i]+" ] < [ "+ finalAmplitudes[i + j] + " ]");
////
//////                                }
////
//////                                if (amplitudes[i] < amplitudes[i + j]) {
//////                                    if (j != 0) {
//////                                        amplitudes[i] = 0;
//////                                        biggestPeak = false;
//////                                        break;
//////                                    }
////
//////                                }
//////                            }
////
//////                            if (biggestPeak) {
//////                                Log.d("ADDING", "i =  " + i + " freq: " + (i) * fourierCoef + " amp: " + amplitudes[i]);
////                                peakList.add(new Peak(i));
////
//////                            }
////
////
////                        }
////                    }
//
//
//                    Log.d("fundamentals", "so far, there are " + peakList.size() + " potential peaks");
//
//                    // we have a list of possible peaks:
//                    float[] lowFreqs = new LowFrequencies().getLowFreqs();
//
////                    iterate over peaks:
//
//
//                    for (int i = 0; i < peakList.size(); i++) {
//                        Peak currPeak = peakList.get(i);
//                        float frequency = currPeak.location * fDelta;
//
//                        for (int j = 0; j < lowFreqs.length; j++) {
//                            double q = frequency / lowFreqs[j];
//                            float quotient = Math.round(q);
//                            double remainder = Math.abs(quotient - q);
//                            double t = fDelta * quotient;
//                            double x = (lowFreqs[j] + t) / lowFreqs[j];
//                            double threshold = Math.abs(Math.round(x) - x);
//                            if (remainder < threshold) {
//                                peakList.get(i).add(new NoteMultiple(j, (int) quotient, currPeak.amplitude));
//                            }
//                        }
//                    }
//
//                    // find supporting harmonics for each peak:
//                    List<NoteMultiple> fundamentals = new ArrayList<NoteMultiple>();
//
//                    for (int i = 0; i < peakList.size(); i++) {
//                        Peak currPeak = peakList.get(i);
//
//                        List<NoteMultiple> NMList = currPeak.getList();
//
//                        for (int j = 0; j < NMList.size(); j++) {
//                            NoteMultiple currNM = NMList.get(j);
//                            // if it is a power of 2:
//                            if ((currNM.multiple & (currNM.multiple - 1)) == 0) {
////                                search for supporting harmonics:
//                                for (int a = i + 1; a < peakList.size(); a++) {
//                                    Peak currPeakHarmonic = peakList.get(a);
//
//                                    List<NoteMultiple> NMListHarmonic = currPeakHarmonic.getList();
//
//                                    for (int b = 0; b < NMListHarmonic.size(); b++) {
//                                        NoteMultiple currNMHarmonic = NMListHarmonic.get(b);
//                                        if (currNM.note == currNMHarmonic.note && currNM.multiple < currNMHarmonic.multiple) {
//                                            // add supporting harmonic:
//                                            currNM.addSupport(currNMHarmonic);
//                                            currNM.supportCount++;
//                                        }
//                                    }
//                                }
//                                if (currNM.supportingHarmonics.size() > 0) {
//                                    fundamentals.add(currNM);
//                                }
//                            }
//                        }
//                    }
//
//
//                    List<NoteMultiple> definiteFunda = new ArrayList<NoteMultiple>();
//                    List<NoteMultiple> potentialFunda = new ArrayList<NoteMultiple>();
//                    float averageMean = 0;
//                    // find fundamentals:
//                    boolean isHarmonic;
//                    for (int i = 0; i < fundamentals.size(); i++) {
//                        isHarmonic = false;
//                        NoteMultiple fundamental = fundamentals.get(i);
//                        for (int j = 0; j < definiteFunda.size(); j++) {
//                            NoteMultiple definite = definiteFunda.get(j);
//                            for (int l = 0; l < definite.supportingHarmonics.size(); l++) {
//                                NoteMultiple harmonic = definite.supportingHarmonics.get(l);
//                                if (fundamental.note == harmonic.note && fundamental.multiple == harmonic.multiple) {
//                                    isHarmonic = true;
//                                    break;
//                                }
//                            }
//                        }
//
//                        if (isHarmonic) {
//                            potentialFunda.add(fundamental);
//                        } else {
//                            definiteFunda.add(fundamental);
//                            averageMean += fundamental.amplitude;
//                        }
//
//                    }
//                    averageMean /= definiteFunda.size();
//                    double halfPi = Math.PI / 2;
//                    for (int i = 0; i < definiteFunda.size(); i++) {
//                        NoteMultiple currNotePower = definiteFunda.get(i);
//                        float power = currNotePower.amplitude + currNotePower.supportingHarmonics.get(0).amplitude;
//                        power = (float) (Math.atan(power - averageMean) / halfPi);
//                        currNotePower.power = power;
//                    }

                    Log.d("fundamentals", "*********************************************");
                    Log.d("fundamentals", "showing fundamentals: ");
                    Log.d("fundamentals", "---------------------------------------- ");
                    Log.d("fundamentals", "definite: ");
                    Log.d("fundamentals", "-------- ");

                    for (int i = 0; i < foundCounter; i++) {
//                        if(foundPeaks[i][PEAK_FUNDAMENTAL]>0)
//                        {
                        Log.d("fundamentals", "[note: " + foundPeaks[i][PEAK_NOTE] + " ] , [ freq: " +
                                foundPeaks[i][PEAK_FREQ] + "] , [HarmPower: " + foundPeaks[i][PEAK_FUNDAMENTAL]
                                + " ]");

//                        }
                    }


                    // normalize
//                    for (int i = 0; i < amplitudes.length / 2; i++) {
//                        amplitudes[i] /= max;
////                        if(amplitudes[i]<0.0002)
////                        {
////                            (amplitudes[i])=0;
////                        }
//
//                    }


                    //  Find peaks:
//                    for (int i = 1; i < amplitudes.length / 2; i++) {
//
//                        // check some threshold and close values:
//                        if (amplitudes[i] > 0.005
//                                && amplitudes[i] > amplitudes[i - 1]
//                                && amplitudes[i] > amplitudes[i + 1]) {
////                            boolean realNote = false;
////                            if(noteDB[i]==1)
////                            {
//
//
////                                int index = (int)Math.round(Math.round(m*fourierCoef)/fourierCoef);
////                            if(noteDB[(int)m]==1) {
//                            double newFreq = (i);
//                            Log.d("found freqs", "i =  " + i + " freq: " + i * fourierCoef + " amp: " + amplitudes[i]);
//
//                            peaks[numPeaks++] = newFreq;
////                            }
//
//
////                            }
//
//                        }
//                        if (numPeaks == 100) break;
//                    }

//
//                    float[] amplitudesDown2 = new float[bufferSize]; // downsample by half
//                    float[] amplitudesDown3 = new float[bufferSize]; // downsample by half
////                    float[] amplitudesDown4 = new float[bufferSize]; // downsample by half
////                    float[] amplitudesDown5 = new float[bufferSize]; // downsample by half
//
//
////                    for (int i = 0; i < amplitudesDown2.length / 2; i++) {
////
////                        amplitudesDown2[i] = amplitudes[i * 2];
////                    }
////                    for (int i = 0; i < amplitudesDown3.length / 3; i++) {
////
////                        amplitudesDown3[i] = amplitudes[i * 3];
////                    }
//////                    for (int i = 0; i < amplitudesDown3.length / 4; i++) {
//////
//////                        amplitudesDown4[i] = amplitudes[i * 4];
//////                    }
//////                    for (int i = 0; i < amplitudesDown3.length / 5; i++) {
//////
//////                        amplitudesDown5[i] = amplitudes[i * 5];
//////                    }
////                    max = 0;
////
////                    for (int i = 0; i < amplitudes.length / 2; i++) {
////                        finalAmplitudes[i] = (amplitudes[i] * amplitudesDown2[i] * amplitudesDown3[i]);
////                        max = (max < finalAmplitudes[i]) ? finalAmplitudes[i] : max;
////
////                    }
//
//                    // leave only the peaks:
//
////                    for (int i = 1; i < amplitudes.length / 2; i++) {
////
////
////                        /**
////                         * NEXT IDEA:
////                         * maybe if i checked and there is something bigger ahead, erase the current one!
////                         */
////                        // check some threshold and close values:
////                        if (amplitudes[i] > amplitudes[i - 1]
////                                && amplitudes[i] > amplitudes[i + 1]) {
//////                            check for close range
////                            boolean tallest = true;
////                            // get start index and end index for peak checking:
////                            int stIn = ((i - (int) (15 / fourierCoef)) < 0) ? i : (int) (15 / fourierCoef);
////                            int endIn = ((i + (int) (15 / fourierCoef)) > amplitudes.length) ? amplitudes.length - i - 1 : (int) (15 / fourierCoef);
////                            Log.d("DEBUGIS", "[for the freq: i = " + i + " - " + i * fourierCoef + " ]");
////
////                            for (int j = -stIn; j < stIn + endIn; j++) {
//////                                  Log.d("DEBUG", "length is: + i =  " + i + " freq: " + (i-1) * fourierCoef + " amp: " + finalAmplitudes[i]);
////                                if (j != 0) {
//////                                    Log.d("DEBUGIS", "[ "+0.1*finalAmplitudes[i]+" ] < [ "+ finalAmplitudes[i + j] + " ]");
////
////                                }
////                                if (0.3*amplitudes[i] < amplitudes[i + j]) {
////                                    if (j != 0) {
////                                        finalAmplitudesBinary[i] = 0;
////                                        tallest = false;
////                                        break;
////                                    }
////
////                                }
////                            }
////                            if (tallest) {
////                                finalAmplitudesBinary[i] = 1;
////                            }
////                        }
////                    }
////                    int[] midiNotesCounter = new int[127];
////
////
////                    for (int i = 0; i < finalAmplitudesBinary.length / 2; i++)
////                    {
////
////                        if(finalAmplitudesBinary[i]==1)
////                    {
////                        midiNotesCounter[PitchConverter.hertzToMidiKey(i*fourierCoef)]++;
////                    }
////                        if(finalAmplitudesBinary[i*2]==1)
////                        {
////                            midiNotesCounter[PitchConverter.hertzToMidiKey(i*2*fourierCoef)]++;
////                        }
////                        if(i*3 < finalAmplitudesBinary.length)
////                        {
////                            if(finalAmplitudesBinary[i*3]==1)
////                            {
////                                midiNotesCounter[PitchConverter.hertzToMidiKey(i*3*fourierCoef)]++;
////                            }
////                        }
////
////
////                    }
////
////
////                    Log.d("BINARY", "****************************************");
////                    Log.d("BINARY", "----------- found frequencies: ---------");
////                    Log.d("BINARY", "");
////                    Log.d("BINARY", "");
////
////                    for (int i = 0; i < midiNotesCounter.length; i++) {
////                        if(midiNotesCounter[i]>0)
////                        {
////                            Log.d("BINARY", "note: [ " + i + " , counter: " + midiNotesCounter[i]+ " ]");
////
////                        }
////
////                    }
////                    Log.d("BINARY", "");
////
////                    Log.d("BINARY", "****************************************");
//
//                    for (int i = 0; i < amplitudesDown2.length / 2; i++) {
//
//                        amplitudesDown2[i] = amplitudes[i * 2];
//                    }
//                    for (int i = 0; i < amplitudesDown3.length / 3; i++) {
//
//                        amplitudesDown3[i] = amplitudes[i * 3];
//                    }
////                    for (int i = 0; i < amplitudesDown3.length / 4; i++) {
////
////                        amplitudesDown4[i] = amplitudes[i * 4];
////                    }
////                    for (int i = 0; i < amplitudesDown3.length / 5; i++) {
////
////                        amplitudesDown5[i] = amplitudes[i * 5];
////                    }
//                    max = 0;
//
//
//                    for (int i = 0; i < amplitudes.length / 2; i++) {
//                        finalAmplitudes[i] = (amplitudes[i] * amplitudesDown2[i] * amplitudesDown3[i]);
//                        max = (max < finalAmplitudes[i]) ? finalAmplitudes[i] : max;
//
//                    }
//
////                    finalAmplitudes = amplitudes;
//                    for (int i = 0; i < amplitudes.length / 2; i++) {
//                        finalAmplitudes[i] /= max;
//                    }
////                    for (int i = 0; i < numPeaks; i++) {
////
////                        Log.d("found freqs" , "after multiplication, value at "+i+" is:" + finalAmplitudes[(int)peaks[i]]);
////
////
////                    }
//
//                    double[][] finalPeaks = new double[100][2];
//                    int numFinalPeaks = 0;
//
//                    for (int i = 1; i < finalAmplitudes.length / 2; i++) {
//
//
//                        /**
//                         * NEXT IDEA:
//                         * maybe if i checked and there is something bigger ahead, erase the current one!
//                         */
//                        // check some threshold and close values:
//                        if (finalAmplitudes[i] > 0.001
//                                && finalAmplitudes[i] > finalAmplitudes[i - 1]
//                                && finalAmplitudes[i] > finalAmplitudes[i + 1]) {
////                            check for close range
//                            boolean biggestPeak = true;
//                            // get start index and end index for peak checking:
//                            int stIn = ((i - (int) (15 / fourierCoef)) < 0) ? i : (int) (15 / fourierCoef);
//                            int endIn = ((i + (int) (15 / fourierCoef)) > finalAmplitudes.length) ? finalAmplitudes.length - i - 1 : (int) (15 / fourierCoef);
//                            Log.d("DEBUGIS", "[for the freq: i = " + i + " - " + i * fourierCoef + ", amp: " + finalAmplitudes[i] + " ]");
//
//                            for (int j = -stIn; j < stIn + endIn; j++) {
////                                  Log.d("DEBUG", "length is: + i =  " + i + " freq: " + (i-1) * fourierCoef + " amp: " + finalAmplitudes[i]);
//                                if (j != 0) {
////                                    Log.d("DEBUGIS", "[ "+0.1*finalAmplitudes[i]+" ] < [ "+ finalAmplitudes[i + j] + " ]");
//
//                                }
//
//                                if (0.1 * finalAmplitudes[i] < finalAmplitudes[i + j]) {
//                                    if (j != 0) {
//                                        finalAmplitudes[i] = 0;
//                                        biggestPeak = false;
//                                        break;
//                                    }
//
//                                }
//                            }
//
//                            if (biggestPeak) {
//                                Log.d("found freqs FINAL", "i =  " + i + " freq: " + (i) * fourierCoef + " amp: " + finalAmplitudes[i]);
//
//                                finalPeaks[numFinalPeaks][0] = i * fourierCoef;
//                                finalPeaks[numFinalPeaks][1] = finalAmplitudes[i];
//
//                                numFinalPeaks++;
//                            }
//
//
//                        }
////                        Log.d("DEBUGIS", "******************************]");
////
////                        for( int t = 0 ; t <numFinalPeaks ; t++)
////                        {
////                            Log.d("DEBUGIS", "[ "+finalPeaks[t][0]+" ]");
////
////                        }
//                        if (numFinalPeaks > 98) break;
//                    }

                    // check for peak again after HPS:
//                    for (int i = 0; i < numPeaks; i++) {
//                        if (finalAmplitudes[(int) peaks[i]] > 0) {
//                            finalPeaks[numFinalPeaks++] = (int) peaks[i] * fourierCoef;
//
//                        }
//
//
//                    }
//                    double temp0 = 0, temp1 = 0;
//
//                    for (int i = 0; i < numFinalPeaks; i++) {
//                        for (int j = 1; j < (numFinalPeaks - i); j++) {
//
//                            if (finalPeaks[j - 1][1] > finalPeaks[j][1]) {
//                                //swap the elements!
//                                temp0 = finalPeaks[j - 1][0];
//                                temp1 = finalPeaks[j - 1][1];
//                                finalPeaks[j - 1][0] = finalPeaks[j][0];
//                                finalPeaks[j - 1][1] = finalPeaks[j][1];
//                                finalPeaks[j][0] = temp0;
//                                finalPeaks[j][1] = temp1;
//                            }
//
//                        }
//                    }


                    String Sfreqs = "";
//                    Log.d("CORRECT", "****************************************");
//                    Log.d("CORRECT", "----------- found frequencies: ---------");
//                    Log.d("CORRECT", "");
//                    Log.d("CORRECT", "");

                    for (int i = 0; i < foundCounter; i++) {
//                        Log.d("CORRECT", "note: [ " + PitchConverter.hertzToMidiKey(finalPeaks[i][0]) + " ]  , freq: [ " + finalPeaks[i][0] + " ] ,  amp: [ " + finalPeaks[i][1] + " ]");

                        Sfreqs += " , " + Math.round(foundPeaks[i][PEAK_FREQ]);
                    }
//                    Log.d("CORRECT", "");

//                    Log.d("CORRECT", "****************************************");

//                    final String foundFreqs = Speaks;
                    final String foundFreqsNum = Sfreqs;

                    boolean NoteCorrect = false, allCorrect = false;
//                    for (int i = 0; i < expNotes.length; i++) {
//                        for (int j = 0; j < numFinalPeaks; j++) {
////                            Log.d("CORRECT",PitchConverter.midiKeyToHertz((int) (expNotes[0][FREQ])) + "  -  " + finalPeaks[j][0]  );
//
//                            if (Math.abs(PitchConverter.midiKeyToHertz((int) (expNotes[0][FREQ])) - finalPeaks[j][0]) < 2) {
//                                NoteCorrect = true;
//                                break;
//                            }
//                        }
//                        if (NoteCorrect) break;
//                    }

                    final boolean correctFound = NoteCorrect;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView text = (TextView) findViewById(R.id.newPitch);
                            text.setText("notes freqs found: " + foundFreqsNum);
//
//                            TextView correctness = (TextView) findViewById(R.id.cor_note);
//                            if (correctFound) {
//                                correctness.setText("Correct note!");
//                                correctness.setTextColor(Color.GREEN);
//
//                            } else {
//                                correctness.setTextColor(Color.RED);
//                                correctness.setText("wrong note!");
//                            }
                        }
                    });
//                    }
                }


                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView note = (TextView) findViewById(R.id.req_note);
                        note.setText("notes req: " + PitchConverter.midiKeyToHertz((int) (expNotes[0][FREQ])));

                    }
                });
//                Log.d("silence", "is playing: " + !silence);


                return true;
            }

        };


        dispatcher.addAudioProcessor(fftProcessor);
        dispatcher.addAudioProcessor(new PitchProcessor(
                PitchEstimationAlgorithm.FFT_YIN, SR, bufferSize,
                new PitchDetectionHandler() {

                    @Override
                    public void handlePitch(

                            PitchDetectionResult pitchDetectionResult,
                            AudioEvent audioEvent) {

                        final float pitchInHz = pitchDetectionResult.getPitch();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                TextView text = (TextView) findViewById(R.id.orPitch);
                                String currNote = gtNt.freqToNote(pitchInHz);
                                text.setText("" + pitchInHz
                                        + " , according note:" + currNote);
                            }
                        });

                    }
                }));

        final Thread listenThread = new Thread(dispatcher, "Audio Dispatcher");
//        new Thread(dispatcher, "Audio Dispatcher").start();
//		addListenerOnButton();


        Button changeStatus = (Button) findViewById(R.id.button_start_pause);
        changeStatus.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                if (!running) {
                    final Countdown countdown = new Countdown(BPM, listenThread, activity);
                    countdown.start();
                    TextView status_view = (TextView) findViewById(R.id.text_status);
                    status_view.setText("running!");
//                    listenThread.start();
                }
                running = true;

            }
        });

        //        *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-


    }

//    private void pause(Thread listenThread , AudioDispatcher dispatcher ) {
//        dispatcher.stop();
////        listenThread.stop();
//
//    }
//
//    private void resume(Thread listenThread , AudioDispatcher dispatcher, boolean threadStart) {
//
//        if(!threadStart)
//        {
//
//
//        }
//        else
//        {
////            listenThread.resume();
//            dispatcher.run();
//        }
//    }


    //    /**
//     * Quadratic Interpolation of Peak Location
//     *
//     * <p>Provides a more accurate value for the peak based on the
//     * best fit parabolic function.
//     *
//     * <p>α = spectrum[max-1]
//     * <br>β = spectrum[max]
//     * <br>γ = spectrum[max+1]
//     *
//     * <p>p = 0.5[(α - γ) / (α - 2β + γ)] = peak offset
//     *
//     * <p>k = max + p = interpolated peak location
//     *
//     * <p>Courtesy: <a href="https://ccrma.stanford.edu/~jos/sasp/Quadratic_Interpolation_Spectral_Peaks.html">
//     * information source</a>.
//     *
//     * @param index The estimated peak value to base a quadratic interpolation on.
//     * @return Float value that represents a more accurate peak index in a spectrum.
//     */
    private Peak quadraticPeak(int index, float[] amplitudes) {
        float alpha, beta, gamma, p, k;

        alpha = (float) amplitudes[index - 1];
        beta = (float) amplitudes[index];
        gamma = (float) amplitudes[index + 1];

        p = 0.5f * ((alpha - gamma) / (alpha - 2 * beta + gamma));

        k = (float) index + p;

        float amplitude = amplitudes[index];
        return new Peak((int) k, amplitude);
    }

    /**
     * The function returns the average of the the peak surrounding.
     *
     * @param location
     * @param tempSoltuion
     * @param fourierCoef
     * @return
     */
    private float getAverage(int location, float[] tempSoltuion, double fourierCoef) {

        double n = 12 * (Math.log((location * fourierCoef / 440))/Math.log(2)) + 49;

        // find the next note in relation to the previous peak
        double nextNote = (Math.pow(2, (n - 46) / 12)) * 440;
        double prevNote = (Math.pow(2, (n - 52) / 12)) * 440;

        int prevI = (int) Math.round(prevNote / (fourierCoef));
        int nextI = (int) Math.round(nextNote / (fourierCoef));

        float tempAv = 0;
        float tempCount = 0;
        if (location > 10 && (location + 10) < tempSoltuion.length) {


//            average left of peak
            for (int i = prevI; i <= location ; i++) {
                if (tempSoltuion[i] > 0.005) {
                    tempAv += tempSoltuion[i];
                    tempCount++;
                }
            }
            //            average right of peak
            for (int i = location + 3; i <= nextI; i++) {
                if (tempSoltuion[i] > 0.005) {
                    tempAv += tempSoltuion[i];
                    tempCount++;
                }
            }
        }
        tempAv /= tempCount;
        return tempAv;

    }

    private void getMidi() {
        try {
            AssetManager assets = this.getResources().getAssets();
            String[] files = assets.list("");
            for (String path : files) {
                Log.d("midi", "asset: " + path);

                if (path.endsWith(".midi")) {
                    midiToPlay = new FileUri(assets, path, path);
                }
            }
        } catch (IOException e) {
            this.finish();
            return;
        }
        if (midiToPlay == null) {

            Log.d("midi", "found nothing!");
            this.finish();
            return;
        } else {
            Log.d("midi", "midi loaded!");
            byte[] data = midiToPlay.getData();
            if (data == null || data.length <= 6 || !hasMidiHeader(data)) {
                Log.d("midi", "Error: Unable to open song: " + midiToPlay.toString());
                this.finish();
                return;
            }
            try {
                mfile = new MidiFile(data, "my file");
                Log.d("midi", "created midi file successfuly!");

            } catch (MidiFileException e) {
                this.finish();
                return;
            }
            MidiOptions options = new MidiOptions(mfile);
            BPM = 60000000 / options.tempo;
            PPQ = mfile.returnPPQ();
            p2s = 60000 / (double) ((BPM * PPQ));
            Log.d("midi", "p2s: " + p2s);

            Log.d("midi", "tempo: " + 60000000 / options.tempo);

            windowSizeTime = 60 / (2 * (double) BPM);
            windowSize = Math.round(windowSizeTime * SR);
            Log.d("midi", "tempo , windowSizeTime, windowSize: " + BPM + " , " + windowSizeTime + " , " + windowSize);
            MidiTrack track = MidiFile.CombineToSingleTrack(mfile.getTracks());


//            MidiTrack track = mfile.getTracks().get(1);
            notes = track.getNotes();

            for (int j = 0; j < notes.size(); j++) {
                MidiNote currNote = notes.get(j);
                Log.d("midi", "Note: " + currNote.getNumber() + ", st t: " + ((double) (currNote.getStartTime())) * p2s + ", end t: " + currNote.getEndTime());

            }
        }
    }

    /**
     * Return true if the data starts with the header MTrk
     */
    boolean hasMidiHeader(byte[] data) {
        String s;
        try {
            s = new String(data, 0, 4, "US-ASCII");
            Log.d("midi", "data header: " + s);

            if (s.equals("MThd"))
                return true;
            else
                return false;
        } catch (UnsupportedEncodingException e) {
            return false;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class PlaceholderFragment extends Fragment {

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
            return rootView;
        }
    }
}
