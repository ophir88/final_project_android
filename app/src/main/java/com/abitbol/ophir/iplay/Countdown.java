package com.abitbol.ophir.iplay;

import android.annotation.TargetApi;
import android.app.Activity;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.CountDownTimer;
import android.util.Log;

import java.util.concurrent.TimeUnit;

/**
 * Created by Negan on 4/19/2015.
 */
public class Countdown extends CountDownTimer {
    Activity callerAct;
    Thread listenThread;
    private SoundPool tickSound;
    private int tickId;


    public Countdown(long BPM, Thread listenThread, Activity act) {
        super(60000*6/BPM, 60000/BPM );
        Log.d("COUNTING", "called constructor! tick every: "  +(double) BPM );
        callerAct = act;
        getSound();
        this.listenThread = listenThread;

    }


    @Override
    public void onTick(long millisUntilFinished) {
        tickSound.play(tickId, 1, 1, 1, 0, 1);
        Log.d("COUNTING", "count! left: "+millisUntilFinished);

    }

    @Override
    public void onFinish() {
        listenThread.start();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void getSound() {
        tickSound = new SoundPool(1, AudioManager.STREAM_MUSIC, 1);
        tickId = tickSound.load(callerAct, R.raw.tick, 1);

    }
}
