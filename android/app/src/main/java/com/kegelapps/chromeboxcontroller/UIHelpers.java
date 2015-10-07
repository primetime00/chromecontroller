package com.kegelapps.chromeboxcontroller;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v4.app.Fragment;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.widget.EditText;

import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;

import java.io.FileOutputStream;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Created by Ryan on 9/6/2015.
 */
public class UIHelpers {

    public interface OnDeviceTextEntry {
        void onTextEntry(String name);
        void onCancelled();
    }

    public interface OnDeviceConnected {
        void onDeviceConnected();
        void onDeviceConnectFailed();
    }

    public interface OnFragmentCancelled {
        boolean cancel();
    }

    public interface OnChoiceSelected {
        void onChoiceSelected(String choice);
    }

    static void parentOpenFragment(Fragment frag, int where, Fragment current) throws Exception {
        while (current.getParentFragment() != null) {
            Fragment parent = current.getParentFragment();
            if (parent instanceof FragmentOpener) {
                ((FragmentOpener) parent).openFragment(frag, where, false);
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
            current = parent;
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

    static void showTextDialog(Context t, String title, String message, DialogInterface.OnDismissListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(t);
        if (listener != null)
            builder.setOnDismissListener(listener);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(true);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.show();
    }

    static void showDisconnectDialog(Context t, String message, DialogInterface.OnDismissListener listener) {
        if (message.length() > 0)
            showTextDialog(t, "Disconnected", message, listener);
        else
            showTextDialog(t, "Disconnected", "You were disconnected from the server.", listener);
    }

    static void choiceEntry(Context t, String title, final List<String> choices, final OnChoiceSelected listener ) {
        AlertDialog.Builder builder = new AlertDialog.Builder(t);
        builder.setTitle(title);
        builder.setItems(choices.toArray(new CharSequence[choices.size()]), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                listener.onChoiceSelected(choices.get(which));
                dialog.dismiss();
            }
        });
        builder.show();
    }

    public static void textEntry(Context c, String title, final OnDeviceTextEntry listener) {
        assert (listener != null);
        AlertDialog.Builder builder = new AlertDialog.Builder(c);
        builder.setTitle(title);
        final EditText input = new EditText(c);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);
        builder.setCancelable(false);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString().trim();
                if (name.length() > 0)
                    listener.onTextEntry(name);
                dialog.dismiss();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                listener.onCancelled();
            }
        });

        builder.show();
    }

    static public int convertIp(String ipStr) {
        int result = 0;

        // iterate over each octet
        for(String part : ipStr.split(Pattern.quote("."))) {
            // shift the previously parsed bits over by 1 byte
            result = result << 8;
            // set the low order bits to the current octet
            result |= Integer.parseInt(part);
        }
        return result;
    }
}
