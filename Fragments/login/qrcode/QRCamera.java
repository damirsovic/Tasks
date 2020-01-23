package com.nn.my2ncommunicator.main.login.qrcode;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;
import com.nn.my2ncommunicator.App;
import com.nn.my2ncommunicator.main.login.qrcode.decoding.QRCodeDecodedCredentials;
import com.nn.my2ncommunicator.main.login.qrcode.decoding.QRCodeStringDecoder;
import com.nn.my2ncommunicator.main.login.qrcode.decoding.QRCodeVersionEnum;
import com.nn.my2ncommunicator.util.answers.AnswersUtils;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import quanti.com.kotlinlog.Log;

import static android.content.Context.CAMERA_SERVICE;
import static android.hardware.camera2.CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES;
import static android.hardware.camera2.CameraCharacteristics.LENS_FACING;
import static android.hardware.camera2.CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP;
import static android.hardware.camera2.CameraMetadata.CONTROL_AE_MODE_ON;
import static android.hardware.camera2.CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE;
import static android.hardware.camera2.CameraMetadata.CONTROL_MODE_AUTO;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;
import static android.hardware.camera2.CaptureRequest.CONTROL_AE_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE;
import static android.hardware.camera2.CaptureRequest.CONTROL_AF_MODE;
import static android.hardware.camera2.CaptureRequest.CONTROL_MODE;

public class QRCamera {

    private static final int YUV420_IMAGE_FORMAT = ImageFormat.YUV_420_888;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private final Semaphore openLock = new Semaphore(1);
    private FirebaseVisionBarcodeDetector detector;
    private String mCameraId;
    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;
    private Handler mBackgroundHandler;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private HandlerThread mBackgroundThread;
    private QRDecodeListener mListener;
    private AutoFitTextureView mTextureView;
    private CameraManager cameraManager;
    public boolean isClosing = false;
    private Range<Integer> fpsRange = null;
    private Disposable readerDisposable = null;
    private CameraCaptureSession.StateCallback ccsStateCallBack = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NotNull CameraCaptureSession cameraCaptureSession) {
            // The camera is already closed
            if (mCameraDevice == null) {
                Log.i("Camera State onConfigured but mCameraDevice is null");
                return;
            } else {
                Log.i("Camera State onConfigured");
            }

            // When the session is ready, we start displaying the preview.
            mCaptureSession = cameraCaptureSession;
            try {
                // Auto focus should be continuous for camera preview.
//                        https://developer.android.com/reference/android/hardware/camera2/CaptureRequest.html#CONTROL_AF_MODE
                mPreviewRequestBuilder.set(CONTROL_MODE, CONTROL_MODE_AUTO);
                mPreviewRequestBuilder.set(CONTROL_AE_MODE, CONTROL_AE_MODE_ON);
                mPreviewRequestBuilder.set(CONTROL_AF_MODE, CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                mPreviewRequestBuilder.set(CONTROL_AE_TARGET_FPS_RANGE, fpsRange);

                // Finally, we start displaying the camera preview.
                CaptureRequest mPreviewRequest = mPreviewRequestBuilder.build();
                mCaptureSession.setRepeatingRequest(mPreviewRequest, null, mBackgroundHandler);
            } catch (CameraAccessException | IllegalStateException e) {
                Log.e(e, "An error occurred during camera configuration");
            }
        }

        @Override
        public void onConfigureFailed(@NotNull CameraCaptureSession cameraCaptureSession) {
            mListener.onConfigureFailed(cameraCaptureSession);
        }
    };
    private final CameraDevice.StateCallback cdStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            openLock.release();
            mCameraDevice = cameraDevice;
            createPreviewSession();
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            openLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(CameraDevice cameraDevice, int error) {
            openLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }
    };
    private OnSuccessListener<List<FirebaseVisionBarcode>> success = new OnSuccessListener<List<FirebaseVisionBarcode>>() {
        @Override
        public void onSuccess(List<FirebaseVisionBarcode> barcodes) {
            // Task completed successfully
            // [START_EXCLUDE]
            // [START get_barcodes]
            for (FirebaseVisionBarcode barcode : barcodes) {
                String rawValue = barcode.getRawValue();

                if (rawValue != null) {
                    int valueType = barcode.getValueType();
                    // See API reference for complete list of supported types
                    switch (valueType) {
                        case 7:
                            if (qrCodeDecode(rawValue))
                                return;
                    }
                }
            }
            // [END get_barcodes]
            // [END_EXCLUDE]
        }
    };
    private OnFailureListener failure = new OnFailureListener() {
        @Override
        public void onFailure(@NonNull Exception e) {
            // Task failed with an exception
            e.printStackTrace();
        }
    };

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                if (!isClosing && readerDisposable == null) {
                    final int rotation = (mCameraDevice == null) ? 0 : getRotationCompensation(mCameraDevice.getId());
                    if (mTextureView != null)
                        configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
                    readerDisposable = Observable.fromCallable(() -> FirebaseVisionImage.fromMediaImage(image, rotation))
                            .subscribeOn(Schedulers.computation())
                            .observeOn(Schedulers.computation())
                            .doOnNext(firebaseVisionImage -> {
                                scanBarcodes(firebaseVisionImage);
                            })
                            .subscribe(ignored -> {
                                image.close();
                                readerDisposable = null;
                            }, error -> {
                                Log.e("Error in QR decoder", error);
                            });
                } else {
                    image.close();
                }
            }
        }
    };

    public QRCamera(AutoFitTextureView textureView, QRDecodeListener listener) {
        this.mTextureView = textureView;
        this.mListener = listener;
        cameraManager = (CameraManager) mTextureView.getContext().getSystemService(CAMERA_SERVICE);
    }

    private static Size chooseOptimalSize(List<Size> choices, int width, int height) {

        // Collect the supported resolutions that are at least as big as the preffered dimension
        List<Size> goodRes = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preffered dimension
        for (Size option : choices) {
            if (option.getWidth() <= width && option.getHeight() <= height) {
                // add as good resolution
                goodRes.add(option);
            }
        }

        if (goodRes.size() > 0) {
            // Pick the largest of those big enough.
            return Collections.max(goodRes, new CompareSizesByArea());
        }

        // Camera does not support minimal dimensions ????
        // Get the smallest dimension
        return Collections.min(choices, new CompareSizesByArea());
    }

    /**
     * Get the angle by which an image must be rotated given the device's current
     * orientation.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private int getRotationCompensation(String cameraId) {
        try {
            // Get the device's current rotation relative to its "native" orientation.
            // Then, from the ORIENTATIONS table, look up the angle the image must be
            // rotated to compensate for the device's rotation.
            Activity activity = App.getInstance().getActivity();
            if (null == activity) {
                return 0;
            }
            int deviceRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            int rotationCompensation = ORIENTATIONS.get(deviceRotation);

            // On most devices, the sensor orientation is 90 degrees, but for some
            // devices it is 270 degrees. For devices with a sensor orientation of
            // 270, rotate the image an additional 180 ((270 + 270) % 360) degrees.

            int sensorOrientation = cameraManager
                    .getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SENSOR_ORIENTATION);
            rotationCompensation = (rotationCompensation + sensorOrientation + 270) % 360;

            // Return the corresponding FirebaseVisionImageMetadata rotation value.
            int result;
            switch (rotationCompensation) {
                case 0:
                    result = FirebaseVisionImageMetadata.ROTATION_0;
                    break;
                case 90:
                    result = FirebaseVisionImageMetadata.ROTATION_90;
                    break;
                case 180:
                    result = FirebaseVisionImageMetadata.ROTATION_180;
                    break;
                case 270:
                    result = FirebaseVisionImageMetadata.ROTATION_270;
                    break;
                default:
                    result = FirebaseVisionImageMetadata.ROTATION_0;
                    Log.e("QRDecoder", "Bad rotation value: " + rotationCompensation);
            }
            return result;
        } catch (CameraAccessException e) {
            Log.e("error getting rotation", e);
            return 0;
        }
    }

    private void createPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            Surface surface = new Surface(texture);
            Surface mImageSurface = mImageReader.getSurface();
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(mImageSurface);
            mPreviewRequestBuilder.addTarget(surface);

            // Create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(mImageSurface, surface), ccsStateCallBack, null);
        } catch (CameraAccessException | IllegalStateException e) {
            Log.e("Error creating preview session", e);
        }
    }

    private String getCameraId() {
        String resultingCameraId = null;
        try {
            String[] cameraIdList = cameraManager.getCameraIdList();
            for (String cameraId : cameraIdList) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (characteristics.get(LENS_FACING) == LENS_FACING_FRONT) {
                    continue;
                }
                resultingCameraId = cameraId;
            }
        } catch (CameraAccessException e) {
            Log.e("", e);
        }
        return resultingCameraId;
    }

    @SuppressLint("MissingPermission")
    public void open(int width, int height) {
        try {
            if (!openLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            startBackgroundThread();

            createDetector();
            setUpCameraOutputs(width, height);
            configureTransform(width, height);
            cameraManager.openCamera(mCameraId, cdStateCallback, mBackgroundHandler);
        } catch (Exception e) {
            Log.e("", e);
        }
    }

    private void setUpCameraOutputs(int width, int height) {
        try {
            String cameraId = getCameraId();
            if (cameraId != null) {
                this.mCameraId = cameraId;
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);

                StreamConfigurationMap map = characteristics.get(SCALER_STREAM_CONFIGURATION_MAP);

                // For still image captures, we use the largest available size.
                List<Size> outputSizes = Arrays.asList(map.getOutputSizes(YUV420_IMAGE_FORMAT));

                // determine correct picture and preview size by TextureView
                Size captureImageSize = chooseOptimalSize(outputSizes, width, height);

                mImageReader = ImageReader.newInstance(captureImageSize.getWidth(), captureImageSize.getHeight(), YUV420_IMAGE_FORMAT, 10);
                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
                mPreviewSize = captureImageSize;

                // determine FPS
                try {
                    Range<Integer>[] ranges = characteristics.get(CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                    if (ranges != null) {
                        for (Range<Integer> range : ranges) {
                            int upper = range.getUpper();
                            int lower = range.getLower();
                            if (fpsRange == null || lower <= 15 && upper > fpsRange.getUpper()) {
                                fpsRange = range;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Log.i("QRDecoder", "[FPS Range] is:" + fpsRange);
            }
        } catch (CameraAccessException e) {
            Log.e("", e);
        }
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    public void configureTransform(int viewWidth, int viewHeight) {
        Activity activity = App.getInstance().getActivity();
        if (null == mTextureView || null == mPreviewSize || null == activity) {
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        Maybe.fromAction(() -> mTextureView.setTransform(matrix))
                .subscribeOn(AndroidSchedulers.mainThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        success -> {
                        },
                        error -> Log.e("QRCamera", error)
                );

    }

    public void stop() {
        readerDisposable = null;
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        mImageReader.setOnImageAvailableListener(null, null);

    }

    public void close() {
        if (!isClosing) {
            isClosing = true;
            try {
                openLock.acquire();
                stop();

                if (mImageReader != null) {
                    mImageReader = null;
                }
                stopBackgroundThread();
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
            } catch (Exception e) {
                Log.d("QRDecodeFragment", e.getMessage());
            } finally {
                openLock.release();
            }
        }
    }

    private void createDetector() {
        FirebaseVisionBarcodeDetectorOptions options =
                new FirebaseVisionBarcodeDetectorOptions.Builder()
                        .setBarcodeFormats(
                                FirebaseVisionBarcode.FORMAT_QR_CODE)
                        .build();

        detector = FirebaseVision.getInstance()
                .getVisionBarcodeDetector(options);

    }

    private void scanBarcodes(FirebaseVisionImage image) {
        detector.detectInImage(image)
                .addOnSuccessListener(success)
                .addOnFailureListener(failure);
    }

    private boolean qrCodeDecode(String textToDecode) {
        QRCodeVersionEnum whichVersionDecodes = QRCodeStringDecoder.whichVersionDecodes(textToDecode);
        if (whichVersionDecodes == QRCodeVersionEnum.ERROR) {
            mListener.onQrCodeUnknown();
        } else {
            AnswersUtils.INSTANCE.getLoginAnswersEvent().logQrScannerSuccess();
            QRCodeDecodedCredentials decodedCredentials = QRCodeStringDecoder.decodeUsingVersion(textToDecode, whichVersionDecodes);
            mListener.onQrCodeRecognized(decodedCredentials);
            close();
            return true;
        }
        return false;
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("BGCamera" + mCameraId);
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        try {
            mBackgroundThread.quitSafely();
            mBackgroundThread.join();
        } catch (InterruptedException | NullPointerException e) {
            Log.e("QRDecodeFragment", e.getMessage());
        } finally {
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }
    }

    private static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}
