package com.kegelapps.chromeboxcontroller;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.kegelapps.chromeboxcontroller.proto.ScriptCommandProto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Created by keg45397 on 10/5/2015.
 */
public class ResultListAdapter extends BaseAdapter {
    private List<String> mResults;
    private Context context;

    public ResultListAdapter(Context context) {
        this.mResults = new ArrayList<String>();
        this.context = context;
    }

    @Override
    public int getCount() {
        return mResults.size();
    }

    @Override
    public Object getItem(int position) {
        return mResults.get(position);
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
        String res = (String)getItem(position);
        ((TextView)v.findViewById(R.id.title)).setText(res);
        TextView t = (TextView)v.findViewById(R.id.description);
        t.setVisibility(View.GONE);
        v.findViewById(R.id.progress).setVisibility(View.GONE);
        return v;
    }

    public void addResults(ScriptCommandProto.ScriptInfo result) {
        String data = result.getReturnData();
        mResults.addAll(Arrays.asList(data.split("\n")));
        this.notifyDataSetChanged();
    }
}
