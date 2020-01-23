package com.nn.my2ncommunicator.main.callog.detail.lockevent;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.TextView;

import com.nn.my2ncommunicator.R;

import butterknife.BindView;
import butterknife.ButterKnife;

/**
 * Created by damir on 12.3.2018..
 */

public class LockEventHolder extends RecyclerView.ViewHolder {

    @BindView(R.id.lock_name)
    TextView mLockName;
    @BindView(R.id.unlock_time)
    TextView mUnlockTimeText;


    public LockEventHolder(View parent) {
        super(parent);
        ButterKnife.bind(this, parent);
    }
}
