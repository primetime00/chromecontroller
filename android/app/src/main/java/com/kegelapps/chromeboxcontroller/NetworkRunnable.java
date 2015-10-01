package com.kegelapps.chromeboxcontroller;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.google.protobuf.InvalidProtocolBufferException;
import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;
import com.kegelapps.chromeboxcontroller.proto.MessageProto;
import com.kegelapps.chromeboxcontroller.proto.PingProto;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Ryan on 9/7/2015.
 */
public class NetworkRunnable implements Runnable {
    final static String NETWORK_DATA_KEY = "DATA";
    final static int NETWORK_THREAD_SEND = 0;
    final static int NETWORK_THREAD_EXIT = 1;
    final static int NETWORK_THREAD_PING = 2;

    private String mIP;
    private int mPort;
    private Socket mSocket;
    private byte [] mRecvBuffer;
    private ByteBuffer mBuffer;
    int position = 0;

    private int mNetworkMessageId;

    private long mLastRecv = 0;
    private Runnable mTimerRunnable;
    private long mNextPing = 0;

    private Timer mPingTimer;
    private int mPingId;

    public Handler mNetworkHandler;

    private Context context;
    private ControllerService.OnConnection mListener;

    public NetworkRunnable(Context context, ControllerService.OnConnection listener) {
        assert (context != null);
        assert (listener != null);
        this.context = context;
        this.mListener = listener;

        mTimerRunnable = new Runnable() {

            @Override
            public void run() {
                onTimer();
            }
        };
    }

    @Override
    public void run() {
        mRecvBuffer = new byte[50000];
        mPingId = 0; //this is the ping packet number
        mNetworkMessageId = 0; //this is the packet number for all sent packets
        mNextPing = System.currentTimeMillis() + 2000; //ping in the next 2 seconds
        mBuffer = ByteBuffer.wrap(mRecvBuffer);
        Looper.prepare();
        try {
            mSocket = new Socket(mIP, mPort);
        } catch (IOException e) { //could not connect for some reason?
            e.printStackTrace();
            mListener.onConnectionFailed();
            return;
        }

        createPingTimer();

        //Start the receive Thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                recvFunction();
                Log.d("NetworkRunnable", "Receive thread is shutting down.");
            }
        }).start();


        //create the loop handler
        mNetworkHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == NETWORK_THREAD_EXIT) {
                    Looper.myLooper().quit();
                    return;
                }
                handleNetworkMessage(msg);
            }
        };

        mNetworkHandler.postDelayed(mTimerRunnable, 1000);

        //enter main loop
        Looper.loop();

        if (mPingTimer != null)
            mPingTimer.cancel();

        try {
            mSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("NetworkRunnable", "Looper thread is shutting down.");
    }

    private void createPingTimer() {
        mPingTimer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Message m = Message.obtain();
                m.what = NETWORK_THREAD_PING;
                mNetworkHandler.sendMessage(m);
            }
        };
        //mPingTimer.schedule(task, 2000, 4000);
    }

    private void handleNetworkMessage(Message msg) {
        MessageProto.Message data;
        switch (msg.what) {
            case NETWORK_THREAD_SEND: //sending a message
                try {
                    data = MessageProto.Message.parseFrom(msg.getData().getByteArray(NETWORK_DATA_KEY));
                    sendMessagePacket(data);
                } catch (InvalidProtocolBufferException e) {
                    Log.e("NetworkRunnable", "Could not send packet, because I couldn't parse it.");
                    e.printStackTrace();
                }
                break;
            case NETWORK_THREAD_PING: //sending a ping message
                data = createPingMessage();
                Log.d("NetworkRunnable", "Sending a ping message");
                sendMessagePacket(data);
                break;
            default:
                break;
        }
    }

    private void sendMessagePacket(MessageProto.Message msg) {
        assert (msg != null);
        ByteBuffer data = ByteBuffer.allocate(msg.getSerializedSize()+4);
        data.order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(msg.getSerializedSize());
        data.put(msg.toByteArray());
        try {
            mSocket.getOutputStream().write(data.array());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private MessageProto.Message createPingMessage() {
        PingProto.Ping p = PingProto.Ping.newBuilder().setId(mPingId++).build();
        MessageProto.Message m = MessageProto.Message.newBuilder().setPing(p).setId(getNetworkId()).build();
        return m;
    }

    private synchronized long getNetworkId() {
        return mNetworkMessageId++;
    }

    private void processMessage(MessageProto.Message msg) {
        if (msg.hasCommandList()) {
            Log.d("NetworkRunnable", "I received a list of commands with " + msg.getCommandList().getScriptsCount() + " commands");
        }
    }

    public void setConnection(DeviceInfoProto.DeviceInfo dev) {
        mIP = dev.getIp();
        mPort = dev.getPort();
    }

    public void recvFunction() {
        int length = 0;
        mBuffer.order(ByteOrder.LITTLE_ENDIAN);
        while (true) {
            try {
                int read = mSocket.getInputStream().read(mRecvBuffer, position, mRecvBuffer.length - position);
                if (read == -1) //we've been disconnected?
                {
                    closeNetwork();
                    mListener.onDisconnected("You have been disconnected from the server.");
                    return;
                }
                if (read > 0)
                    mLastRecv = System.currentTimeMillis();
                if (position == 0 && read < 4)
                    continue;
                length = mBuffer.getInt(0);
                while (read >= length+4) {
                    ByteBuffer data = extractMessageData(mRecvBuffer, length);
                    MessageProto.Message msg = MessageProto.Message.parseFrom(data.array());
                    processMessage(msg);
                    read -= (length+4);
                    position = read;
                    if (read < 4)
                        break;
                    length = mBuffer.getInt(0);
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    private void closeNetwork() {
        Message m = Message.obtain();
        m.what = NETWORK_THREAD_EXIT;
        mNetworkHandler.sendMessage(m);
    }

    private ByteBuffer extractMessageData(byte[] mRecvBuffer, int length) {
        ByteBuffer b = ByteBuffer.allocate(length);
        b.put(mRecvBuffer, 4, length);
        mBuffer.position(length + 4);
        mBuffer.compact();
        return b;
    }

    public Handler getHandler() {
        return mNetworkHandler;
    }

    private void onTimer()
    {
        long now = System.currentTimeMillis();
        if (now - mLastRecv > 5000) //we haven't had data for 5 seconds
        {
            Log.d("NetworkRunnable", "I'm had nothing for 5 seconds, timing out!");
            closeNetwork();
            mListener.onDisconnected("I am no longer receiving data from the server.");
            return;
        }
        if (now >= mNextPing) {
            mNextPing = now+4000;
            Message m = Message.obtain();
            m.what = NETWORK_THREAD_PING;
            mNetworkHandler.sendMessage(m);
        }
        mNetworkHandler.postDelayed(mTimerRunnable, 1000);
    }
}
