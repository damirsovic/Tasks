package com.nn.my2ncommunicator.main.services.lockevent;

import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.ProcessLifecycleOwner;
import android.util.LongSparseArray;

import com.nn.my2ncommunicator.App;
import com.nn.my2ncommunicator.core.BaseClassWithListener;
import com.nn.my2ncommunicator.main.callog.detail.lockevent.LockEventWrapper;
import com.nn.my2ncommunicator.main.linphone.ICallStateListener;
import com.nn.my2ncommunicator.model.calllog.CallLogEntry;
import com.nn.my2ncommunicator.model.dtmf.Dtmf;
import com.nn.my2ncommunicator.model.lockevent.LockEvent;
import com.nn.my2ncommunicator.model.lockevent.LockEventRepository;

import org.linphone.core.LinphoneCall;
import org.linphone.core.LinphoneCore;

import java.util.ArrayList;
import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.MaybeOnSubscribe;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import quanti.com.kotlinlog.Log;

/**
 * Created by damir on 12.3.2018..
 */

public class LockEventService extends BaseClassWithListener<ILockEventListener> implements ICallStateListener {

    private CallLogEntry currentCallLogEntry = null;
    private final List<LockEventWrapper> mLockEvents = new ArrayList<>();
    private final LongSparseArray<List<LockEventWrapper>> mMappedLockEventPositions = new LongSparseArray<>();

    public LockEventService() {
        setupObservers(ProcessLifecycleOwner.get());
    }

    private void setupObservers(LifecycleOwner owner) {
        //setup observers
        Log.d("Setup LockEvent observers");

        LockEventRepository.INSTANCE.getAll().observe(owner, lockEventRoom ->
                {
                    Log.d("LOCKEVENT START");

                    if (lockEventRoom != null) {
                        long currentCallLogEntryId = -1;
                        List<LockEventWrapper> mLockEventWrappers = new ArrayList<>();
                        for (LockEvent entry : lockEventRoom) {
                            if (currentCallLogEntryId != entry.getCallLogEntryId()) {
                                if (mLockEventWrappers.size() > 0)
                                    mMappedLockEventPositions.put(currentCallLogEntryId, mLockEventWrappers);
                                mLockEventWrappers = new ArrayList<>();
                            }

                            currentCallLogEntryId = entry.getCallLogEntryId();
                            mLockEventWrappers.add(wrapLockEvent(entry));
                        }
                        mMappedLockEventPositions.put(currentCallLogEntryId, mLockEventWrappers);
                        notifyListeners();
                    }
                    mLockEvents.clear();
                    Log.d("LOCKEVENT START END");
                }
        );
    }

    public Single<Long> save(LockEvent lockEvent) {
        return Single.create(e -> {
            Long result = 0L;
            try {
                result = LockEventRepository.INSTANCE.save(lockEvent);
            } catch (Exception ex) {
                e.onError(ex);
            } finally {
                e.onSuccess(result);
            }
        });
    }

    public Completable deleteById(long callLogId) {
        return Completable.fromAction(() -> LockEventRepository.INSTANCE.deleteById(callLogId));
    }

    public Completable deleteAll() {
        return Completable.fromAction(LockEventRepository.INSTANCE::deleteAll);
    }

    public void saveLockEvent(Dtmf dtmf) {
        if (currentCallLogEntry != null) {
            LockEventWrapper wrapper = new LockEventWrapper();
            wrapper.setCallLogEntryId(currentCallLogEntry.getId());
            wrapper.setDtmfId(dtmf.getId());
            wrapper.setDtmfName(dtmf.getName());
            wrapper.setUnlockTime(System.currentTimeMillis());
            mLockEvents.add(wrapper);
        }
    }

    public LockEventWrapper wrapLockEvent(LockEvent lockEvent) {
        LockEventWrapper wrapper = new LockEventWrapper();
        wrapper.setLockEventId(lockEvent.getId());
        wrapper.setDtmfId(lockEvent.getDtmfId());
        wrapper.setCallLogEntryId(lockEvent.getCallLogEntryId());
        Dtmf dtmf = App.getInstance().getContactService().getDtmfById(lockEvent.getDtmfId());
        if (dtmf != null)
            wrapper.setDtmfName(dtmf.getName());
        wrapper.setUnlockTime(lockEvent.getUnlockTime());
        return wrapper;
    }

    public LockEvent unwrapLockEvent(LockEventWrapper lockEventWrapper) {
        LockEvent lockEvent = new LockEvent();
        if (lockEventWrapper.getLockEventId() != -1)
            lockEvent.setId(lockEventWrapper.getLockEventId());
        lockEvent.setDtmfId(lockEventWrapper.getDtmfId());
        lockEvent.setCallLogEntryId(lockEventWrapper.getCallLogEntryId());
        lockEvent.setUnlockTime(lockEventWrapper.getUnlockTime());
        return lockEvent;
    }

    public void saveLockEvents() {
        if (mLockEvents.size() > 0) {
            Observable.
                    fromIterable(mLockEvents).
                    map(this::unwrapLockEvent).
                    subscribe(
                            elem -> save(elem)
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(id -> elem.setId(id)),
                            err -> Log.e(String.format("Error saving values: %s", err.getMessage()))
                    );
        }
    }

    public List<LockEventWrapper> getLockEvents(long callLogEntryId) {
        return mMappedLockEventPositions.get(callLogEntryId);
    }

    @Override
    public void onCallStateChanged(LinphoneCore linphoneCore, LinphoneCall call, LinphoneCall.State state) {
        switch (state.value()) {
            case 6:
                // resolve contact & calllog id
                currentCallLogEntry = App.getInstance().getCallLogService().getCurrentCallLogEntry();
                break;

            case 12:
                // Error
            case 13:
                // call end
                // save all events
                if (mLockEvents.size() > 0) {
                    saveLockEvents();
                }
                // reset state
                mLockEvents.clear();
                currentCallLogEntry = null;
                break;
        }
    }

    public void setCurrentCallLogEntry(CallLogEntry callLogEntry) {
        currentCallLogEntry = callLogEntry;
    }

    public void deleteByCallLogId(long callLogId) {
        deleteById(callLogId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe();
    }

    protected void notifyListeners() {
        Log.d("LockEvent service reloaded");
        for (ILockEventListener listener : mListeners) {
            listener.onLockEventChanged();
        }
    }
}
