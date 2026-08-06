package com.vaenow.appupdate.android;

/**
 * Created by LuoWen on 2015/12/14.
 */
public class Version {
    private long local;
    private long remote;

    public Version(long local, long remote) {
        this.local = local;
        this.remote = remote;
    }

    public long getLocal() {
        return local;
    }

    public long getRemote() {
        return remote;
    }
}