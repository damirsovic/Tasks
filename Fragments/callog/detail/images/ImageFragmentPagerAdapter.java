package com.nn.my2ncommunicator.main.callog.detail.images;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.github.chrisbanes.photoview.PhotoView;
import com.nn.my2ncommunicator.App;
import com.nn.my2ncommunicator.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by damir on 12.2.2018..
 */

public class ImageFragmentPagerAdapter extends PagerAdapter {
    protected final List<String> images = new ArrayList<>();
    protected final int imageView = R.id.image_view;
    protected int imageContainer;
    protected long callLogId;
    protected int pos;
    protected boolean hasPreview;
    protected boolean zoomable;
    protected IImageClickListener listener;

    public ImageFragmentPagerAdapter(long callLogId, int imageContainer, int pos, boolean hasPreview, boolean zoomable) {
        this.imageContainer = imageContainer;
        String[] images = App.getInstance().getSnapshotService().getImages(callLogId);
        if (callLogId > 0 && images != null) {
            this.images.addAll(Arrays.asList(images));
        }
        this.callLogId = callLogId;
        this.pos = pos;
        this.hasPreview = hasPreview;
        this.zoomable = zoomable;
    }

    public ImageFragmentPagerAdapter(int imageContainer, boolean hasPreview, boolean zoomable) {
        this.imageContainer = imageContainer;
        this.images.clear();
        this.callLogId = 0;
        this.pos = 0;
        this.hasPreview = hasPreview;
        this.zoomable = zoomable;
    }

    @Override
    public int getCount() {
        return images.size();
    }

    @Override
    public int getItemPosition(@NonNull Object object) {
        return PagerAdapter.POSITION_NONE;
    }

    public void setPosition(int position) {
        this.pos = position;
        notifyDataSetChanged();
    }

    public int getPosition() {
        return this.pos;
    }

    public void updateData(long callLogId, int position) {
        this.images.clear();
        this.pos = 0;
        String[] storage = App.getInstance().getSnapshotService().getImages(callLogId);
        if (storage != null) {
            this.images.addAll(Arrays.asList(storage));
            this.pos = position;
        }
        this.callLogId = callLogId;
        notifyDataSetChanged();
    }

    public void remove(int pos) {
        images.remove(pos);
        notifyDataSetChanged();
    }

    public void remove() {
        images.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Object instantiateItem(@NonNull ViewGroup container, int position) {
        String imageFileName = images.get(position);
        LayoutInflater inflater = (LayoutInflater) App.getInstance().getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View swipeView = inflater.inflate(imageContainer, null);
        PhotoView photoView = swipeView.findViewById(imageView);
        if (zoomable)
            photoView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        else
            photoView.setScaleType(ImageView.ScaleType.FIT_XY);

        if (images.size() > 0) {
            View clickView = hasPreview ? swipeView : photoView;

            clickView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onImageClick(position, imageFileName);
                }
            });

            clickView.setOnLongClickListener(v -> {
                if (listener != null) {
                    return listener.onImageLongClick(position, imageFileName);
                } else {
                    return false;
                }
            });
        }

        Bitmap image = BitmapFactory.decodeFile(imageFileName);
        photoView.setImageBitmap(image);
        photoView.setZoomable(zoomable);

        container.addView(swipeView, 0);
        return swipeView;
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
        return view.equals(object);
    }

    @Override
    public void destroyItem(@NonNull ViewGroup collection, int position, @NonNull Object view) {
        collection.removeView((View) view);
    }

    public void setImageClickListener(IImageClickListener listener) {
        this.listener = listener;
    }

    public interface IImageClickListener {
        void onImageClick(int position, String filename);

        boolean onImageLongClick(int position, String filename);
    }

}