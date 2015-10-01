package com.kegelapps.chromeboxcontroller;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;

/**
 * Created by Ryan on 9/6/2015.
 */
public class UIHelpers {

    public interface OnDeviceNamedListener {
        void onDeviceNamed(String name);
    }

    public interface OnDeviceConnected {
        void onDeviceConnected();
        void onDeviceConnectFailed();
    }

    static void parentOpenFragment(Fragment frag, int where, Fragment current) throws Exception {
        while (current.getParentFragment() != null) {
            Fragment parent = current.getParentFragment();
            if (parent instanceof FragmentOpener) {
                ((FragmentOpener) parent).openFragment(frag, where);
                return;
            }
        }
        throw new Exception("Cannot find any instance of FragmentOpener");
    }

    static FragmentOpener findFragmentOpener(Fragment current) {
        while (current.getParentFragment() != null) {
            Fragment parent = current.getParentFragment();
            if (parent instanceof FragmentOpener) {
                return (FragmentOpener) parent;
            }
        }
        return null;
    }

    static BaseActivity getBaseActivity(Fragment current) {
        if (current == null)
            return null;
        if (current.getActivity() instanceof BaseActivity)
            return (BaseActivity)current.getActivity();
        return null;
    }

    static ControllerService getService(Fragment current)
    {
        BaseActivity b = getBaseActivity(current);
        if (b != null)
            return b.getService();
        return null;
    }

    static void showDisconnectDialog(Context t, String message, DialogInterface.OnDismissListener listener) {
        assert (listener != null);
        AlertDialog.Builder builder = new AlertDialog.Builder(t);
        builder.setOnDismissListener(listener);
        builder.setTitle("Disconnected");
        if (message.length() > 0)
            builder.setMessage(message);
        else
            builder.setMessage("You were disconnected from the server.");
        builder.setCancelable(true);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }
}
