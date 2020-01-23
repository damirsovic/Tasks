package com.nn.my2ncommunicator.main.services.snapshot;

import android.media.MediaScannerConnection;
import android.provider.MediaStore;

import com.nn.my2ncommunicator.App;
import com.nn.my2ncommunicator.main.preference.Settings;
import com.nn.my2ncommunicator.model.calllog.CallLogEntry;
import com.nn.my2ncommunicator.model.contact.Contact;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.schedulers.Schedulers;
import quanti.com.kotlinlog.Log;

/**
 * Created by damir on 20.2.2018..
 */

public class ImagesService {
    private final String selectionClause = MediaStore.Images.Media.DATA + " = ?";

    public boolean hasAnyImage(long callLogId) {
        File[] folderContent = new File(Settings.getSnapshotCallLogFolder(callLogId)).listFiles();
        if (folderContent != null) {
            for (File f : folderContent) {
                //TODO linphone creates .part files when the call is terminated before snapshot is fully saved. These files are empty and we want to filter them. In new version of Linphone this will be solved.
                if (f.length() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    public String[] getImages(long callLogId) {
        List<String> files = new ArrayList<>();
        File[] folderContent = new File(Settings.getSnapshotCallLogFolder(callLogId)).listFiles();
        if (folderContent != null && folderContent.length > 0) {
            Arrays.sort(folderContent, this::compare);
            for (File f : folderContent) {
                if (f.length() > 0) {
                    files.add(f.getAbsolutePath());
                }
            }
        }
        String[] filesArray = new String[files.size()];
        return files.toArray(filesArray);
    }

    public File[] findGalleryImages(long callLogId) {
        final File folder = new File(Settings.getSnapshotGalleryFolder());
        final File[] subfolders = folder.listFiles(File::isDirectory);
        final String matchExpression = String.format("_%1$d.jpg", callLogId);
        File[] files = null;
        if (subfolders != null && subfolders.length > 0) {
            for (File subfolder : subfolders) {
                if ((files = subfolder.listFiles((dir, name) -> name.endsWith(matchExpression))) != null)
                    break;
            }
        }
        return files;
    }

    public File[] findGalleryImages(CallLogEntry entry) {
        return findGalleryImages(entry.getId());
    }

    public void deleteImage(long callLogId, int pos) {
        deleteImage(getImages(callLogId)[pos]);
    }

    public void deleteImage(String path) {
        Maybe.just(new File(path))
                .flatMap(file -> {
                    file.delete();
                    String[] selectionArgs = {file.getAbsolutePath()};
                    App.getInstance().getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selectionClause, selectionArgs);
                    return Maybe.just(file);
                })
                .observeOn(Schedulers.io())
                .subscribeOn(Schedulers.io())
                .subscribe(
                        f -> Log.d("Image " + f.getName() + " deleted"),
                        e -> Log.e(e.getMessage())
                );
    }

    public void deleteCallLog(long callLogId) {
        purgeFolder(new File(Settings.getSnapshotCallLogFolder(callLogId)));
    }

    public void deleteCallLog(CallLogEntry entry) {
        deleteCallLog(entry.getId());
    }

    private final List<String> deletedFiles = new ArrayList<>();

    public void purgeFolder(File item) {
        if (item.isDirectory())
            if (item.listFiles() != null)
                for (File child : item.listFiles())
                    purgeFolder(child);

        item.delete();
        deletedFiles.add(item.getAbsolutePath());
    }

    public void updateGallery() {

        Observable.fromIterable(deletedFiles)
                .filter(s -> s.contains(android.os.Environment.DIRECTORY_DCIM))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(
                        arg -> App.getInstance().getContentResolver().delete(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selectionClause, new String[]{arg}),
                        e -> Log.e(e.getMessage()),
                        () -> MediaScannerConnection.scanFile(
                                App.getInstance().getApplicationContext(),
                                deletedFiles.toArray(new String[0]),
                                new String[]{"image/jpeg"},
                                (path, uri) -> deletedFiles.clear())
                );
    }


    public void purgeFiles(File[] files) {
        if (files != null && files.length > 0) {
            if (deletedFiles.size() > 0)
                deletedFiles.clear();
            Observable.fromArray(files)
                    .observeOn(Schedulers.io())
                    .subscribeOn(Schedulers.io())
                    .subscribe(
                            child -> {
                                child.delete();
                                deletedFiles.add(child.getAbsolutePath());
                            },
                            e -> Log.e(e.getMessage()),
                            () -> updateGallery()
                    );
        }
    }

    public void removeExistingSnapshotContactImages(Contact contact) {
        final String matchExpression = String.format("-%1$s.jpg", contact.getSipName());
        final File folder = new File(Settings.getSnapshotContactFolder());
        File[] files;
        if ((files = folder.listFiles((dir, name) -> name.endsWith(matchExpression))) != null) {
            for (File f : files) {
                f.delete();
            }
        }
    }

    public int compare(File file, File file2) {
        return (int) (file.lastModified() - file2.lastModified());
    }
}
