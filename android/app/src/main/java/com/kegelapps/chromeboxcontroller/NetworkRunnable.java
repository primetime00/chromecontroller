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
import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
    private byte[] mRecvBuffer;
    private ByteBuffer mBuffer;
    private boolean mActive;

    int position = 0;

    private int mNetworkMessageId;

    private PingEngine mPingEngine;

    public MessageHandler mNetworkHandler;

    private Context context;
    private ControllerService.OnConnection mListener;

    static class MessageHandler extends Handler {
        private final WeakReference<NetworkRunnable> mNetwork;

        public MessageHandler(NetworkRunnable network) {
            this.mNetwork = new WeakReference<NetworkRunnable>(network);
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == NETWORK_THREAD_EXIT) {
                Looper.myLooper().quit();
                return;
            }
            NetworkRunnable net = mNetwork.get();
            if (net != null)
                net.handleNetworkMessage(msg);
        }
    }

    public NetworkRunnable(Context context, ControllerService.OnConnection listener) {
        assert (context != null);
        assert (listener != null);
        this.context = context;
        this.mListener = listener;
        mActive = false;
    }

    @Override
    public void run() {
        mActive = true;
        mRecvBuffer = new byte[50000];
        mNetworkMessageId = 0; //this is the packet number for all sent packets
        mBuffer = ByteBuffer.wrap(mRecvBuffer);
        Looper.prepare();

        try {
            mSocket = new Socket();
            mSocket.connect(new InetSocketAddress(mIP, mPort), 3000);
        } catch (IOException e) { //could not connect for some reason?
            e.printStackTrace();
            mListener.onConnectionFailed();
            mActive = false;
            return;
        }

        //Start the receive Thread
        startReceiveThread();


        //create the loop handler
        mNetworkHandler = new MessageHandler(this);

        mPingEngine = new PingEngine(1000, 4000, 5000, mNetworkHandler);
        mPingEngine.setOnDisconnectTimer(new PingEngine.OnPingDisconnectTimer() {
            @Override
            public void onDisconnectTimeout() {
                Log.d("NetworkRunnable", "I'm had nothing for 5 seconds, timing out!");
                closeNetwork();
                mListener.onDisconnected("I am no longer receiving data from the server.");
            }
        });
        mPingEngine.start(1000);

        mListener.onConnectionSuccess();


        //enter main loop
        Looper.loop();

        try {
            mSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d("NetworkRunnable", "Looper thread is shutting down.");
        mListener.onDisconnectComplete();
        mActive = false;
    }

    private void startReceiveThread() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                recvFunction();
                Log.d("NetworkRunnable", "Receive thread is shutting down.");
            }
        }).start();
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
                data = createPingMessage(msg.arg1);
                sendMessagePacket(data);
                break;
            default:
                break;
        }
    }

    private void sendMessagePacket(MessageProto.Message msg) {
        assert (msg != null);
        msg = msg.toBuilder().setId(getNetworkId()).build();
        ByteBuffer data = ByteBuffer.allocate(msg.getSerializedSize() + 4);
        data.order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(msg.getSerializedSize());
        data.put(msg.toByteArray());
        try {
            mSocket.getOutputStream().write(data.array());
        } catch (IOException e) {
            Log.d("PingEngine", "Write data exception!\n" + e.getMessage());
            e.printStackTrace();
        }
    }

    private MessageProto.Message createPingMessage(int id) {
        PingProto.Ping p = PingProto.Ping.newBuilder().setId(id).build();
        MessageProto.Message m = MessageProto.Message.newBuilder().setPing(p).build();
        return m;
    }

    private synchronized long getNetworkId() {
        return mNetworkMessageId++;
    }

    private void processMessage(MessageProto.Message msg) {
        mListener.onReceivedMessage(msg);
    }

    public void setConnection(DeviceInfoProto.DeviceInfo dev) {
        mIP = dev.getIp();
        if (!dev.hasPort())
            mPort = ControllerService.SERVICE_PORT;
        else
            mPort = dev.getPort();
    }

    public void recvFunction() {
        int length = 0;
        int read = 0;
        mBuffer.order(ByteOrder.LITTLE_ENDIAN);
        while (true) {
            try {
                read += mSocket.getInputStream().read(mRecvBuffer, position, mRecvBuffer.length - position);
                if (read == -1) //we've been disconnected?
                {
                    closeNetwork();
                    mListener.onDisconnected("You have been disconnected from the server.");
                    return;
                }
                if (read > 0)
                    mPingEngine.resetTimer();

                if (position == 0 && read < 4)
                    continue;
                if (length == 0)
                    length = mBuffer.getInt(0);
                Log.d("NetworkRunnable", "Length is " + length + " Read is " + read);
                position = read;
                while (read >= length + 4) {
                    ByteBuffer data = extractMessageData(mRecvBuffer, length);
                    MessageProto.Message msg = MessageProto.Message.parseFrom(data.array());
                    processMessage(msg);
                    read -= (length + 4);
                    position = read;
                    if (read < 4) {
                        length = 0;
                        break;
                    }
                    length = mBuffer.getInt(0);
                }
            } catch (IOException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    public void closeNetwork() {
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

    public boolean isActive() { return  mActive;}
}
