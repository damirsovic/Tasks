package com.nn.my2ncommunicator.main.services.snapshot;

import android.content.ContentValues;
import android.media.MediaScannerConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;

import com.nn.my2ncommunicator.App;
import com.nn.my2ncommunicator.main.linphone.LinphoneCallManager;
import com.nn.my2ncommunicator.main.preference.Settings;
import com.nn.my2ncommunicator.model.calllog.CallLogEntry;
import com.nn.my2ncommunicator.model.contact.Contact;
import com.nn.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import quanti.com.kotlinlog.Log;

public class TakeSnapshotService {
    private final int M_INTERVAL = 3000; // take photo every 3 sec
    private final int MAX_NUM_PICTURES = 10;  // maximum number of pictures per session
    private final long MIN_FILE_SIZE = 5120L; // Minimum size of file considered valid
    private Handler mHandler;
    private HandlerThread ht;
    private final List<String> autoFiles = new ArrayList<>();
    private final List<String> manualFiles = new ArrayList<>();
    private final List<String> files = new ArrayList<>();
    private boolean running = false;
    private CallLogEntry entry;
    private Contact contact;
    private final List<ISnapshotServiceListener> mListeners = new ArrayList<>();
    private final Runnable mAutoShooting = new Runnable() {

        @Override
        public synchronized void run() {
            Log.d("Shooting Image AUTO");

            try {
                String filename = Settings.getSnapshotCallLogFilename(entry);
                takeSnapshot(filename, SnapshotType.AUTO);
                autoFiles.add(filename);
            } catch (Exception ex) {
                ex.printStackTrace();
            } finally {
                mHandler.postDelayed(mAutoShooting, M_INTERVAL);      // wait M_INTERVAL and restart
            }
        }
    };

    public boolean registerListener(ISnapshotServiceListener listener) {
        if (mListeners.contains(listener)) {
            return false;
        }
        mListeners.add(listener);
        return true;
    }

    public boolean unregisterListener(ISnapshotServiceListener listener) {
        if (!mListeners.contains(listener)) {
            return false;
        }
        mListeners.remove(listener);
        if (running)
            stopImageShooting();
        return true;
    }

    public synchronized void takeSnapshot(String filename, SnapshotType type) {
        App.getInstance().getCallManager().takeSnapshot(filename, false)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeOn(Schedulers.single())
                .subscribe(() ->
                {
                    switch (type) {
                        case AUTO:
                            Log.d("AutoSnapshot copied");
                            break;
                        case MANUAL:
                            Log.d("Manual Snapshot copied");
                            for (ISnapshotServiceListener l : mListeners) {
                                l.onManualSnapshotTaken(entry.getId());
                            }
                            break;
                    }
                }, error -> Log.e("Could not take snapshot", error));
    }


    public synchronized void startAutoShooting() {
        if (running) return;
        entry = App.getInstance().getCallLogService().getCurrentCallLogEntry();
        contact = App.getInstance().getContactService().getCurrentCallContact();
        if (autoFiles.size() > 0 || manualFiles.size() > 0 || files.size() > 0) {
            autoFiles.clear();
            manualFiles.clear();
            files.clear();
            //Remove manual snapshots
            App.getInstance().getSnapshotService().purgeFiles(App.getInstance().getSnapshotService().findGalleryImages(entry));

            //Remove call log snapshots
            App.getInstance().getSnapshotService().deleteCallLog(entry);
        }
        Contact contact = App.getInstance().getContactService().getCurrentCallContact();
        LinphoneCallManager callManager = App.getInstance().getCallManager();
        if (contact != null && callManager != null) {
            if (contact.isIpIntercom() && callManager.isVideoStreamEnabled()) {
                running = true;
                ht = new HandlerThread("Snapshots");
                ht.start();

                mHandler = new Handler(ht.getLooper());
                mHandler.post(mAutoShooting);
            }
        }
    }

    private void distributeImages() {
        files.clear();
        processManualImages();
    }

    public void copyImageToGallery(String filename) {
        //Save a copy of snapshot to Gallery
        String[] parsedName = filename.split("/");
        String backupName = parsedName[parsedName.length - 1];
        String copyFilename = Settings.getSnapshotGalleryFilename(entry, contact, backupName);
        FileUtils.copyFile(filename, copyFilename);

    }

    private void processManualImages() {
        Observable.fromIterable(manualFiles)
                .subscribe(
                        filename -> {
                            File f = new File(filename);
                            if (f.length() < MIN_FILE_SIZE)     // manual snapshot is smaller than expected,
                            {
                                f.delete();                     // remove it from file system
                            } else {
                                files.add(filename);             // save manual snapshot in final list
                                copyImageToGallery(filename);
                            }
                        },
                        e -> Log.e(e.getMessage()),
                        () -> {
                            updateGallery(manualFiles);
                            processAutoImages();
                        }
                );

    }

    private void processAutoImages() {
        List<String> tmpList = new ArrayList<>(autoFiles);
        Observable<String> autoShots = Observable
                .fromIterable(tmpList);

        if (files.size() >= MAX_NUM_PICTURES) {    // There is enough images, remove autoshot images
            autoShots.subscribe(
                    filename -> {
                        if (files.indexOf(filename) < 0) {
                            File f = new File(filename);
                            f.delete();                     // remove it from file system
                        }
                    },
                    e -> Log.e(e.getMessage()),
                    this::finalizeImages);
        } else {
            autoShots.subscribe(
                    filename -> {
                        File file = new File(filename);
                        if (file.length() < MIN_FILE_SIZE) {   // automatic snapshot is smaller than expected
                            file.delete();                     // remove it from the file system
                            autoFiles.remove(filename);        // and the list
                        } else if (files.indexOf(filename) > 0) {// already exists in list
                            autoFiles.remove(filename);        // remove it from the list
                        }
                    },
                    e -> Log.e(e.getMessage()),
                    this::finalizeImages);
        }
    }

    private void finalizeImages() {
        if (files.size() + autoFiles.size() <= MAX_NUM_PICTURES) {
            files.addAll(autoFiles);
        } else {

            // now perform calculations
            int noOfImages = autoFiles.size();
            int pos = 0;           // pointer to current element
            long elem = 0;         // pointer to marked element
            int imageNo = 0;       // number of saved images
            int deleted = 0;       // number of deleted images
            double median = (1.0 * noOfImages / (MAX_NUM_PICTURES - files.size()));   // distribute evenly across the list using median
            for (String snapshotFile : autoFiles) {
                File file = new File(snapshotFile);
                if (pos != elem)                                                           // current element is not marked
                {
                    file.delete();                                                         // remove it
                    deleted++;
                } else {
                    ++imageNo;
                    elem = (long) (imageNo * median);                                        // mark next element
                    files.add(snapshotFile);                                                 // add element to list
                }
                pos++;                                                                       // move pointer to next element
            }
        }

        App.getInstance().getSnapshotService().removeExistingSnapshotContactImages(contact);
        if (files.size() > 0 && contact != null && entry != null) {
            App.getInstance().getContactService().updateContactImage(contact.getContactId(), files.get(0));
        }
    }

    protected void updateGallery(List<String> fileList) {
        if (fileList != null && fileList.size() > 0) {
            Observable.fromIterable(fileList)
                    .observeOn(Schedulers.io())
                    .subscribeOn(Schedulers.io())
                    .subscribe(filename -> {
                        String[] tmp = filename.split("/");
                        String[] dest = {Settings.getSnapshotGalleryFilename(entry, contact, tmp[tmp.length - 1])};
                        MediaScannerConnection.scanFile(App.getInstance().getApplicationContext(),
                                dest,
                                new String[]{"image/jpeg"},
                                (path, uri) -> {
                                    ContentValues values = new ContentValues();
                                    values.put(MediaStore.Images.Media.DATA, path);
                                    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); // or image/png
                                    App.getInstance().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                                });

                    });
        }
    }

    public synchronized void stopImageShooting() {
        if (running) {
            if (mHandler != null) {
                mHandler.removeCallbacks(mAutoShooting);
            }

            if (ht != null) {
                ht.quitSafely();
                ht = null;
            }
            running = false;

            distributeImages();
        }
    }

    public synchronized void manualSnapshot() {
        LinphoneCallManager callManager = App.getInstance().getCallManager();
        if (contact != null && callManager != null) {
            String filename = Settings.getSnapshotCallLogFilename(entry);
            takeSnapshot(filename, SnapshotType.MANUAL);
            manualFiles.add(filename);
        }
    }

    enum SnapshotType {
        AUTO,   // indicates automatic snapshots
        MANUAL  // indicates manual snapshot with entry update
    }

}
