package com.kegelapps.chromeboxcontroller;

import android.content.Context;
import android.util.Log;

import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Created by keg45397 on 10/8/2015.
 */
public class WakeRunnable implements Runnable {
    public static final int PORT = 9;
    DeviceInfoProto.DeviceInfo device;
    Context context;
    ControllerService.OnWake listener;
    boolean mActive = false;

    public WakeRunnable(Context context, ControllerService.OnWake listener) {
        this.context = context;
        this.listener = listener;
    }


    @Override
    public void run() {
        mActive = true;
        sendPacket(device.getIp(), device.getMac(), false);
        String ip = device.getIp();
        int end = ip.lastIndexOf(".");
        ip = ip.substring(0, end) + ".255";
        sendPacket(ip, device.getMac(), true);
        mActive = false;
    }

    private void sendPacket(String ip, String mac, boolean notify) {
        String ipStr = ip;
        String macStr = mac;

        try {
            byte[] macBytes = getMacBytes(macStr);
            byte[] bytes = new byte[6 + 16 * macBytes.length];
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) 0xff;
            }
            for (int i = 6; i < bytes.length; i += macBytes.length) {
                System.arraycopy(macBytes, 0, bytes, i, macBytes.length);
            }

            InetAddress address = InetAddress.getByName(ipStr);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, PORT);
            DatagramSocket socket = new DatagramSocket();
            socket.send(packet);
            socket.close();
            Log.d("WakeRunnable", "Wake-On-Lan Success");
            if (listener != null && notify)
                listener.onWakeSuccess();
        }
        catch (Exception e) {
            Log.d("WakeRunnable", "Failed to parse MAC");
            if (listener != null && notify)
                listener.onWakeFail();
        }
    }

    private static byte[] getMacBytes(String macStr) throws IllegalArgumentException {
        byte[] bytes = new byte[6];
        String[] hex = macStr.split("(\\:|\\-)");
        if (hex.length != 6) {
            throw new IllegalArgumentException("Invalid MAC address.");
        }
        try {
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) Integer.parseInt(hex[i], 16);
            }
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid hex digit in MAC address.");
        }
        return bytes;
    }

    public void setDevice(DeviceInfoProto.DeviceInfo device) {
        this.device = DeviceInfoProto.DeviceInfo.newBuilder(device).build();
    }

    public boolean isActive() {
        return mActive;
    }
}
