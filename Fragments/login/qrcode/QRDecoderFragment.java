package com.nn.my2ncommunicator.main.login.qrcode;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.view.TextureView;
import android.view.View;
import android.widget.RelativeLayout;

import com.nn.my2ncommunicator.App;
import com.nn.my2ncommunicator.R;
import com.nn.my2ncommunicator.core.fragment.BaseViewModelFragment;
import com.nn.my2ncommunicator.core.widget.snack.SnackBarFactory;
import com.nn.my2ncommunicator.databinding.FragmentQrCodeBinding;
import com.nn.my2ncommunicator.main.login.LoginBundle;
import com.nn.my2ncommunicator.main.login.LoginFormState;
import com.nn.my2ncommunicator.main.login.LoginFragment;
import com.nn.my2ncommunicator.main.login.qrcode.decoding.QRCodeDecodedCredentials;

import butterknife.ButterKnife;
import butterknife.OnClick;
import eu.inloop.viewmodel.binding.ViewModelBindingConfig;

public class QRDecoderFragment extends BaseViewModelFragment<IQRCodeBindingView, QRCodeViewModel, FragmentQrCodeBinding, QRCodeBundle> implements IQRCodeBindingView, FragmentCompat.OnRequestPermissionsResultCallback, QRDecodeListener {
    private static final int CAMERA_PERMISSIONS_REQUEST_CODE = 123;
    private Snackbar mInvalidCodeSnackBar;
    private RelativeLayout layout;
    public AutoFitTextureView mTextureView;
    private QRCamera qrCamera;
    private QRCodeDecodedCredentials qrcode = null;
    private boolean recognized = false;

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            if (!recognized) {
                qrCamera.open(width, height);
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
            qrCamera.configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            mTextureView = null;
            if (!qrCamera.isClosing) {
                qrCamera.stop();
            }
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }
    };

    @Nullable
    @Override
    public Class<QRCodeViewModel> getViewModelClass() {
        return QRCodeViewModel.class;
    }

    @Override
    public ViewModelBindingConfig getViewModelBindingConfig() {
        return new ViewModelBindingConfig(R.layout.fragment_qr_code, requireContext());
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ButterKnife.bind(this, view);
        setModelView(this);

        layout = view.findViewById(R.id.decoder_output_layout);
    }

    @Override
    public void onResume() {
        super.onResume();

        App.getInstance().getActivity().showActionBar(false);
        App.getInstance().getActivity().showMenu(false);

        mTextureView = new AutoFitTextureView(requireContext());
        qrCamera = new QRCamera(mTextureView, this);

        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }

        // add view for preview (@INFO is here viz. onPause)
        if (layout.findViewWithTag(mTextureView.getTag()) == null) {
            mTextureView.setMinimumWidth(layout.getWidth());
            mTextureView.setMinimumHeight(layout.getHeight());
            layout.addView(mTextureView, 0);
        }
    }

    @Override
    public void onPause() {
        // remove view for preview (@INFO must be removed and then added, otherwise texture view is lagged - ui thread becomes unresponsive)
        layout.removeView(mTextureView);
        super.onPause();
    }

    @OnClick(R.id.cancel_reading_button)
    public void onCancelReadingClicked() {
        onBackClicked();
    }

    @Override
    public boolean onBackPressed() {
        onBackClicked();
        return true;
    }

    private void requestCameraPermission() {
        // Show an explanation to the user *asynchronously* -- don't block
        // this thread waiting for the user's response! After the user
        // sees the explanation, try again to request the permission.
        if (!ActivityCompat.shouldShowRequestPermissionRationale(App.getInstance().getActivity(), Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(App.getInstance().getActivity(), new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSIONS_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case CAMERA_PERMISSIONS_REQUEST_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
                } else {
                    SnackBarFactory.createSnackBarOnBottom(getView(), R.string.android_account_qr_scan_camera_denial, Snackbar.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    @Override
    public void onQrCodeRecognized(QRCodeDecodedCredentials qrcode) {
        this.qrcode = qrcode;
        if (qrcode != null) {
            if (!recognized) {
                recognized = true;
                App.getInstance().getFragmentManager()
                        .changeFragmentInMainThread(
                                LoginFragment.class,
                                LoginFragment.class.getSimpleName(),
                                new LoginBundle(this.qrcode, LoginFormState.STATE_OPENED),
                                true
                        ).subscribe();
            }
        }
    }

    @Override
    public void onQrCodeUnknown() {
        if (mInvalidCodeSnackBar == null || !mInvalidCodeSnackBar.isShown()) {
            mInvalidCodeSnackBar = SnackBarFactory.createSnackBarOnBottom(getView(), R.string.account_qr_scan_invalid_code, Snackbar.LENGTH_SHORT);
            mInvalidCodeSnackBar.show();
        }
    }

    @Override
    public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
        SnackBarFactory.createSnackBarOnBottom(getView(), R.string.android_account_qr_scan_cant_scan, Snackbar.LENGTH_SHORT).show();
    }

    public void onBackClicked() {
        App.getInstance().getFragmentManager().changeFragment(LoginFragment.class, LoginFragment.class.getSimpleName(), new LoginBundle(LoginFormState.STATE_OPENED), true);
    }

    @Override
    public void onInvalidQRCodeScanned() {

    }
}