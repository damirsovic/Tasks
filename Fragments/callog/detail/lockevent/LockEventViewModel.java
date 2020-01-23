package com.nn.my2ncommunicator.main.callog.detail.lockevent;

import android.databinding.ObservableField;

import com.nn.my2ncommunicator.core.fragment.BaseViewModel;
import com.nn.my2ncommunicator.main.callog.CallLogBundle;
import com.nn.my2ncommunicator.main.callog.detail.ICallLogDetailBindingView;

/**
 * Created by damir on 12.3.2018..
 */

public class LockEventViewModel extends BaseViewModel<ICallLogDetailBindingView, CallLogBundle> {
    public final ObservableField<String> lockName = new ObservableField<>();
    public final ObservableField<String> unlockTime = new ObservableField<>();

    @Override
    public void init() {

    }

    @Override
    public void update(CallLogBundle data) {

    }

    @Override
    public void leave() {

    }
}
