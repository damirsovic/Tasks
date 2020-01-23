package com.nn.my2ncommunicator.main.callog.detail.lockevent;

/**
 * Created by damir on 16.3.2018..
 */

public class LockEventWrapper {
    private long lockEventId = -1;
    private long callLogEntryId = -1;
    private long dtmfId = -1;
    private String dtmfName;
    private long unlockTime = -1;

    public long getCallLogEntryId() {
        return callLogEntryId;
    }

    public void setCallLogEntryId(long callLogEntryId) {
        this.callLogEntryId = callLogEntryId;
    }

    public long getDtmfId() {
        return dtmfId;
    }

    public void setDtmfId(long dtmfId) {
        this.dtmfId = dtmfId;
    }

    public String getDtmfName() {
        return dtmfName;
    }

    public void setDtmfName(String dtmfName) {
        this.dtmfName = dtmfName;
    }

    public long getLockEventId() {
        return lockEventId;
    }

    public void setLockEventId(long lockEventId) {
        this.lockEventId = lockEventId;
    }

    public long getUnlockTime() {
        return unlockTime;
    }

    public void setUnlockTime(long unlockTime) {
        this.unlockTime = unlockTime;
    }
}
