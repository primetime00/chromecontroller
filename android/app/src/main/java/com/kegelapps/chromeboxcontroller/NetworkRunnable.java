package com.kegelapps.chromeboxcontroller;

import android.content.Context;

import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;
import com.kegelapps.chromeboxcontroller.proto.MessageProto;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import javax.jmdns.ServiceListener;

/**
 * Created by Ryan on 9/7/2015.
 */
public class NetworkRunnable implements Runnable {
    private String mIP;
    private int mPort;
    private Socket mSocket;
    private byte [] mRecvBuffer;
    private ByteBuffer mBuffer;
    int position = 0;

    private Context context;
    private ControllerService.OnConnection listener;

    public NetworkRunnable(Context context, ControllerService.OnConnection listener) {
        assert (context != null);
        assert (listener != null);
        this.context = context;
        this.listener = listener;
    }

    @Override
    public void run() {
        mRecvBuffer = new byte[50000];
        mBuffer = ByteBuffer.wrap(mRecvBuffer);
        try {
            mSocket = new Socket(mIP, mPort);
        } catch (IOException e) { //could not connect for some reason?
            e.printStackTrace();
            listener.onConnectionFailed();
        }

        //Start the receive Thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                int length = 0;
                while (true) {
                    try {
                        int read = mSocket.getInputStream().read(mRecvBuffer, position, mRecvBuffer.length - position);
                        if (position == 0 && read < 4)
                            continue;
                        length = mBuffer.getInt(0);
                        while (read >= length+4) {
                            ByteBuffer data = ByteBuffer.wrap(mRecvBuffer, 4, length);
                            MessageProto.Message msg = MessageProto.Message.parseFrom(data.array());
                            processMessage(msg);
                            mBuffer.position(length + 4);
                            mBuffer.compact();
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
        }).start();


    }

    private void processMessage(MessageProto.Message msg) {

    }

    public void setConnection(DeviceInfoProto.DeviceInfo dev) {
        mIP = dev.getIp();
        mPort = dev.getPort();
    }
}
