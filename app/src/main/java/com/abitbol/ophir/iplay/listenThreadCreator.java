package com.abitbol.ophir.iplay;

import android.content.res.AssetManager;
import android.graphics.Color;
import android.util.Log;
import android.widget.TextView;

import com.abitbol.ophir.iplay.*;
import com.abitbol.ophir.iplay.FileUri;
import com.abitbol.ophir.iplay.MidiFile;
import com.abitbol.ophir.iplay.MidiFileException;
import com.abitbol.ophir.iplay.MidiNote;
import com.abitbol.ophir.iplay.MidiOptions;
import com.abitbol.ophir.iplay.MidiTrack;

import org.jtransforms.fft.FloatFFT_1D;

import java.io.IOException;
import java.util.ArrayList;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.util.PitchConverter;
import be.tarsos.dsp.util.fft.FFT;

/**
 * Created by Negan on 4/26/2015.
 */
public class listenThreadCreator {


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
    final int bufferSize = (int) windowSize;
    final double fourierCoef = SR / (2 * (windowSize));
    final getNote gtNt = new getNote(bufferSize, fourierCoef);
    final float threshold = (float) 0.01;

    public listenThreadCreator(MidiFile midifile , Runnable refresh) {
        mfile = midifile;
        getTempo();
        noteDB = gtNt.getNotes();
        final AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SR, bufferSize, 0);
        refresh.run();

    }

    private void getTempo() {

        MidiOptions options = new MidiOptions(mfile);
        BPM = 60000000 / options.tempo;
        PPQ = mfile.returnPPQ();
        p2s = 60000 / (double) ((BPM * PPQ));
        windowSizeTime = 60 / (2 * (double) BPM);
        windowSize = Math.round(windowSizeTime * SR);
        Log.d("midi", "tempo , windowSizeTime, windowSize: " + BPM + " , " + windowSizeTime + " , " + windowSize);

    }

    AudioProcessor createProcess() {
        AudioProcessor fftProcessor = new AudioProcessor() {


            // silence detector:
            SilenceDetector silenceDetector = new SilenceDetector();
            double silenceThreshold = SilenceDetector.DEFAULT_SILENCE_THRESHOLD;

            FloatFFT_1D jft = new FloatFFT_1D(bufferSize);

            float[] amplitudes = new float[bufferSize];


            FFT fft = new FFT(bufferSize);
            //            float[] amplitudes = new float[bufferSize / 2];
            float[] finalAmplitudes = new float[bufferSize];

            boolean firstRun = true;
            long startTime;
            int noteIdx = 0;
            int maxNotes = notes.size();

            @Override
            public void processingFinished() {
                // TODO Auto-generated method stub
            }

            /**
             * on each audio event (buffer if full) perform this:
             * @param audioEvent the buffer event
             * @return
             */
            public boolean process(AudioEvent audioEvent) {


                // the buffer containing the audio data:
                float[] audioFloatBuffer = audioEvent.getFloatBuffer();
                float[] envelope = audioFloatBuffer;

                // check to see if there is anything playing:
                boolean silence = silenceDetector.isSilence(audioFloatBuffer);

                // if it isn't silent:
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

                    float maxAmp = 0;


                    // perform the fourier transofrm:
                    float[] transformbuffer = new float[bufferSize * 2];

                    System.arraycopy(audioFloatBuffer, 0, transformbuffer, 0,
                            audioFloatBuffer.length);

                    jft.realForward((float[]) transformbuffer);
//                    jft.realForward(transformbuffer);
                    amplitudes = transformbuffer;


//                 find peaks:
                    float max = 0;
                    float freq = 0;
                    int numPeaks = 0;
                    double[] peaks = new double[100];


                    for (int i = 0; i < amplitudes.length / 2; i++) {
                        amplitudes[i] = amplitudes[i] * amplitudes[i];
//                        max = (max < amplitudes[i]) ? amplitudes[i] : max;
                    }
                    Log.d("max spec amp", "spec max: " + max);

//                    --------------------------------------------------------------
//                    -------------------------  HPF  ------------------------------
//                    --------------------------------------------------------------

                    float[] amplitudesDown2 = new float[bufferSize]; // downsample by half
                    float[] amplitudesDown3 = new float[bufferSize]; // downsample by half
//                    float[] amplitudesDown4 = new float[bufferSize]; // downsample by half
//                    float[] amplitudesDown5 = new float[bufferSize]; // downsample by half

                    for (int i = 0; i < amplitudesDown2.length / 2; i++) {

                        amplitudesDown2[i] = amplitudes[i * 2];
                    }
                    for (int i = 0; i < amplitudesDown3.length / 3; i++) {

                        amplitudesDown3[i] = amplitudes[i * 3];
                    }
//                    for (int i = 0; i < amplitudesDown3.length / 4; i++) {
//
//                        amplitudesDown4[i] = amplitudes[i * 4];
//                    }
//                    for (int i = 0; i < amplitudesDown3.length / 5; i++) {
//
//                        amplitudesDown5[i] = amplitudes[i * 5];
//                    }
                    max = 0;
                    for (int i = 0; i < amplitudes.length / 2; i++) {
                        finalAmplitudes[i] = (amplitudes[i] * amplitudesDown2[i] * amplitudesDown3[i]
                        ) * noteDB[i];
                        max = (max < finalAmplitudes[i]) ? finalAmplitudes[i] : max;

//                        max = (max < finalAmplitudes[i]) ? finalAmplitudes[i] : max;
                    }
//                    finalAmplitudes = amplitudes;
                    for (int i = 0; i < amplitudes.length / 2; i++) {
                        finalAmplitudes[i] /= max;
                    }
                    double[][] finalPeaks = new double[100][2];
                    int numFinalPeaks = 0;

                    for (int i = 1; i < finalAmplitudes.length / 2; i++) {

                        // check some threshold and close values:
                        if (finalAmplitudes[i] > 0.0000001
                                && finalAmplitudes[i] > finalAmplitudes[i - 1]
                                && finalAmplitudes[i] > finalAmplitudes[i + 1]) {
//                            check for close range
                            boolean biggestPeak = true;
                            // get start index and end index for peak checking:
                            int stIn = ((i - (int) (10 / fourierCoef)) < 0) ? i : (int) (10 / fourierCoef);
                            int endIn = ((i + (int) (10 / fourierCoef)) > finalAmplitudes.length) ? finalAmplitudes.length - i - 1 : (int) (10 / fourierCoef);
                            for (int j = -stIn; j < stIn + endIn; j++) {
//                                Log.d("DEBUG", "length is: + i =  " + i + " freq: " + (i-1) * fourierCoef + " amp: " + finalAmplitudes[i]);

                                if (finalAmplitudes[i] < finalAmplitudes[i + j]) {
                                    biggestPeak = false;
                                    break;
                                }
                            }

                            if (biggestPeak) {
//                                Log.d("found freqs FINAL", "i =  " + i + " freq: " + (i) * fourierCoef + " amp: " + finalAmplitudes[i]);
                                finalPeaks[numFinalPeaks][0] = i * fourierCoef;
                                finalPeaks[numFinalPeaks][1] = finalAmplitudes[i];
                                numFinalPeaks++;
                            }


                        }
                        if (numFinalPeaks > 98) break;
                    }

                    // bubble sort:
//                    double temp0 = 0, temp1 = 0;

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


//                    String Sfreqs = "";
//
//                    for (int i = 0; i < numFinalPeaks; i++) {
//                        Sfreqs += " , " + finalPeaks[i][0];
//                    }
////                    final String foundFreqs = Speaks;
//                    final String foundFreqsNum = Sfreqs;
//
//                    boolean NoteCorrect = false, allCorrect = false;
//                    for (int i = 0; i < expNotes.length; i++) {
//                        for (int j = 0; j < numFinalPeaks; j++) {
//                            Log.d("CORRECT", PitchConverter.midiKeyToHertz((int) (expNotes[0][FREQ])) + "  -  " + finalPeaks[j][0]  );
//
//                            if (Math.abs(PitchConverter.midiKeyToHertz((int) (expNotes[0][FREQ])) - finalPeaks[j][0]) < 2) {
//                                NoteCorrect = true;
//                                break;
//                            }
//                        }
//                        if (NoteCorrect) break;
//                    }

//                    final boolean correctFound = NoteCorrect;

//                    runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            TextView text = (TextView) findViewById(R.id.newPitch);
//                            text.setText("notes freqs found: " + foundFreqsNum);
//
//                            TextView correctness = (TextView) findViewById(R.id.cor_note);
//                            if(correctFound)
//                            {
//                                correctness.setText("Correct note!");
//                                correctness.setTextColor(Color.GREEN);
//
//                            }
//                            else
//                            {
//                                correctness.setTextColor(Color.RED);
//                                correctness.setText("wrong note!");
//                            }
//                        }
//                    });
//                    }
                }


//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        TextView note = (TextView) findViewById(R.id.req_note);
//                        note.setText("notes req: " + PitchConverter.midiKeyToHertz((int) (expNotes[0][FREQ])));
//
//                    }
//                });
//                Log.d("silence", "is playing: " + !silence);


                return true;
            }

        };

        return  fftProcessor;
    }


}
