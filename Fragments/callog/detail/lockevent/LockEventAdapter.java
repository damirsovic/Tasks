package com.nn.my2ncommunicator.main.callog.detail.lockevent;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.nn.my2ncommunicator.R;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by damir on 14.3.2018..
 */

public class LockEventAdapter extends RecyclerView.Adapter<LockEventHolder> {
    private final List<LockEventWrapper> mItems;

    public LockEventAdapter() {
        mItems = new ArrayList<>();
    }

    public void setLockEvents(List<LockEventWrapper> entries) {
        mItems.clear();
        mItems.addAll(entries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LockEventHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_lock_event_item, parent, false);
        return new LockEventHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LockEventHolder holder, int position) {
        LockEventWrapper lockEvent = mItems.get(position);
        holder.mLockName.setText(lockEvent.getDtmfName());
        Date date = new Date(lockEvent.getUnlockTime());
        DateFormat formatter = new SimpleDateFormat("HH:mm:ss");
        holder.mUnlockTimeText.setText(formatter.format(date));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }
}
