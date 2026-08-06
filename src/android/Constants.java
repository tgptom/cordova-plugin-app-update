package com.vaenow.appupdate.android;

/**
 * Created by LuoWen on 2015/12/14.
 */
public interface Constants {
    /* Downloading */
    int DOWNLOAD = 1;
    /* Download complete */
    int DOWNLOAD_FINISH = 2;
    /* Download button clicked */
    int DOWNLOAD_CLICK_START = 3;

    /**
     * Version comparison
     */
    int VERSION_COMPARE_START = 200; // Start comparing versions
    int VERSION_NEED_UPDATE = 201; // Update available
    int VERSION_UP_TO_UPDATE = 202; // Version is up to date
    int VERSION_UPDATING = 203; // Update in progress

    /**
     * Version parsing errors
     */
    int VERSION_RESOLVE_FAIL = 301; // Version XML parsing failed
    int VERSION_COMPARE_FAIL = 302; // Version comparison failed

    /**
     * Network errors
     */
    int REMOTE_FILE_NOT_FOUND = 404;
    int NETWORK_ERROR = 405;
    int OPERATION_IN_PROGRESS = 409;

    /**
     * No such method
     */
    int NO_SUCH_METHOD = 501;

    /**
     * Permissions
     */
    int PERMISSION_DENIED = 601;

    /**
     * Unknown error
     */
    int UNKNOWN_ERROR = 901;

}
