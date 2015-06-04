/*
 * Copyright (c) 2007-2012 Madhav Vaidyanathan
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License version 2.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 */

package com.abitbol.ophir.iplay.midiViewer;

import java.util.*;
import java.io.*;

import android.app.*;
import android.content.*;
import android.content.res.*;
import android.util.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import android.os.*;
import android.media.*;

import com.abitbol.ophir.iplay.*;
import com.abitbol.ophir.iplay.midiViewer.Countdown;
import com.abitbol.ophir.iplay.FileUri;

import org.jtransforms.fft.FloatFFT_1D;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.android.AudioDispatcherFactory;
import be.tarsos.dsp.util.PitchConverter;
import be.tarsos.dsp.util.fft.FFT;


/**
 * @class MidiPlayer
 * <p/>
 * The MidiPlayer is the panel at the top used to play the sound
 * of the midi file.  It consists of:
 * <p/>
 * - The Rewind button
 * - The Play/Pause button
 * - The Stop button
 * - The Fast Forward button
 * - The Playback speed bar
 * <p/>
 * The sound of the midi file depends on
 * - The MidiOptions (taken from the menus)
 * Which tracks are selected
 * How much to transpose the keys by
 * What instruments to use per track
 * - The tempo (from the Speed bar)
 * - The volume
 * <p/>
 * The MidiFile.ChangeSound() method is used to create a new midi file
 * with these options.  The mciSendString() function is used for
 * playing, pausing, and stopping the sound.
 * <p/>
 * For shading the notes during playback, the method
 * SheetMusic.ShadeNotes() is used.  It takes the current 'pulse time',
 * and determines which notes to shade.
 */
public class MidiPlayer extends LinearLayout {

    public final static int PEAK_LOC = 0;
    public final static int PEAK_FREQ = 1;
    public final static int PEAK_AMP = 2;
    public final static int PEAK_NOTE = 3;
    public final static int PEAK_FUNDAMENTAL = 4;
    public final static int MAX_PEAKS = 60;
    public final static int NUM_HARMONY = 3;
    public final static int NUM_NOTES = 88;
    final float thresh = (float) 0.01;
    static int bufferCounter = 0;

    Countdown countdown;
    Activity activity;
    volatile boolean keepRun;

    /**
     * first run?
     */
    boolean threadRunning;

    /**
     * listening thread
     */
    Thread listThread;

    static Bitmap rewindImage;
    /**
     * The rewind image
     */
    static Bitmap playImage;
    /**
     * The play image
     */
    static Bitmap pauseImage;
    /**
     * The pause image
     */
    static Bitmap stopImage;
    /**
     * The stop image
     */
    static Bitmap fastFwdImage;
    /**
     * The fast forward image
     */
    static Bitmap volumeImage;
    /**
     * The volume image
     */

    private ImageButton rewindButton;
    /**
     * The rewind button
     */
    private ImageButton playButton;
    /**
     * The play/pause button
     */
    private ImageButton stopButton;
    /**
     * The stop button
     */
    private ImageButton fastFwdButton;
    /**
     * The fast forward button
     */
    private TextView speedText;
    /**
     * The "Speed" label
     */
    private SeekBar speedBar;
    /**
     * The seekbar for controlling the playback speed
     */

    int playstate;
    /**
     * The playing state of the Midi Player
     */
    final int stopped = 1;
    /**
     * Currently stopped
     */
    final int playing = 2;
    /**
     * Currently playing music
     */
    final int paused = 3;
    /**
     * Currently paused
     */
    final int initStop = 4;
    /**
     * Transitioning from playing to stop
     */
    final int initPause = 5;
    /**
     * Transitioning from playing to pause
     */

    final String tempSoundFile = "playing.mid";
    /**
     * The filename to play sound from
     */

    MediaPlayer player;
    /**
     * For playing the audio
     */
    MidiFile midifile;
    /**
     * The midi file to play
     */
    MidiOptions options;
    /**
     * The sound options for playing the midi file
     */
    double pulsesPerMsec;
    /**
     * The number of pulses per millisec
     */
    SheetMusic sheet;
    /**
     * The sheet music to shade while playing
     */
    Piano piano;
    /**
     * The piano to shade while playing
     */
    Handler timer;
    /**
     * Timer used to update the sheet music while playing
     */
    long startTime;
    /**
     * Absolute time when music started playing (msec)
     */
    double startPulseTime;
    /**
     * Time (in pulses) when music started playing
     */
    double currentPulseTime;
    /**
     * Time (in pulses) music is currently at
     */
    double prevPulseTime;
    /**
     * Time (in pulses) music was last at
     */
    Context context;            /** The context, for writing midi files */


    /**
     * Load the play/pause/stop button images
     */
    public static void LoadImages(Context context) {
        if (rewindImage != null) {
            return;
        }
        Resources res = context.getResources();
        rewindImage = BitmapFactory.decodeResource(res, R.drawable.rewind);
        playImage = BitmapFactory.decodeResource(res, R.drawable.play);
        pauseImage = BitmapFactory.decodeResource(res, R.drawable.pause);
        stopImage = BitmapFactory.decodeResource(res, R.drawable.stop);
        fastFwdImage = BitmapFactory.decodeResource(res, R.drawable.fastforward);
    }


    /**
     * Create a new MidiPlayer, displaying the play/stop buttons, and the
     * speed bar.  The midifile and sheetmusic are initially null.
     */
    public MidiPlayer(Context context) {
        super(context);
        this.activity = activity;
        threadRunning = false;
        LoadImages(context);
        this.context = context;
        this.midifile = null;
        this.options = null;
        this.sheet = null;
        timeCounter.playstate = stopped;
        startTime = SystemClock.uptimeMillis();
        startPulseTime = 0;
        currentPulseTime = 0;
        prevPulseTime = -10;
        this.setPadding(0, 0, 0, 0);
        CreateButtons();

        activity = (Activity) context;
        int screenwidth = activity.getWindowManager().getDefaultDisplay().getWidth();
        int screenheight = activity.getWindowManager().getDefaultDisplay().getHeight();
        Point newsize = MidiPlayer.getPreferredSize(screenwidth, screenheight);
        resizeButtons(newsize.x, newsize.y);
        player = new MediaPlayer();
        setBackgroundColor(Color.BLACK);
    }

    /**
     * Get the preferred width/height given the screen width/height
     */
    public static Point getPreferredSize(int screenwidth, int screenheight) {
        int height = (int) (5.0 * screenwidth / (2 + Piano.KeysPerOctave * Piano.MaxOctave));
        height = height * 2 / 3;
        Point result = new Point(screenwidth, height);
        return result;
    }

    /**
     * Determine the measured width and height.
     * Resize the individual buttons according to the new width/height.
     */
    @Override
    protected void onMeasure(int widthspec, int heightspec) {
        super.onMeasure(widthspec, heightspec);
        int screenwidth = MeasureSpec.getSize(widthspec);
        int screenheight = MeasureSpec.getSize(heightspec);

        /* Make the button height 2/3 the piano WhiteKeyHeight */
        int width = screenwidth;
        int height = (int) (5.0 * screenwidth / (2 + Piano.KeysPerOctave * Piano.MaxOctave));
        height = height * 2 / 3;
        setMeasuredDimension(width, height);
    }

    /**
     * When this view is resized, adjust the button sizes
     */
    @Override
    protected void
    onSizeChanged(int newwidth, int newheight, int oldwidth, int oldheight) {
        resizeButtons(newwidth, newheight);
        super.onSizeChanged(newwidth, newheight, oldwidth, oldheight);
    }


    /**
     * Create the rewind, play, stop, and fast forward buttons
     */
    void CreateButtons() {
        this.setOrientation(LinearLayout.HORIZONTAL);

        /* Create the rewind button */
        rewindButton = new ImageButton(context);
        rewindButton.setBackgroundColor(Color.BLACK);
        rewindButton.setImageBitmap(rewindImage);
        rewindButton.setScaleType(ImageView.ScaleType.FIT_XY);
        rewindButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Rewind();
            }
        });
        this.addView(rewindButton);

        /* Create the stop button */
        stopButton = new ImageButton(context);
        stopButton.setBackgroundColor(Color.BLACK);
        stopButton.setImageBitmap(stopImage);
        stopButton.setScaleType(ImageView.ScaleType.FIT_XY);
        stopButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Stop();
            }
        });
        this.addView(stopButton);

        
        /* Create the play button */
        playButton = new ImageButton(context);
        playButton.setBackgroundColor(Color.BLACK);
        playButton.setImageBitmap(playImage);
        playButton.setScaleType(ImageView.ScaleType.FIT_XY);
        playButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Play();
            }
        });
        this.addView(playButton);        
        
        /* Create the fastFwd button */
        fastFwdButton = new ImageButton(context);
        fastFwdButton.setBackgroundColor(Color.BLACK);
        fastFwdButton.setImageBitmap(fastFwdImage);
        fastFwdButton.setScaleType(ImageView.ScaleType.FIT_XY);
        fastFwdButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                FastForward();
            }
        });
        this.addView(fastFwdButton);


        /* Create the Speed bar */
        speedText = new TextView(context);
        speedText.setText("   Speed:    ");
        speedText.setGravity(Gravity.CENTER);
        this.addView(speedText);

        speedBar = new SeekBar(context);
        speedBar.setMax(100);
        speedBar.setProgress(100);
        this.addView(speedBar);

        /* Initialize the timer used for playback, but don't start
         * the timer yet (enabled = false).
         */
        timer = new Handler();
    }

    void resizeButtons(int newwidth, int newheight) {
        int buttonheight = newheight;
        int pad = buttonheight / 6;
        rewindButton.setPadding(pad, pad, pad, pad);
        stopButton.setPadding(pad, pad, pad, pad);
        playButton.setPadding(pad, pad, pad, pad);
        fastFwdButton.setPadding(pad, pad, pad, pad);

        LinearLayout.LayoutParams params;

        params = new LinearLayout.LayoutParams(buttonheight, buttonheight);
        params.width = buttonheight;
        params.height = buttonheight;
        params.bottomMargin = 0;
        params.topMargin = 0;
        params.rightMargin = 0;
        params.leftMargin = buttonheight / 6;

        rewindButton.setLayoutParams(params);

        params = new LinearLayout.LayoutParams(buttonheight, buttonheight);
        params.bottomMargin = 0;
        params.topMargin = 0;
        params.rightMargin = 0;
        params.leftMargin = 0;

        playButton.setLayoutParams(params);
        stopButton.setLayoutParams(params);
        fastFwdButton.setLayoutParams(params);

        params = (LinearLayout.LayoutParams) speedText.getLayoutParams();
        params.height = buttonheight;
        speedText.setLayoutParams(params);

        params = new LinearLayout.LayoutParams(buttonheight * 6, buttonheight);
        params.width = buttonheight * 6;
        params.bottomMargin = 0;
        params.leftMargin = 0;
        params.topMargin = 0;
        params.rightMargin = 0;
        speedBar.setLayoutParams(params);
        speedBar.setPadding(pad, pad, pad, pad);

    }

    public void SetPiano(Piano p) {
        piano = p;
    }

    /**
     * The MidiFile and/or SheetMusic has changed. Stop any playback sound,
     * and store the current midifile and sheet music.
     */
    public void SetMidiFile(MidiFile file, MidiOptions opt, SheetMusic s) {

        /* If we're paused, and using the same midi file, redraw the
         * highlighted notes.
         */
        if ((file == midifile && midifile != null && timeCounter.playstate == paused)) {
            options = opt;
            sheet = s;
            //Log.d("shading", "123456789");

            sheet.ShadeNotes((int) currentPulseTime, (int) -1, false);

            /* We have to wait some time (200 msec) for the sheet music
             * to scroll and redraw, before we can re-shade.
             */
            timer.removeCallbacks(TimerCallback);
            timer.postDelayed(ReShade, 500);
        } else {
            Stop();
            midifile = file;
            options = opt;
            sheet = s;
        }
    }

    /**
     * If we're paused, reshade the sheet music and piano.
     */
    Runnable ReShade = new Runnable() {
        public void run() {
            if (timeCounter.playstate == paused || timeCounter.playstate == stopped) {
//                //Log.d("shading" , "9999999595988");

                sheet.ShadeNotes((int) currentPulseTime, (int) -10, false);
                piano.ShadeNotes((int) currentPulseTime, (int) prevPulseTime);
            }
        }
    };


    /**
     * Return the number of tracks selected in the MidiOptions.
     * If the number of tracks is 0, there is no sound to play.
     */
    private int numberTracks() {
        int count = 0;
        for (int i = 0; i < options.tracks.length; i++) {
            if (options.tracks[i] && !options.mute[i]) {
                count += 1;
            }
        }
        return count;
    }

    /**
     * Create a new midi file with all the MidiOptions incorporated.
     * Save the new file to playing.mid, and store
     * this temporary filename in tempSoundFile.
     */
    private void CreateMidiFile() {
        double inverse_tempo = 1.0 / midifile.getTime().getTempo();
        double inverse_tempo_scaled = inverse_tempo * speedBar.getProgress() / 100.0;
        // double inverse_tempo_scaled = inverse_tempo * 100.0 / 100.0;
        options.tempo = (int) (1.0 / inverse_tempo_scaled);
        pulsesPerMsec = midifile.getTime().getQuarter() * (1000.0 / options.tempo);

        try {
            FileOutputStream dest = context.openFileOutput(tempSoundFile, Context.MODE_PRIVATE);
            midifile.ChangeSound(dest, options);
            dest.close();
            // checkFile(tempSoundFile);
        } catch (IOException e) {
            Toast toast = Toast.makeText(context, "Error: Unable to create MIDI file for playing.", Toast.LENGTH_LONG);
            toast.show();
        }
    }

    private void checkFile(String name) {
        try {
            FileInputStream in = context.openFileInput(name);
            byte[] data = new byte[4096];
            int total = 0, len = 0;
            while (true) {
                len = in.read(data, 0, 4096);
                if (len > 0)
                    total += len;
                else
                    break;
            }
            in.close();
            data = new byte[total];
            in = context.openFileInput(name);
            int offset = 0;
            while (offset < total) {
                len = in.read(data, offset, total - offset);
                if (len > 0)
                    offset += len;
            }
            in.close();
            MidiFile testmidi = new MidiFile(data, name);
        } catch (IOException e) {
            Toast toast = Toast.makeText(context, "CheckFile: " + e.toString(), Toast.LENGTH_LONG);
            toast.show();
        } catch (MidiFileException e) {
            Toast toast = Toast.makeText(context, "CheckFile midi: " + e.toString(), Toast.LENGTH_LONG);
            toast.show();
        }
    }


    /**
     * Play the sound for the given MIDI file
     */
    private void PlaySound(String filename) {
        if (player == null)
            return;
        try {
            FileInputStream input = context.openFileInput(filename);
            player.reset();
            player.setDataSource(input.getFD());
            input.close();
            player.prepare();
            player.start();
        } catch (IOException e) {
            Toast toast = Toast.makeText(context, "Error: Unable to play MIDI sound", Toast.LENGTH_LONG);
            toast.show();
        }
    }

    /**
     * Stop playing the MIDI music
     */
    private void StopSound() {
        if (player == null)
            return;
        player.stop();
        player.reset();
    }


    /**
     * The callback for the play button.
     * If we're stopped or pause, then play the midi file.
     */
    private void Play() {
//        callBack CB = new callBack();
        listenThreadCreator creator = new listenThreadCreator(midifile, new callBack());
        if (midifile == null || sheet == null || numberTracks() == 0) {
            return;
        } else if (timeCounter.playstate == initStop || timeCounter.playstate == initPause || timeCounter.playstate == playing) {
            return;
        }
        // playstate is stopped or paused

        // Hide the midi player, wait a little for the view
        // to refresh, and then start playing

        /**
         * Idea:
         * listener.start.
         * the listener will contain the activite, etc.
         * get current time.
         * check not paused etc.
         * call shades etc.
         *
         */


        this.setVisibility(View.GONE);

        if (!threadRunning) {
            timeCounter.playstate = playing;
//            playstate = playing;

            timeCounter.firstRun = true;
            sheet.cleanCounters();
            threadRunning = true;


            listThread = creator.getThread();
            countdown = creator.getCountDown();
            countdown.start();
//            listThread.start();
        } else {
            countdown.setThreadStart(false);
            countdown.start();
//            timeCounter.firstRun = true;

            sheet.cleanCounters();

        }
//        listThread = creator.getThread();
//        listThread.start();
        //        timer.removeCallbacks(TimerCallback);
//        timer.postDelayed(DoPlay, 1000);
    }

    Runnable DoPlay = new Runnable() {
        public void run() {
            Activity activity = (Activity) context;
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        /* The startPulseTime is the pulse time of the midi file when
         * we first start playing the music.  It's used during shading.
         */
            if (options.playMeasuresInLoop) {
            /* If we're playing measures in a loop, make sure the
             * currentPulseTime is somewhere inside the loop measures.
             */
                int measure = (int) (currentPulseTime / midifile.getTime().getMeasure());
                if ((measure < options.playMeasuresInLoopStart) ||
                        (measure > options.playMeasuresInLoopEnd)) {
                    currentPulseTime = options.playMeasuresInLoopStart * midifile.getTime().getMeasure();
                }
                startPulseTime = currentPulseTime;
                options.pauseTime = (int) (currentPulseTime - options.shifttime);
            } else if (timeCounter.playstate == paused) {
                startPulseTime = currentPulseTime;
                options.pauseTime = (int) (currentPulseTime - options.shifttime);
            } else {
                options.pauseTime = 0;
                startPulseTime = options.shifttime;
                currentPulseTime = options.shifttime;
                prevPulseTime = options.shifttime - midifile.getTime().getQuarter();
            }

            CreateMidiFile();
            timeCounter.playstate = playing;
            PlaySound(tempSoundFile);
            startTime = SystemClock.uptimeMillis();

            timer.removeCallbacks(TimerCallback);
            timer.removeCallbacks(ReShade);
            timer.postDelayed(TimerCallback, 100);
//            //Log.d("shading" , "2222222222222222222");

            sheet.ShadeNotes((int) currentPulseTime, (int) prevPulseTime, true);
            piano.ShadeNotes((int) currentPulseTime, (int) prevPulseTime);
            return;
        }
    };


    /**
     * The callback for pausing playback.
     * If we're currently playing, pause the music.
     * The actual pause is done when the timer is invoked.
     */
    public void Pause() {
        this.setVisibility(View.VISIBLE);
        LinearLayout layout = (LinearLayout) this.getParent();
        layout.requestLayout();
        this.requestLayout();
        this.invalidate();

        Activity activity = (Activity) context;
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (midifile == null || sheet == null || numberTracks() == 0) {
            return;
        } else if (timeCounter.playstate == playing) {
            timeCounter.playstate = initPause;
            return;
        }
    }


    /**
     * The callback for the Stop button.
     * If playing, initiate a stop and wait for the timer to finish.
     * Then do the actual stop.
     */
    void Stop() {
        this.setVisibility(View.VISIBLE);
        if (midifile == null || sheet == null || timeCounter.playstate == stopped) {
            return;
        }

        if (timeCounter.playstate == initPause || timeCounter.playstate == initStop || timeCounter.playstate == playing) {
            /* Wait for timer to finish */
            timeCounter.playstate = initStop;
            DoStop();
        } else if (timeCounter.playstate == paused) {

            DoStop();
        }
    }

    /**
     * Perform the actual stop, by stopping the sound,
     * removing any shading, and clearing the state.
     */
    void DoStop() {
        timeCounter.playstate = stopped;
        timer.removeCallbacks(TimerCallback);
        //Log.d("shading", "333333333333333");

        sheet.ShadeNotes(-10, (int) prevPulseTime, false);
        sheet.ShadeNotes(-10, (int) currentPulseTime, false);
        piano.ShadeNotes(-10, (int) prevPulseTime);
        piano.ShadeNotes(-10, (int) currentPulseTime);
        startPulseTime = 0;
        currentPulseTime = 0;
        prevPulseTime = 0;
        setVisibility(View.VISIBLE);
        StopSound();
    }

    /**
     * Rewind the midi music back one measure.
     * The music must be in the paused state.
     * When we resume in playPause, we start at the currentPulseTime.
     * So to rewind, just decrease the currentPulseTime,
     * and re-shade the sheet music.
     */
    void Rewind() {
        if (midifile == null || sheet == null || timeCounter.playstate != paused) {
            return;
        }

        /* Remove any highlighted notes */
        //Log.d("shading", "44444444444");

        sheet.ShadeNotes(-10, (int) currentPulseTime, false);
        piano.ShadeNotes(-10, (int) currentPulseTime);

        prevPulseTime = currentPulseTime;
        currentPulseTime -= midifile.getTime().getMeasure();
        if (currentPulseTime < options.shifttime) {
            currentPulseTime = options.shifttime;
        }
        //Log.d("shading", "555555555555555555");

        sheet.ShadeNotes((int) currentPulseTime, (int) prevPulseTime, false);
        piano.ShadeNotes((int) currentPulseTime, (int) prevPulseTime);
    }

    /**
     * Fast forward the midi music by one measure.
     * The music must be in the paused/stopped state.
     * When we resume in playPause, we start at the currentPulseTime.
     * So to fast forward, just increase the currentPulseTime,
     * and re-shade the sheet music.
     */
    void FastForward() {
        if (midifile == null || sheet == null) {
            return;
        }
        if (timeCounter.playstate != paused && timeCounter.playstate != stopped) {
            return;
        }
        timeCounter.playstate = paused;

        /* Remove any highlighted notes */
        //Log.d("shading", "6666666666666");

        sheet.ShadeNotes(-10, (int) currentPulseTime, false);
        piano.ShadeNotes(-10, (int) currentPulseTime);

        prevPulseTime = currentPulseTime;
        currentPulseTime += midifile.getTime().getMeasure();
        if (currentPulseTime > midifile.getTotalPulses()) {
            currentPulseTime -= midifile.getTime().getMeasure();
        }
        //Log.d("shading", "777777777777777");

        sheet.ShadeNotes((int) currentPulseTime, (int) prevPulseTime, false);
        piano.ShadeNotes((int) currentPulseTime, (int) prevPulseTime);
    }


    /**
     * The callback for the timer. If the midi is still playing,
     * update the currentPulseTime and shade the sheet music.
     * If a stop or pause has been initiated (by someone clicking
     * the stop or pause button), then stop the timer.
     */
    Runnable TimerCallback = new Runnable() {
        public void run() {
            if (midifile == null || sheet == null) {
                timeCounter.playstate = stopped;
                return;
            } else if (timeCounter.playstate == stopped || timeCounter.playstate == paused) {
            /* This case should never happen */
                return;
            } else if (timeCounter.playstate == initStop) {
                return;
            } else if (timeCounter.playstate == playing) {
                long msec = SystemClock.uptimeMillis() - startTime;
                prevPulseTime = currentPulseTime;
                currentPulseTime = startPulseTime + msec * pulsesPerMsec;

            /* If we're playing in a loop, stop and restart */
                if (options.playMeasuresInLoop) {
                    double nearEndTime = currentPulseTime + pulsesPerMsec * 10;
                    int measure = (int) (nearEndTime / midifile.getTime().getMeasure());
                    if (measure > options.playMeasuresInLoopEnd) {
                        RestartPlayMeasuresInLoop();
                        return;
                    }
                }

            /* Stop if we've reached the end of the song */
                if (currentPulseTime > midifile.getTotalPulses()) {
                    DoStop();
                    return;
                }
//                //Log.d("shading" , "8888888888888888888");

                sheet.ShadeNotes((int) currentPulseTime, (int) prevPulseTime, true);
                piano.ShadeNotes((int) currentPulseTime, (int) prevPulseTime);
                timer.postDelayed(TimerCallback, 100);
                return;
            } else if (timeCounter.playstate == initPause) {
                long msec = SystemClock.uptimeMillis() - startTime;
                StopSound();

                prevPulseTime = currentPulseTime;
                currentPulseTime = startPulseTime + msec * pulsesPerMsec;
//                //Log.d("shading" , "9999999999999999");

                sheet.ShadeNotes((int) currentPulseTime, (int) prevPulseTime, false);
                piano.ShadeNotes((int) currentPulseTime, (int) prevPulseTime);
                timeCounter.playstate = paused;
                timer.postDelayed(ReShade, 1000);
                return;
            }
        }
    };


    /**
     * The "Play Measures in a Loop" feature is enabled, and we've reached
     * the last measure. Stop the sound, unshade the music, and then
     * start playing again.
     */

    private void RestartPlayMeasuresInLoop() {
        timeCounter.playstate = stopped;
        //Log.d("shading", "10010101010101011");

        piano.ShadeNotes(-10, (int) prevPulseTime);
        sheet.ShadeNotes(-10, (int) prevPulseTime, false);
        currentPulseTime = 0;
        prevPulseTime = -1;
        StopSound();
        timer.postDelayed(DoPlay, 300);
    }


    // ----------------------------------------------
    // ------------- runable implementation----------
    // ----------------------------------------------

    /**
     * The callback for the timer. If the midi is still playing,
     * update the currentPulseTime and shade the sheet music.
     * If a stop or pause has been initiated (by someone clicking
     * the stop or pause button), then stop the timer.
     */
    public class callBack implements Runnable {
        float[][] extraNotes;
        double currentTime;
        double currentPulseTime;
        Buffer buffer;

        public callBack() {
            currentPulseTime = 0;
        }

        public void setPeaks(Buffer buffer, double time, boolean silence) {
            this.buffer = buffer;
            currentTime = time;
        }

        @Override
        public void run() {
//                //Log.d("shades", "curr time: " + currentTime);
//                //Log.d("shades", "playing: " +  timeCounter.playstate);

            if (midifile == null || sheet == null) {
                timeCounter.playstate = stopped;
                return;
            } else if (timeCounter.playstate == stopped || timeCounter.playstate == paused) {
            /* This case should never happen */
                return;
            } else if (timeCounter.playstate == initStop) {
                return;
            } else if (timeCounter.playstate == playing) {
//                    long msec = SystemClock.uptimeMillis() - startTime;
                prevPulseTime = Math.round(currentPulseTime);

                currentPulseTime = Math.round(currentTime);
//                    //Log.d("shades" , "curr: " + currentPulseTime + " prev: " + prevPulseTime);

//                    currentPulseTime = startPulseTime + msec * pulsesPerMsec;

//            /* If we're playing in a loop, stop and restart */
//                    if (options.playMeasuresInLoop) {
//                        double nearEndTime = currentPulseTime + pulsesPerMsec * 10;
//                        int measure = (int) (nearEndTime / midifile.getTime().getMeasure());
//                        if (measure > options.playMeasuresInLoopEnd) {
//                            RestartPlayMeasuresInLoop();
//                            return;
//                        }
//                    }

            /* Stop if we've reached the end of the song */
                if (currentPulseTime > midifile.getTotalPulses()) {
                    DoStop();
                    return;
                }
                //Log.d("shading", "calling shading");
                sheet.ShadeNotes((int) currentPulseTime, (int) prevPulseTime, true, buffer);
                piano.ShadeNotes((int) currentPulseTime, (int) prevPulseTime);
//                    timer.postDelayed(TimerCallback, 312);
                return;
            } else if (timeCounter.playstate == initPause) {
                //Log.d("shading", "1111111111111111111");

                long msec = SystemClock.uptimeMillis() - startTime;
                StopSound();

                prevPulseTime = currentPulseTime;
                currentPulseTime = startPulseTime + msec * pulsesPerMsec;
                sheet.ShadeNotes((int) currentPulseTime, (int) prevPulseTime, false);
                piano.ShadeNotes((int) currentPulseTime, (int) prevPulseTime);
                timeCounter.playstate = paused;
                timer.postDelayed(ReShade, 1000);
                return;
            }
        }


    }

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

        ArrayList<com.abitbol.ophir.iplay.MidiNote> notes; // will hold the notes of the song
        double[][] tempExpNotes = new double[5][4]; // will hold the current notes we are looking for
        double[][] expNotes = new double[5][4]; // will hold the current notes we are looking for
        int[] noteDB; // array of possible notes, according to bufferSize;
        int[] allNotes; // array of possible notes, according to bufferSize;

        boolean endMidi = false;
        boolean running = false; // is the record thread running

        MidiFile mfile; // the midi file to play
        FileUri midiToPlay; // uri of the midi file
        int BPM, PPQ; // the tempo of the song
        double p2s; // pulses to seconds coeff.
        double windowSizeTime, windowSize, fourierCoef; // buffer size in seconds and bits
        int bufferSize;
        final float threshold = (float) 0.01;
        callBack callback;


        public listenThreadCreator(MidiFile midifile, callBack callback) {
            mfile = midifile;
            getTempo();
            bufferSize = (int) windowSize;
            fourierCoef = SR / ((windowSize));
            final getNote gtNt = new getNote(bufferSize, fourierCoef);
            noteDB = gtNt.getNotes();
            allNotes = gtNt.getAllNotes();
            this.callback = callback;
        }

        public Thread getThread() {
            final AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(SR, bufferSize, 0);
            dispatcher.addAudioProcessor(createProcess());
            return new Thread(dispatcher, "Audio Dispatcher");

        }

        public Countdown getCountDown() {
            return new Countdown(BPM, getThread(), activity);
        }

        private void getTempo() {

            MidiOptions options = new MidiOptions(mfile);
            BPM = 60000000 / options.tempo;
            PPQ = mfile.returnPPQ();
            p2s = 60000 / (double) ((BPM * PPQ));
            windowSizeTime = 60 / (4 * (double) BPM);
            windowSize = Math.round(windowSizeTime * SR);

        }

        AudioProcessor createProcess() {
            AudioProcessor fftProcessor = new AudioProcessor() {

                // silence detector:
                SilenceDetector silenceDetector = new SilenceDetector();
                double silenceThreshold = SilenceDetector.DEFAULT_SILENCE_THRESHOLD;

                FloatFFT_1D jft = new FloatFFT_1D(bufferSize);

                float[] amplitudes = new float[bufferSize];
                float[] amplitudesErased = new float[bufferSize];
                float[] amplitudesDerivative = new float[bufferSize];
                float[][] possiblePeaks;
                float[][] foundPeaks;
                float[][] foundPeaks2;
                float[][] foundPeaksFinal;
                boolean foundAll;
                boolean foundExtra;
                int foundCounter, foundCounterFinal;
                float extraMidi;
                boolean newExtraAlgo;



                ArrayList<Staff> staffs = sheet.staffs;
                float[] phase = new float[bufferSize];
                double[][] finalPeaks;
                double[][] finalizedPeaks;
                int prevTime = -10;
                int currTime = 0;
                FFT fft = new FFT(bufferSize);

                //            float[] amplitudes = new float[bufferSize / 2];
                float[] finalAmplitudes = new float[bufferSize];
                boolean firstRun = true;
                double timeElapsed;


                int noteIdx = 0;
//                    int maxNotes = notes.size();

                @Override
                public void processingFinished() {
//                        //Log.d("chord" , "interupted!");
                    // TODO Auto-generated method stub
                }

                /**
                 * on each audio event (buffer if full) perform this:
                 * @param audioEvent the buffer event
                 * @return
                 */
                public boolean process(AudioEvent audioEvent) {


                    Log.d("midi", "tempo , windowSizeTime, windowSize, fourier" + BPM + " , " + windowSizeTime + " , " + windowSize + " , " + fourierCoef);

                    float[] audioFloatBuffer = audioEvent.getFloatBuffer();

                    // check to see if there is anything playing:
                    boolean silence = silenceDetector.isSilence(audioFloatBuffer);

                    if (timeCounter.firstRun && !silence) {

//
                        startTime = System.nanoTime();
                        timeCounter.firstRun = false;
                        timeElapsed = 0;
                    } else if (!timeCounter.firstRun) {

                        prevTime = (int) Math.round(timeElapsed / p2s);

                        timeElapsed = ((double) (System.nanoTime() - startTime)) / 1000000.0;
                        currTime = (int) Math.round(timeElapsed / p2s);

                    }
//                        if (firstRun) {
//
//                            timeCounter.startTime = System.nanoTime();
//                            //Log.d("thread" , "time: " + timeCounter.startTime);
//
//                            startTime = System.nanoTime();
//                            firstRun = false;
//                            timeElapsed = 0;
//                        } else {
//                            timeElapsed = ((double) (System.nanoTime() - startTime)) / 1000000.0;
//                        }

                    // the buffer containing the audio data:


                    // if it isn't silent:
                    if (!silence) {

//////               ===============================================================
//////               ========================  Envelope  ===========================
//////               ===============================================================
////
////                    int peakwindowsize = 206;
////                    double average = 0.0;
////                    float tempMax = 0;
////                    for (int i = 0; i < envelope.length - peakwindowsize; i++) {
////
////                        average += envelope[i];
////                        tempMax = 0;
////                        for (int j = i; j < i + peakwindowsize; j++) {
////                            tempMax = (tempMax < envelope[j]) ? envelope[j] : tempMax;
////                        }
////                        envelope[i] = tempMax;
////                    }
////                    for (int i = envelope.length - peakwindowsize; i < envelope.length; i++) {
////                        average += envelope[i];
////                        tempMax = 0;
////                        for (int j = i - peakwindowsize; j < i; j++) {
////                            tempMax = (tempMax < audioFloatBuffer[j]) ? audioFloatBuffer[j] : tempMax;
////                        }
////                        envelope[i] = tempMax;
////                    }
////                    average /= envelope.length;
////
//////               ===============================================================
//////               ========================  find peaks  =========================
//////               ===============================================================
////
////
//////               *-*-*-*-*-*-*-*- variables *-*-*-*-*-*-*-*-
////                    int[]ampPeak = new int[2]; // up to two peaks in a buffer:
////                    int peakIdx = 0;
////                    boolean inPeak = false, continuousNote = false;
////
////                    int peakStartLoc = 0;
////                    int peakEndLoc = 0;
////
////                    double thresh = average;
////                    for (int i = 0; i < envelope.length; i++) {
////
////                        if (!inPeak) { // if we aren't in a peaks
////                            if (envelope[i] > thresh) { // and the amplitude is higher than average
////                                inPeak = true; // then we start a peak
////                                peakStartLoc = i;
////                            }
////                        } else {
////                            if (envelope[i] < thresh) { // and the amplitude is higher than average
////                                inPeak = false; // then we start a peak
////                                peakEndLoc = i;
////
////                                if (peakEndLoc - peakStartLoc > windowSize / 4) {
////                                    ampPeak[PEAK_START] = peakStartLoc;
////                                    ampPeak[PEAK_END] = peakStartLoc;
//////                                    peakIdx++;
////                                }
////
////                            }
////                        }
////
////
////                    }
////
////                    if(inPeak && peakStartLoc<windowSize*7/8)
////                    {
////                        ampPeak[PEAK_START] = peakStartLoc;
////                        ampPeak[1] = (int)windowSize-1;
////                        continuousNote = true;
////                    }
//
//                        float maxAmp = 0;
//
//
//                        // perform the fourier transofrm:
//                        float[] transformbuffer = new float[bufferSize * 2];
//
//                        System.arraycopy(audioFloatBuffer, 0, transformbuffer, 0,
//                                audioFloatBuffer.length);
//
//                        jft.realForward((float[]) transformbuffer);
////                    jft.realForward(transformbuffer);
//                        amplitudes = transformbuffer;
//
//
////                 find peaks:
//                        float max = 0;
//                        float freq = 0;
//                        int numPeaks = 0;
//
//
//                        for (int i = 0; i < amplitudes.length / 2; i++) {
//                            amplitudes[i] = amplitudes[i] * amplitudes[i];
//                            amplitudes[i] = (amplitudes[i]) * noteDB[i];
//                            max = (max < amplitudes[i]) ? amplitudes[i] : max;
//
////                        max = (max < amplitudes[i]) ? amplitudes[i] : max;
//                        }
//
////                        *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-
////                        *-*-*-*-*-*-*-*-* Matlab Addon*-*-*-*-*-*-*-*-*-*-
////                        *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-
////
//                        finalizedPeaks = new double[100][2];
//
//                        double[][] peaks = new double[100][3];
//                        float[] matlabAlgo = amplitudes;
//                        boolean minorPeak = false;
//                        int peakIdx = 0;
//                        for (int i = 0; i < matlabAlgo.length / 2; i++) {
//                            matlabAlgo[i] /=max;
//                            if (matlabAlgo[i] > 0.01
//                                    && matlabAlgo[i] > matlabAlgo[i - 1]
//                                    && matlabAlgo[i] > matlabAlgo[i + 1]) {
//                                if (peakIdx > 0) {
//                                    double n = 12 * Math.log(peaks[peakIdx - 1][1] / 440) + 49;
//                                    double nextNote = Math.pow(2, (n - 48) / 12) * 440;
//                                    int diff = (int) (nextNote - peaks[peakIdx - 1][1]);
//
//
//                                    if (Math.abs((i) * fourierCoef - peaks[peakIdx - 1][0]) < diff * 0.7) {
//                                        if (matlabAlgo[i] > peaks[peakIdx - 1][1]) {
//                                            peakIdx--;
//                                        } else {
//                                            minorPeak = true;
//                                        }
//                                    }
//
//                                }
//
//
//                                if(!minorPeak)
//                                {
//                                    double newFreq = i*fourierCoef;
//
//                                    // limits for search:
//                                    double n = 12 * Math.log(i*fourierCoef / 440) + 49;
//                                    double nextNote = Math.pow(2, (n - 47) / 12) * 440;
//                                    double prevNote = Math.pow(2, (n - 51) / 12) * 440;
//                                   int prevI = Math.round((float) (prevNote/(fourierCoef*2)));
//                                   int nextI = Math.round((float)  (nextNote/(fourierCoef*2)));
//
//                                    if(i>10 && (i+10)<matlabAlgo.length)
//                                    {
//                                        double tmepAv = 0;
//                                        int tempCount = 0;
//                                        for (int avIdx = prevI ; avIdx< i-2 ; avIdx++)
//                                        {
//
//                                            if(matlabAlgo[avIdx]>0)
//                                            {
//                                                tmepAv+= matlabAlgo[avIdx];
//                                                tempCount++;
//                                            }
//                                        }
//                                        for (int avIdx = i+3 ; avIdx< nextI ; avIdx++)
//                                        {
//
//                                            if(matlabAlgo[avIdx]>0)
//                                            {
//                                                tmepAv+= matlabAlgo[avIdx];
//                                                tempCount++;
//                                            }
//                                        }
//
//                                        tmepAv /=tempCount;
//                                        double ratio = (tempCount>0)?matlabAlgo[i]/tmepAv:0;
//                                        peaks[peakIdx][0] = newFreq;
//                                        peaks[peakIdx][1] = i;
//                                        peaks[peakIdx][2] = ratio;
//                                        peakIdx++;
//                                    }
//
//                                }
//
//
//
//
//
//
//                            }
//                            if(peakIdx>60)
//                            {
//                                break;
//                            }
//                        }
//
//
//
////                        get peak average:
//                        int numOfPeaks = 0;
//                        double peakAverage = 0;
//
//                        for (int i = 0 ; i<peakIdx ; i++)
//                        {
//                            peakAverage+=peaks[i][1];
//                        }
//                        peakAverage/=(peakIdx-1);
//
//                        int finalizedPeaksIdx= 0;
//                        for (int i = 0 ; i<peakIdx ; i++)
//                        {
//                            if(peaks[i][2]>peakAverage)
//                            {
//                                for (int j = 0 ; i<peakIdx ; i++)
//                                {
//                                    if(((Math.abs(peaks[i][0] - peaks[j][0]/2)<5) && peaks[j][1]>peakAverage/4 )||
//                                            ((Math.abs(peaks[i][0] - peaks[j][0]/2)<5) && peaks[j][2]/10>0.4 ))
//                                    {
//                                        if(peaks[i][3]/10<0.4)
//                                        {
//                                            finalizedPeaks[finalizedPeaksIdx][0]=peaks[i][0];
//                                            finalizedPeaks[finalizedPeaksIdx][1]=1;
//                                        }
//                                        else
//                                        {
//                                            finalizedPeaks[finalizedPeaksIdx][0]=peaks[i][0];
//                                            finalizedPeaks[finalizedPeaksIdx][1]=0;
//                                        }
//                                        finalizedPeaksIdx++;
//                                    }
//                                }
//
//                            }
//                        }

//
//
//
//                  *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-
//                        *-*-*-*-*-*-*-*-* Matlab Addon end *-*-*-*-*-*-*-*-*-*-
//                        *-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-*-


//                            //Log.d("max spec amp", "spec max: " + max);

//                    --------------------------------------------------------------
//                    -------------------------  HPF  ------------------------------
//                    --------------------------------------------------------------

//                        float[] amplitudesDown2 = new float[bufferSize]; // downsample by half
//                        float[] amplitudesDown3 = new float[bufferSize]; // downsample by half
////                    float[] amplitudesDown4 = new float[bufferSize]; // downsample by half
////                    float[] amplitudesDown5 = new float[bufferSize]; // downsample by half
//
//
//
//
//                        for (int i = 0; i < amplitudesDown2.length / 2; i++) {
//
//                            amplitudesDown2[i] = amplitudes[i * 2];
//                        }
//                        for (int i = 0; i < amplitudesDown3.length / 3; i++) {
//
//                            amplitudesDown3[i] = amplitudes[i * 3];
//                        }
////                    for (int i = 0; i < amplitudesDown3.length / 4; i++) {
////
////                        amplitudesDown4[i] = amplitudes[i * 4];
////                    }
////                    for (int i = 0; i < amplitudesDown3.length / 5; i++) {
////
////                        amplitudesDown5[i] = amplitudes[i * 5];
////                    }
//                        max = 0;
//                        for (int i = 0; i < amplitudes.length / 2; i++) {
//                            finalAmplitudes[i] = (amplitudes[i] * amplitudesDown2[i] * amplitudesDown3[i]);
//                            max = (max < finalAmplitudes[i]) ? finalAmplitudes[i] : max;
//                        }
////                    finalAmplitudes = amplitudes;
//                        for (int i = 0; i < amplitudes.length / 2; i++) {
//                            finalAmplitudes[i] /= max;
//                        }
//                        finalPeaks = new double[100][2];
//                        int numFinalPeaks = 0;
//
//                        for (int i = 1; i < finalAmplitudes.length / 2; i++) {
//
//                            // check some threshold and close values:
//                            if (finalAmplitudes[i] > 0.00001
//                                    && finalAmplitudes[i] > finalAmplitudes[i - 1]
//                                    && finalAmplitudes[i] > finalAmplitudes[i + 1]) {
////                            check for close range
//                                boolean biggestPeak = true;
//                                // get start index and end index for peak checking:
//                                int stIn = ((i - (int) (15 / fourierCoef)) < 0) ? i : (int) (15 / fourierCoef);
//                                int endIn = ((i + (int) (15 / fourierCoef)) > finalAmplitudes.length) ? finalAmplitudes.length - i - 1 : (int) (15 / fourierCoef);
//                                for (int j = -stIn; j < stIn + endIn; j++) {
//                                    //Log.d("DEBUG", "length is: + i =  " + i + " freq: " + (i - 1) * fourierCoef + " amp: " + finalAmplitudes[i]);
//
//                                    if (j != 0) {
//                                        if (0.1 * finalAmplitudes[i] < finalAmplitudes[i + j]) {
//                                            biggestPeak = false;
//                                            break;
//                                        }
//                                    }
//
//                                }
//
//                                if (biggestPeak) {
////                                //Log.d("found freqs FINAL", "i =  " + i + " freq: " + (i) * fourierCoef + " amp: " + finalAmplitudes[i]);
//                                    finalPeaks[numFinalPeaks][0] = i * fourierCoef;
//                                    finalPeaks[numFinalPeaks][1] = finalAmplitudes[i];
//                                    numFinalPeaks++;
//                                }
//
//
//                            }
//                            if (numFinalPeaks > 98) break;
//                        }

                        // bubble sort:
                        ////                        double temp0 = 0, temp1 = 0;
//
//                        for (int i = 0; i < numFinalPeaks; i++) {
//                            for (int j = 1; j < (numFinalPeaks - i); j++) {
//
//                                if (finalPeaks[j - 1][1] > finalPeaks[j][1]) {
//                                    //swap the elements!
//                                    temp0 = finalPeaks[j - 1][0];
//                                    temp1 = finalPeaks[j - 1][1];
//                                    finalPeaks[j - 1][0] = finalPeaks[j][0];
//                                    finalPeaks[j - 1][1] = finalPeaks[j][1];
//                                    finalPeaks[j][0] = temp0;
//                                    finalPeaks[j][1] = temp1;
//                                }
//
//                            }
//                        }

//                        int n = numFinalPeaks;
//                        double temp0 = 0, temp1 = 0;
//
//                        for (int i = 0; i < n; i++) {
//                            for (int j = 1; j < (n - i); j++) {
//
//                                if (finalPeaks[j - 1][1] < finalPeaks[j][1]) {
//                                    //swap the elements!
//                                    temp0 = finalPeaks[j - 1][0];
//                                    temp1 = finalPeaks[j - 1][1];
//
//                                    finalPeaks[j - 1][0] = finalPeaks[j][0];
//                                    finalPeaks[j - 1][1] = finalPeaks[j][1];
//
//                                    finalPeaks[j][0] = temp0;
//                                    finalPeaks[j][1] = temp1;
//
//                                }
//
//                            }
//                        }


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
//                            //Log.d("CORRECT", PitchConverter.midiKeyToHertz((int) (expNotes[0][FREQ])) + "  -  " + finalPeaks[j][0]  );
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
//                    for (int j = 0; j < audioFloatBuffer.length; j++) {
//                        maxAmp = (maxAmp < audioFloatBuffer[j]) ? audioFloatBuffer[j] : maxAmp;
//
//                    }

//                    if (maxAmp > 0.1) {


                        // get the re

                        /**
                         * get the relevant notes:
                         */

//                        int tempPrevPulseTime = (int)Math.round(prevPulseTime);

//                        int tempCurrentPulseTime = (int) Math.round(timeElapsed);
                        Log.d("symbols", "***********[ " + prevTime + " , " + currTime + " ]");

                        ArrayList<Integer> tempSymbols = new ArrayList<Integer>();
                        for (Staff staff : staffs) {
                            tempSymbols.addAll(getCurrentSymbols.getSymbols(currTime, prevTime, staff));
                        }
                        String symbs = "[";
                        for (int note : tempSymbols) {
                            symbs += note + ", ";
                        }
                        symbs += "]";

                        Log.d("symbols", "looking for: " + symbs);

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
                        File log5 = new File(Environment.getExternalStorageDirectory(), "bufferBefore.txt");
                        //Log.d("buffer", "path: " + log.getAbsolutePath());
                        try {
                            BufferedWriter out = new BufferedWriter(new FileWriter(log5.getAbsolutePath(), true));
                            out.write("buffer: \n");

                            for (int i = 1; i < amplitudes.length / 2; ++i) {
                                out.write(amplitudes[i] + "\n");

                            }
                            out.close();
                        } catch (Exception e) {
                            Log.e("buffer", "Error opening Log.", e);
                        }



                        /**
                         * new code:
                         */
                        foundAll = true;
                        String currNotes = "";
                        float[] extraNotes = new float[amplitudes.length];
                        File log2 = new File(Environment.getExternalStorageDirectory(), "notes.txt");

                        //Log.d("buffer", "path: " + log.getAbsolutePath());
                        try {
                            BufferedWriter out = new BufferedWriter(new FileWriter(log2.getAbsolutePath(), true));
                            out.write("****************  "+bufferCounter+": \n");


                            for (int note : tempSymbols) {
                                currNotes+=note+", ";
                                int index = (int) Math.round(PitchConverter.midiKeyToHertz(note) / fourierCoef);
                                out.write("looking for " + note + "in index " + index + " \n");
                                for (int j = 1; j < NUM_HARMONY; j++) {

                                    float average = 0;
                                    out.write("amplitudes[+" + index * j + ", +1 , -1] = " + amplitudes[index * j] + "," + amplitudes[index * j + 1] + "," + amplitudes[index * j - 1] + " \n");

                                    if (amplitudes[index * j] > 0.02 || amplitudes[index * j + 1] > 0.02 || amplitudes[index * j - 1] > 0.02) {
                                    } else {
                                        out.write("note big enough! \n");

                                        foundAll = false;
                                        break;
                                    }


                                }

                                if (!foundAll) {
                                    break;
                                }
                            }
                            if (foundAll) {
                                out.write("found all! \n");

//
//
                            } else {
                                out.write("===== missing ===== \n");

                            }


                            out.close();
                        } catch (Exception e) {
                            Log.e("buffer", "Error opening Log.", e);
                        }

                        System.arraycopy(amplitudes, 0, amplitudesErased, 0, amplitudes.length);

                        if (foundAll) {
//                            erase all found notes and harmonies
                            for (int note : tempSymbols) {
                                int index = (int) Math.round(PitchConverter.midiKeyToHertz(note) / fourierCoef);
                                for (int j = 1; j <= NUM_HARMONY+1; j++) {
                                    amplitudesErased[index * j - 4] = 0;
                                    amplitudesErased[index * j - 3] = 0;
                                    amplitudesErased[index * j - 2] = 0;
                                    amplitudesErased[index * j - 1] = 0;
                                    amplitudesErased[index * j] = 0;
                                    amplitudesErased[index * j + 1] = 0;
                                    amplitudesErased[index * j + 2] = 0;
                                    amplitudesErased[index * j + 3] = 0;
                                    amplitudesErased[index * j + 4] = 0;

                                }
                            }
////
//////
////
//////                                    //look for extra notes:
//                            for (int noteIndex : allNotes) {
//                                if(noteIndex>=copyAmps.length/2)
//                                {
//                                    break;
//                                }
//                                if (noteIndex==0)
//                                {
//                                    noteIndex=1;
//                                }
//                                if (copyAmps[noteIndex] > 0.02 || copyAmps[noteIndex + 1] > 0.02 || copyAmps[noteIndex - 1] > 0.02) {
//                                    if (copyAmps[noteIndex * 2] > 0.02 || copyAmps[noteIndex * 2 + 1] > 0.02 || copyAmps[noteIndex * 2 - 1] > 0.02) {
//                                        extraNotes[noteIndex] = copyAmps[noteIndex] + copyAmps[noteIndex * 2];
//                                        extraNotes[noteIndex] = copyAmps[noteIndex + 1] + copyAmps[noteIndex * 2 + 1];
//                                        extraNotes[noteIndex] = copyAmps[noteIndex - 1] + copyAmps[noteIndex * 2 - 1];
//                                    }
//
//                                }
//
////                            }
////
//                            File logger = new File(Environment.getExternalStorageDirectory(), "extra.txt");
//                            //Log.d("buffer", "path: " + log.getAbsolutePath());
//                            try {
//                                BufferedWriter outter = new BufferedWriter(new FileWriter(logger.getAbsolutePath(), true));
//
//                                outter.write("#########extra notes:#########");
//                                for (int i = 1; i < extraNotes.length / 2; ++i) {
//                                    if (extraNotes[i] > 0.01) {
//                                        outter.write("freq: " + i * fourierCoef + " , amp: " + extraNotes[i] + "\n");
//
//                                    }
//
//                                }
//                                outter.close();
//                            } catch (Exception e) {
//                                Log.e("buffer", "Error opening Log.", e);
//                            }

                        }

                        extraMidi = 0;
                        newExtraAlgo = false;

                        File log6 = new File(Environment.getExternalStorageDirectory(), "extraFinder.txt");
                        //Log.d("buffer", "path: " + log.getAbsolutePath());
                        try {
                            BufferedWriter out = new BufferedWriter(new FileWriter(log6.getAbsolutePath(), true));
                            out.write("**************"+bufferCounter+"********************: \n");
                            for (int i = 1; i < amplitudesErased.length / 2; ++i) {


                                if(amplitudesErased[i]>0.5)
                                {
                                    out.write("found at: "+i+" , freq: "+i*fourierCoef+" \n");

                                    newExtraAlgo = true;
                                    extraMidi = PitchConverter.hertzToMidiKey(i*fourierCoef);
                                    break;
                                }


//                                // if exists amp over 0.5
//                                if(amplitudesErased[i]>0.5)
//                                {
//
//                                    out.write("found index " + i+" amp: "+amplitudesErased[i]+": \n");
//
//
//                                    // check it's second harmony
//                                    for ( int index = -3 ; index<4 ; index++)
//                                    {
//                                        // if second harmony exists
//                                        if(amplitudes[i*2+index]>0.15)
//                                        {
//                                            out.write("found second harmony " + (i*2+index)+" amp: "+amplitudes[i*2+index]+": \n");
//
//
//                                            //check third harmony
//                                            for ( int index2 = -3 ; index2<4 ; index2++)
//                                            {
//                                                if(amplitudes[i*3+index2]>0.1)
//                                                {
//                                                    out.write("found third harmony " + (i*3+index2)+" amp: "+amplitudes[i*3+index2]+": \n");
//
//                                                    newExtraAlgo = true;
//                                                    extraMidi = PitchConverter.hertzToMidiKey(i*fourierCoef);
//                                                    break;
//                                                }
//                                            }
//                                        }
//                                        if(newExtraAlgo) break;
//                                    }
//                                }
//                                if(newExtraAlgo) break;
                            }

                            out.close();
                        } catch (Exception e) {
                            Log.e("buffer", "Error opening Log.", e);
                        }




                            File logg = new File(Environment.getExternalStorageDirectory(), "bufferAfter.txt");
                        //Log.d("buffer", "path: " + log.getAbsolutePath());
                        try {
                            BufferedWriter out = new BufferedWriter(new FileWriter(logg.getAbsolutePath(), true));
                            out.write("buffer: \n");

                            for (int i = 1; i < amplitudesErased.length / 2; ++i) {
                                out.write(amplitudesErased[i] + "\n");

                            }
                            out.close();
                        } catch (Exception e) {
                            Log.e("buffer", "Error opening Log.", e);
                        }
//
//
//                        /**
//                         * perform search twice, once with regular buffer, and once with changed buffer.
//                         * compare found peaks:
//                         */
//                        for (int k = 0 ; k<2 ; k++)
//                        {
//
//                            if(k==1)
//                            {
//                                amplitudes=amplitudesErased;
//                            }
//                /*
//                        Look for extra notes played:
//                         */
//
//                            /**
//                             * this will hold the possible peaks:
//                             * 0 = location;
//                             * 1 = frequency
//                             * 2 = amplitude
//                             */
//                            possiblePeaks = new float[MAX_PEAKS][3];
//                            int peakCounter = 0;
//                            foundExtra = false;
//                            boolean full = false;
//                            boolean minorPeak;
//                            for (int i = 1; i < amplitudes.length / 2; i++) {
//                                minorPeak = false; //   restart bool:
//
//                                // find peaks:
//                                if (amplitudes[i] > thresh
//                                        && amplitudes[i] > amplitudes[i - 1]
//                                        && amplitudes[i] > amplitudes[i + 1]) {
//
//                                    // check for close range peaks:
//                                    if (peakCounter > 0) {
//                                        float lastFreq = possiblePeaks[peakCounter - 1][PEAK_FREQ];
//                                        double n = 12 * Math.log((lastFreq / 400)) + 49;
//                                        // find the next note in relation to the previous peak
//                                        double nextNote = Math.pow(2, (n - 48) / 12) * 440;
////                                   frequency between them:
//                                        double diff = nextNote - lastFreq;
//
////                                    if the actual difference is less than diff, find the bigger one:
//                                        if (Math.abs(i * fourierCoef - lastFreq) < diff * 0.7) {
//                                            if (amplitudes[i] > possiblePeaks[peakCounter - 1][PEAK_AMP]) {
//                                                peakCounter--;
//                                            } else {
//                                                minorPeak = true;
//                                            }
//                                        }
//
//                                    }
//
//                                    if (!minorPeak) {
//
//                                        possiblePeaks[peakCounter][PEAK_LOC] = i;
//                                        possiblePeaks[peakCounter][PEAK_FREQ] = (float) (i * fourierCoef);
//                                        possiblePeaks[peakCounter][PEAK_AMP] = amplitudes[i];
//                                        peakCounter++;
//                                    }
//                                }
//
//                                if (peakCounter >= 60) {
//                                    break;
//                                }
//                            }
////                    find the peak average:
//                            float peakAverage = 0;
//                            int numOfPeaks = 0;
//                            for (int i = 0; i < peakCounter; i++) {
//                                if (possiblePeaks[i][PEAK_AMP] > 0) {
//                                    peakAverage += possiblePeaks[i][PEAK_AMP] * possiblePeaks[i][PEAK_AMP];
//                                    numOfPeaks++;
//                                }
//                            }
//                            peakAverage /= numOfPeaks;
//
//                            float[] tempSolution = new float[amplitudes.length];
//                            System.arraycopy(amplitudes, 0, tempSolution, 0, amplitudes.length);
//                            foundPeaks = new float[peakCounter][7];
//                            boolean foundSecond;
//                            foundCounter= 0;
//                            for (int i = 0; i < peakCounter; i++) {
//
//                                foundSecond = false;
//                                // look for peak with amp thresh:
//                                if (possiblePeaks[i][PEAK_FREQ] < 2500 && amplitudes[(int) possiblePeaks[i][PEAK_LOC]]>0 && possiblePeaks[i][PEAK_AMP] > peakAverage / 3) {
//
//                                    // look for second harmony
//                                    for (int j = i + 1; j < peakCounter; j++) {
//                                        if (!foundSecond) {
//                                            if (Math.abs(possiblePeaks[i][PEAK_FREQ]-possiblePeaks[j][PEAK_FREQ]/2)<8
//                                                    &&amplitudes[(int)possiblePeaks[j][PEAK_LOC]]>peakAverage*0.75)
//                                            {
//
//
//                                                //get surrounding average:
//                                                float average = getAverage((int) possiblePeaks[i][PEAK_LOC], tempSolution, fourierCoef);
//
////                                    get ratio:
//                                                float ratio = amplitudes[(int) possiblePeaks[i][PEAK_LOC]] / average;
//
//
//                                                if (ratio >= 2.5) {
////                                        add to found peaks:
//
//                                                    foundPeaks[foundCounter][PEAK_LOC] = possiblePeaks[i][PEAK_LOC];
//                                                    foundPeaks[foundCounter][PEAK_AMP] = possiblePeaks[i][PEAK_AMP];
//                                                    foundPeaks[foundCounter][PEAK_FREQ] = possiblePeaks[i][PEAK_FREQ];
//                                                    foundPeaks[foundCounter][PEAK_NOTE] = PitchConverter.hertzToMidiKey((double) possiblePeaks[i][PEAK_FREQ]);
//                                                    foundPeaks[foundCounter][PEAK_FUNDAMENTAL] = 0;
//
//
//                                                    foundCounter++;
//
////                                      erase the 2nd harmony from the solution:
//                                                    tempSolution[(int) (possiblePeaks[i][PEAK_LOC] * 2 - 2)] = 0;
//                                                    tempSolution[(int) (possiblePeaks[i][PEAK_LOC] * 2 - 1)] = 0;
//                                                    tempSolution[(int) (possiblePeaks[i][PEAK_LOC] * 2)] = 0;
//                                                    tempSolution[(int) (possiblePeaks[i][PEAK_LOC] * 2 + 1)] = 0;
//                                                    tempSolution[(int) (possiblePeaks[i][PEAK_LOC] * 2 + 2)] = 0;
//                                                    foundSecond = true;
//                                                }
//
//                                            }
//                                        } else {
//                                            break;
//                                        }
//
//
//                                    }
//                                }
//
//
//                            }
//
//
////                            File log3 = new File(Environment.getExternalStorageDirectory(), "foundExtraPeaks.txt");
////                            //Log.d("buffer", "path: " + log.getAbsolutePath());
////                            try {
////                                BufferedWriter out3 = new BufferedWriter(new FileWriter(log3.getAbsolutePath(), true));
////                                out3.write("****************  "+bufferCounter+": \n");
////                                for (int i = 0; i < foundCounter; i++) {
////                                    out3.write("[freq: " + foundPeaks[i][PEAK_FREQ] + "],[amp: " + foundPeaks[i][PEAK_AMP] + " ] \n");
////                                }
////                                out3.close();
////                            } catch (Exception e) {
////                                Log.e("buffer", "Error opening Log.", e);
////                            }
//                            // look for third harmonic
//
//                            if(k==0)
//                            {
//                                foundPeaks2 = new float[foundCounter][7];
//
//                                for (int i = 0; i < foundCounter; i++) {
//
//                                    foundPeaks2[i] = Arrays.copyOf(foundPeaks[i], foundPeaks[i].length);
//                                }
//                            }
//                            else //compare:
//                            {
//                                foundPeaksFinal = new float[Math.min(foundPeaks2.length,foundCounter)][7];
//                                foundCounterFinal = 0;
//                                foundExtra = false;
//
//
//
//                                File log = new File(Environment.getExternalStorageDirectory(), "extraCompare.txt");
//                                //Log.d("buffer", "path: " + log.getAbsolutePath());
//                                try {
//                                    BufferedWriter out = new BufferedWriter(new FileWriter(log.getAbsolutePath(), true));
//                                    out.write("=============="+bufferCounter+"=============== \n");
//                                    out.write("looking for "+currNotes+" \n");
//
//                                    for (int i = 0; i < foundCounter; i++) {
//                                        out.write("looking for a match for: "+foundPeaks[i][PEAK_NOTE]+" \n");
//                                        out.write("----------------------- \n");
//
//                                        for (int j = 0; j < foundPeaks2.length; j++) {
//                                            out.write(foundPeaks[i][PEAK_NOTE]+"=?="+foundPeaks2[j][PEAK_NOTE]+" \n");
//                                            if(foundPeaks[i][PEAK_NOTE]==foundPeaks2[j][PEAK_NOTE])
//                                            {
//                                                out.write("found! \n");
//
//                                                foundPeaksFinal[foundCounterFinal] = Arrays.copyOf(foundPeaks[i], foundPeaks[i].length);
//
//                                                foundCounterFinal++;
//                                                foundExtra = true;
//                                                break;
//                                            }
//
//                                        }
//                                        out.write("# # # # # # # # # # # # #  # # # # \n");
//
//                                    }
//
//                                    out.write("{{{{   FOUND: }}}}\n");
//
//                                    for (int j = 0; j < foundCounterFinal; j++) {
//                                        out.write(foundPeaksFinal[j][PEAK_NOTE]+" \n");
//                                    }
//
//                                        out.write("========================================= \n");
//
//                                    out.close();
//                                } catch (Exception e) {
//                                    Log.e("buffer", "Error opening Log.", e);
//                                }
//
//                            }
//                        }
//

                    }
                    else
                    {
                        foundAll = false;
                        foundExtra= false;
                        foundCounter = 0;
                        foundPeaks = null;
                        extraMidi = 0;
                        newExtraAlgo = false;
                    }
                    if(!foundAll)
                    {
                        foundPeaks = null;
                    }
                    if (!timeCounter.firstRun) {

                        callback.setPeaks(new Buffer(foundPeaks , foundAll, newExtraAlgo, (int) extraMidi ), timeElapsed / p2s, silence);
                        callback.run();
                    }


//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        TextView note = (TextView) findViewById(R.id.req_note);
//                        note.setText("notes req: " + PitchConverter.midiKeyToHertz((int) (expNotes[0][FREQ])));
//
//                    }
//                });
//                //Log.d("silence", "is playing: " + !silence);

                    bufferCounter++;

                    return true;
                }

            };

            return fftProcessor;
        }


    }

    private float getAverage(int location, float[] tempSoltuion, double fourierCoef) {

        double n = 12 * (Math.log((location * fourierCoef / 440)) / Math.log(2)) + 49;

        // find the next note in relation to the previous peak
        double nextNote = (Math.pow(2, (n - 46) / 12)) * 440;
        double prevNote = (Math.pow(2, (n - 52) / 12)) * 440;

        int prevI = (int) Math.round(prevNote / (fourierCoef));
        int nextI = (int) Math.round(nextNote / (fourierCoef));

        float tempAv = 0;
        float tempCount = 0;
        if (location > 10 && (location + 10) < tempSoltuion.length) {


//            average left of peak
            for (int i = prevI; i <= location; i++) {
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

}
