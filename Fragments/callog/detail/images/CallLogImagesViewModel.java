package com.nn.my2ncommunicator.main.callog.detail.images;

import android.databinding.ObservableBoolean;
import android.databinding.ObservableField;
import android.databinding.ObservableInt;

import com.nn.my2ncommunicator.App;
import com.nn.my2ncommunicator.core.fragment.BaseViewModel;
import com.nn.my2ncommunicator.main.callog.detail.MenuAction;
import com.nn.my2ncommunicator.main.services.calllog.ICallLogChangedListener;
import com.nn.my2ncommunicator.util.answers.AnswersUtils;

import java.util.List;


/**
 * Created by damir on 13.2.2018..
 */
public class CallLogImagesViewModel extends BaseViewModel<ICallLogImagesBindingView, CallLogImagesBundle> implements ICallLogChangedListener {
    public final ObservableBoolean hasSnapshot = new ObservableBoolean();

    public final ObservableBoolean hasMoreSnapshots = new ObservableBoolean();

    public final ObservableField<String> snapshotPath = new ObservableField<>();

    public final ObservableInt deviceAvatarResourceId = new ObservableInt();

    public final ObservableBoolean showLeftIcon = new ObservableBoolean();

    public final ObservableBoolean showRightIcon = new ObservableBoolean();

    public CallLogImagesViewModel() {
    }

    @Override
    public void init() {
        App.getInstance().getCallLogService().registerListener(this);
    }

    @Override
    public void update(CallLogImagesBundle data) {
        updateContent(data);
    }

    @Override
    public void leave() {
        App.getInstance().getCallLogService().unregisterListener(this);
    }

    private void updateContent(CallLogImagesBundle data) {
        hasSnapshot.set(data.imageName != null);
        snapshotPath.set(data.imageName);
    }

    @Override
    public void onCallLogChanged() {
    }

    public void shareImage(long callLogid, List<String> images) {
        MenuAction.INSTANCE.clearCache();
        AnswersUtils.INSTANCE.getCallLogEvent().logShareFromGallery();
        if (images.size()>1) {
            MenuAction.INSTANCE.shareImage(callLogid, images);
        } else {
            MenuAction.INSTANCE.shareImage(callLogid, images.get(0));
        }
    }

    public void deleteImages(List<String> imagePaths) {
        for (String path: imagePaths) {
            App.getInstance().getSnapshotService().deleteImage(path);
        }
    }

}
