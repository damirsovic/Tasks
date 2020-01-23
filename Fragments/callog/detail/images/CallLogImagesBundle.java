package com.nn.my2ncommunicator.main.callog.detail.images;


import com.nn.my2ncommunicator.main.callog.CallLogBundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by damir on 13.2.2018..
 */

public class CallLogImagesBundle extends CallLogBundle {
    public int pos;
    public String imageName;

    public List<String> selectedImages;

    public CallLogImagesBundle(long callLogId, int pos, String imageName) {
        this(callLogId, pos, imageName, new ArrayList<>());
    }

    public CallLogImagesBundle(long callLogId, int pos, String imageName, List<String> selectedImages) {
        super(callLogId);
        this.pos = pos;
        this.imageName = imageName;
        this.selectedImages = selectedImages;
    }
}
