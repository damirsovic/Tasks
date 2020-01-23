package com.nn.my2ncommunicator.main.callog.detail.images;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import com.github.chrisbanes.photoview.PhotoView;
import com.nn.my2ncommunicator.App;
import com.nn.my2ncommunicator.R;
import com.nn.my2ncommunicator.core.dialog.TextDialogFragment;
import com.nn.my2ncommunicator.core.fragment.BaseViewModelFragment;
import com.nn.my2ncommunicator.core.widget.snack.SnackBarFactory;
import com.nn.my2ncommunicator.databinding.FragmentImagePreviewBinding;
import com.nn.my2ncommunicator.main.callog.CallLogBundle;
import com.nn.my2ncommunicator.main.callog.CallLogFragment;
import com.nn.my2ncommunicator.main.callog.CallLogGalleryPagerBundle;
import com.nn.my2ncommunicator.main.callog.detail.GalleryStatusStorage;
import com.nn.my2ncommunicator.main.fragment.INavigationDrawerMapped;
import com.nn.my2ncommunicator.util.answers.AnswersUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import eu.inloop.viewmodel.binding.ViewModelBindingConfig;
import me.relex.circleindicator.CircleIndicator;

/**
 * Created by damir on 12.2.2018..
 */

public class CallLogImagePreviewFragment extends BaseViewModelFragment<ICallLogImagesBindingView, CallLogImagesViewModel, FragmentImagePreviewBinding, CallLogImagesBundle> implements ICallLogImagesBindingView, INavigationDrawerMapped, ImageRowAdapter.OnItemClickListener, ImageFragmentPagerAdapter.IImageClickListener {
    private ImageFragmentPagerAdapter imageFragmentPagerAdapter;

    @BindView(R.id.pager_indicator_dots)
    CircleIndicator mIndicator;

    @BindView(R.id.pager_preview)
    ViewPager mViewPager;

    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    private ImageRowAdapter previewRowAdapter;

    @OnClick(R.id.pager_left_arrow)
    public void onLeftArrowClick(View view) {
        imageFragmentPagerAdapter.pos = mViewPager.getCurrentItem() - 1 >= 0 ? mViewPager.getCurrentItem() - 1 : imageFragmentPagerAdapter.getCount() - 1;
        resetImages();
        mViewPager.setCurrentItem(imageFragmentPagerAdapter.pos, true);
    }

    @OnClick(R.id.pager_right_arrow)
    public void onRightArrowClick(View view) {
        imageFragmentPagerAdapter.pos = (mViewPager.getCurrentItem() + 1) < imageFragmentPagerAdapter.getCount() ? mViewPager.getCurrentItem() + 1 : imageFragmentPagerAdapter.getCount();
        resetImages();
        mViewPager.setCurrentItem(imageFragmentPagerAdapter.pos, true);
    }

    private final ViewPager.SimpleOnPageChangeListener swipe = new ViewPager.SimpleOnPageChangeListener() {

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            getViewModel().showLeftIcon.set(position > 0);
            getViewModel().showRightIcon.set(position < imageFragmentPagerAdapter.getCount() - 1);
            imageFragmentPagerAdapter.pos = position;
        }
    };

    private void resetImages() {
        for (int i = 0; i < mViewPager.getChildCount(); i++) {
            PhotoView image = (PhotoView) ((RelativeLayout) mViewPager.getChildAt(i)).getChildAt(0);
            if (image != null && image.getScale() > 1.0f) {
                image.setScale(1.0f);
            }
        }
    }

    @Nullable
    @Override
    public Class<CallLogImagesViewModel> getViewModelClass() {
        return CallLogImagesViewModel.class;
    }

    @Override
    public ViewModelBindingConfig getViewModelBindingConfig() {
        return new ViewModelBindingConfig(R.layout.fragment_image_preview, App.getInstance().getActivity());
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        App.getInstance().getActivity().showActionBar(true);

        ButterKnife.bind(this, view);
        setModelView(this);
        setHasOptionsMenu(true);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        previewRowAdapter = new ImageRowAdapter(getData().callLogId, this, getData().pos, getData().selectedImages);
        recyclerView.setAdapter(previewRowAdapter);

        mViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                resetImages();
                CallLogImagesBundle bundle = getData();
                CallLogGalleryPagerBundle pBundle = new CallLogGalleryPagerBundle(bundle.callLogId, position);
                GalleryStatusStorage.instance().setCallLogGalleryPagerBundle(pBundle);
                recyclerView.scrollToPosition(position);
                previewRowAdapter.setCurrentItem(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
            }
        });

        updateCarousel();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.menu_call_log_detail, menu);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        List<String> images = previewRowAdapter.getSelectedImages();
        switch (item.getItemId()) {
            case R.id.action_clear:
                AnswersUtils.INSTANCE.getCallLogEvent().logDeleteFromGallery();
                showRemoveImagesDialog(images);
                break;
            case R.id.action_share:
                getViewModel().shareImage(previewRowAdapter.getCallLogId(), images);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onBackPressed() {
        if (previewRowAdapter.isInSelectMode()) {
            previewRowAdapter.exitSelectMode();
            return true;
        }

        CallLogFragment previousFragment = App.getInstance().getFragmentManager().changeFragment(CallLogFragment.class, CallLogFragment.class.getSimpleName(), new CallLogBundle(getData().callLogId), true);
        previousFragment.showDetailFragment(getData().callLogId);
        return true;
    }

    private void updateCarousel() {
        if (getData() != null) {
            imageFragmentPagerAdapter = new ImageFragmentPagerAdapter(getData().callLogId, R.layout.snapshot_fragment, getData().pos, false, true);
        } else {
            imageFragmentPagerAdapter = new ImageFragmentPagerAdapter(R.layout.snapshot_fragment, false, true);
        }
        imageFragmentPagerAdapter.setImageClickListener(this);
        mViewPager.setAdapter(imageFragmentPagerAdapter);
        mViewPager.setCurrentItem(getData().pos);
        getViewModel().hasSnapshot.set(imageFragmentPagerAdapter.getCount() > 0);
        getViewModel().hasMoreSnapshots.set(imageFragmentPagerAdapter.getCount() > 1);
        mViewPager.addOnPageChangeListener(swipe);
        mIndicator.setViewPager(mViewPager);
        imageFragmentPagerAdapter.registerDataSetObserver(mIndicator.getDataSetObserver());
    }

    @Override
    public int getMenuItemId() {
        return R.id.menu_call_log;
    }

    @Override
    public void onCallLogImagesDeleted(int count) {
        if (count > 1) {
            SnackBarFactory.createSnackBarOnBottom(getView(), R.string.call_log_delete_all_images_success, Snackbar.LENGTH_LONG).show();
        } else {
            SnackBarFactory.createSnackBarOnBottom(getView(), R.string.call_log_delete_image_success, Snackbar.LENGTH_LONG).show();
        }
        updateCarousel();

        if (!App.getInstance().getSnapshotService().hasAnyImage(getData().callLogId)) {
            onBackPressed();
        }
    }

    private void showRemoveImagesDialog(List<String> imagePaths) {
        TextDialogFragment question = new TextDialogFragment();
        if (imagePaths.size() > 1) {
            question.setText(App.getInstance().getApplicationContext().getString(R.string.call_log_delete_images_confirmation));
        } else {
            question.setText(App.getInstance().getApplicationContext().getString(R.string.call_log_delete_image_confirmation));
        }
        question.show(App.getInstance().getFragmentManager().getFragmentManager(), "QuestionDialog");
        question.setListener((status) ->
        {
            if (status == TextDialogFragment.PRESSED_OK) {
                removeImages(imagePaths);
            }
        });
    }

    private void removeImages(List<String> imagePaths) {
        getData().pos = mViewPager.getCurrentItem();
        List<String> allImages = Arrays.asList(App.getInstance().getSnapshotService().getImages(getData().callLogId));
        List<String> remainingImages = new ArrayList<>(allImages);
        getViewModel().deleteImages(imagePaths);
        for (String path: imagePaths) {
            int index = allImages.indexOf(path);
            if (index < getData().pos) {
                getData().pos -= 1;
            }
            remainingImages.remove(path);
        }
        if (getData().pos >= remainingImages.size()) {
            getData().pos = remainingImages.size() - 1;
        }
        imageFragmentPagerAdapter.images.clear();
        imageFragmentPagerAdapter.images.addAll(remainingImages);

        previewRowAdapter.onItemsDeleted(remainingImages);
        previewRowAdapter.setCurrentItem(getData().pos);

        getViewModel().showLeftIcon.set(getData().pos > 0);
        getViewModel().showRightIcon.set(getData().pos < imageFragmentPagerAdapter.getCount() - 1);
        getViewModel().hasSnapshot.set(imageFragmentPagerAdapter.getCount() > 0);
        getViewModel().hasMoreSnapshots.set(imageFragmentPagerAdapter.getCount() > 1);

        onCallLogImagesDeleted(imagePaths.size());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mViewPager.removeOnPageChangeListener(swipe);
        if (getView() != null) {
            ViewGroup parent = (ViewGroup) getView().getParent();
            if (parent != null) {
                parent.removeAllViews();
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        getData().selectedImages = previewRowAdapter.getSelectedImages();
        super.onSaveInstanceState(outState);
    }

    public void update(long callLogId, int pos) {
        imageFragmentPagerAdapter.updateData(callLogId, pos);
    }

    @Override
    public void onPreviewClick(int position) {
        imageFragmentPagerAdapter.pos = position;
        resetImages();
        mViewPager.setCurrentItem(position, true);
    }

    @Override
    public void onImageClick(int position, String filename) {}

    @Override
    public boolean onImageLongClick(int position, String filename) {
        previewRowAdapter.enterSelectMode(position);
        return true;
    }
}
