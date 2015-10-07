package com.kegelapps.chromeboxcontroller;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.kegelapps.chromeboxcontroller.proto.DeviceInfoProto;
import com.kegelapps.chromeboxcontroller.proto.ScriptCommandProto;

/**
 * Created by keg45397 on 10/5/2015.
 */
public class CommandListAdapter extends BaseAdapter {
    private ScriptCommandProto.ScriptInfoList mCommands;
    private Context context;

    public CommandListAdapter(Context context) {
        this.mCommands = ScriptCommandProto.ScriptInfoList.getDefaultInstance();
        this.context = context;
    }

    @Override
    public int getCount() {
        return mCommands.getScriptsCount();
    }

    @Override
    public Object getItem(int position) {
        return mCommands.getScripts(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            LayoutInflater inflater = (LayoutInflater)context.getSystemService (Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.cb_command_list_item, null);
        }
        ScriptCommandProto.ScriptInfo info = (ScriptCommandProto.ScriptInfo)getItem(position);
        ((TextView)v.findViewById(R.id.title)).setText(info.getName());
        TextView t = (TextView)v.findViewById(R.id.description);
        if (info.hasDescription()) {
            t.setVisibility(View.VISIBLE);
            t.setText(info.getDescription());
        }
        else
            t.setVisibility(View.GONE);
        v.findViewById(R.id.progress).setVisibility(View.GONE);
        return v;
    }

    public void addCommands(ScriptCommandProto.ScriptInfoList commands) {
        mCommands = ScriptCommandProto.ScriptInfoList.getDefaultInstance();
        for (ScriptCommandProto.ScriptInfo s : commands.getScriptsList()) {
            if (s.getVisible()) {
                mCommands = mCommands.toBuilder().addScripts(s).build();
            }
        }
        this.notifyDataSetChanged();
    }

    ScriptCommandProto.ScriptInfo findCommand(String name) {
        for (ScriptCommandProto.ScriptInfo s : mCommands.getScriptsList()) {
            if (s.getName().equals(name)) {
                return s;
            }
        }
        return null;
    }
}
