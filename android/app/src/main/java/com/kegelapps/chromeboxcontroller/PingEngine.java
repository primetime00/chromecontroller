package com.kegelapps.chromeboxcontroller;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

/**
 * Created by keg45397 on 10/1/2015.
 */
public class PingEngine {
    private int mPingId;
    private int mTimerTimeout; //this is how often the handler runs
    private int mPingTimeout; //this is how often to send a ping
    private int mDisconnectTimeout; //this is how long it takes to disconnect after no data recieved

    private long mNextPing; //this is the system time of the next ping

    private long mRunningTime;

    private Handler mMessenger; //this is the messenger attached to the looper
    private Runnable mOnTimeout; //this is the runnable that is run every mTimeTimeout ms

    public interface OnPingDisconnectTimer {
        void onDisconnectTimeout();
    }

    private OnPingDisconnectTimer mPingTimer;

    public PingEngine(int timeout, int ping, int disconnect, Handler messenger) {
        mDisconnectTimeout = disconnect;
        mTimerTimeout = timeout;
        mPingTimeout = ping;

        this.mMessenger = messenger;
        mOnTimeout = new Runnable() {
            @Override
            public void run() {
                long now = System.currentTimeMillis();
                Log.d("PingEngine", "passed = " + (now - mRunningTime) + " disconnect: " + mDisconnectTimeout);
                if (now - mRunningTime > mDisconnectTimeout) {
                    //we've not gotten data in awhile
                    if (mPingTimer != null) {
                        mPingTimer.onDisconnectTimeout();
                        return;
                    }
                }
                if (now >= mNextPing) {
                    //time to send a ping!
                    Log.d("PingEngine", "Sending a Ping!");
                    Message m = Message.obtain();
                    m.what = NetworkRunnable.NETWORK_THREAD_PING;
                    m.arg1 = mPingId++;
                    mMessenger.sendMessage(m);
                    mNextPing = System.currentTimeMillis()+mPingTimeout;
                }
                mMessenger.postDelayed(mOnTimeout, mTimerTimeout);
            }
        };
    }

    public void setOnDisconnectTimer(OnPingDisconnectTimer listener) {
        this.mPingTimer = listener;
    }

    public void resetTimer() {
        Log.d("PingEngine", "Got some data?");
        mRunningTime = System.currentTimeMillis();
    }

    public void start(long delay) {
        if (mMessenger != null) {
            this.mNextPing = 0;
            resetTimer();
            mMessenger.postDelayed(mOnTimeout, delay);
        }
    }
}
