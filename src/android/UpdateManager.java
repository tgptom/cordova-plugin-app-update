package com.vaenow.appupdate.android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Handler;
import android.widget.ProgressBar;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by LuoWen on 2015/10/27.
 * <p/>
 * Thanks @coolszy
 */
public class UpdateManager {
    public interface InstallPermissionRequester {
        void runWithInstallPermission(Runnable action);
    }

    public static final String TAG = "UpdateManager";

    /*
     * Remote version file format
     *   <update>
     *       <version>2222</version>
     *       <name>name</name>
     *       <url>https://192.168.3.102/android.apk</url>
     *   </update>
     */
    private String updateXmlUrl;
    private JSONObject options;
    private CordovaInterface cordova;
    private CallbackContext callbackContext;
    private String packageName;
    private Context mContext;
    private MsgBox msgBox;
    private final AtomicBoolean isChecking = new AtomicBoolean(false);
    private final AtomicBoolean isDownloading = new AtomicBoolean(false);
    private final AtomicBoolean operationInProgress = new AtomicBoolean(false);
    private List<Version> queue = new ArrayList<Version>(1);
    private CheckUpdateThread checkUpdateThread;
    private DownloadApkThread downloadApkThread;
    private InstallPermissionRequester installPermissionRequester;

    public UpdateManager(Context context, CordovaInterface cordova, InstallPermissionRequester installPermissionRequester) {
        this.cordova = cordova;
        this.mContext = context;
        this.installPermissionRequester = installPermissionRequester;
        packageName = mContext.getPackageName();
        msgBox = new MsgBox(mContext);
    }

    public boolean options(JSONArray args, CallbackContext callbackContext)
            throws JSONException {
        if (!operationInProgress.compareAndSet(false, true)) {
            callbackContext.error(Utils.makeJSON(Constants.OPERATION_IN_PROGRESS, "an update operation is already in progress"));
            return false;
        }
        try {
            this.callbackContext = callbackContext;
            this.updateXmlUrl = args.getString(0);
            this.options = args.getJSONObject(1);
            return true;
        } catch (JSONException e) {
            operationInProgress.set(false);
            throw e;
        }
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(android.os.Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case Constants.NETWORK_ERROR:
                    isChecking.set(false);
                    isDownloading.set(false);
                    operationInProgress.set(false);
                    if (msg.obj instanceof JSONObject) {
                        callbackContext.error((JSONObject) msg.obj);
                    } else {
                        callbackContext.error(Utils.makeJSON(Constants.NETWORK_ERROR, "network error"));
                    }
                    break;
                case Constants.VERSION_COMPARE_START:
                    isChecking.set(false);
                    compareVersions();
                    break;
                case Constants.DOWNLOAD_CLICK_START:
                    emitNoticeDialogOnClick();
                    break;
                case Constants.DOWNLOAD_FINISH:
                    isDownloading.set(false);
                    operationInProgress.set(false);
                    break;
                case Constants.VERSION_UPDATING:
                    callbackContext.success(Utils.makeJSON(Constants.VERSION_UPDATING, "success, version updating."));
                    break;
                case Constants.VERSION_NEED_UPDATE:
                    callbackContext.success(Utils.makeJSON(Constants.VERSION_NEED_UPDATE, "success, need update."));
                    break;
                case Constants.VERSION_UP_TO_UPDATE:
                    operationInProgress.set(false);
                    callbackContext.success(Utils.makeJSON(Constants.VERSION_UP_TO_UPDATE, "success, up to date."));
                    break;
                case Constants.VERSION_COMPARE_FAIL:
                    isChecking.set(false);
                    operationInProgress.set(false);
                    callbackContext.error(Utils.makeJSON(Constants.VERSION_COMPARE_FAIL, "version compare fail"));
                    break;
                case Constants.VERSION_RESOLVE_FAIL:
                    isChecking.set(false);
                    operationInProgress.set(false);
                    callbackContext.error(Utils.makeJSON(Constants.VERSION_RESOLVE_FAIL, "version resolve fail"));
                    break;
                case Constants.REMOTE_FILE_NOT_FOUND:
                    isChecking.set(false);
                    operationInProgress.set(false);
                    callbackContext.error(Utils.makeJSON(Constants.REMOTE_FILE_NOT_FOUND, "remote file not found"));
                    break;
                default:
                    operationInProgress.set(false);
                    callbackContext.error(Utils.makeJSON(Constants.UNKNOWN_ERROR, "unknown error"));
            }

        }
    };

    /**
     * Check for application updates
     */
    public boolean checkUpdate() {
        if (!isChecking.compareAndSet(false, true)) {
            operationInProgress.set(false);
            callbackContext.error(Utils.makeJSON(Constants.OPERATION_IN_PROGRESS, "an update check is already in progress"));
            return false;
        }
        LOG.d(TAG, "checkUpdate..");

        checkUpdateThread = new CheckUpdateThread(mContext, mHandler, queue, packageName, updateXmlUrl, options);
        this.cordova.getThreadPool().execute(checkUpdateThread);
        //new Thread(checkUpdateThread).start();
        return true;
    }

    /**
     * Permissions denied
     */
    public void permissionDenied(String errMsg) {
        LOG.d(TAG, "permissionsDenied..");

        isDownloading.set(false);
        operationInProgress.set(false);
        callbackContext.error(Utils.makeJSON(Constants.PERMISSION_DENIED, errMsg));
    }

    /**
     * Compare version codes
     */
    private void compareVersions() {
        Version version = queue.get(0);
        long versionCodeLocal = version.getLocal();
        long versionCodeRemote = version.getRemote();

        boolean skipPromptDialog = false;
        try {
            skipPromptDialog = options.getBoolean("skipPromptDialog");
        } catch (JSONException e) {}

        boolean skipProgressDialog = false;
        try {
            skipProgressDialog = options.getBoolean("skipProgressDialog");
        } catch (JSONException e) {}

        // Compare version codes
        // Check whether a newer application version is available
        if (versionCodeLocal < versionCodeRemote) {
            if (isDownloading.get()) {
                msgBox.showDownloadDialog(null, null, null, !skipProgressDialog);
                mHandler.sendEmptyMessage(Constants.VERSION_UPDATING);
            } else {
                LOG.d(TAG, "need update");
                if (skipPromptDialog) {
                    mHandler.sendEmptyMessage(Constants.DOWNLOAD_CLICK_START);
                } else {
                    // Show the update prompt
                    msgBox.showNoticeDialog(noticeDialogOnClick);
                    mHandler.sendEmptyMessage(Constants.VERSION_NEED_UPDATE);
                }
            }
        } else {
            mHandler.sendEmptyMessage(Constants.VERSION_UP_TO_UPDATE);
            // Do not show Toast
            //Toast.makeText(mContext, getString("update_latest"), Toast.LENGTH_LONG).show();
        }
    }

    private OnClickListener noticeDialogOnClick = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            mHandler.sendEmptyMessage(Constants.DOWNLOAD_CLICK_START);
        }
    };

    private void emitNoticeDialogOnClick() {
        installPermissionRequester.runWithInstallPermission(new Runnable() {
            @Override
            public void run() {
                startDownload();
            }
        });
    }

    private void startDownload() {
        if (!isDownloading.compareAndSet(false, true)) {
            mHandler.sendEmptyMessage(Constants.VERSION_UPDATING);
            return;
        }

        boolean skipProgressDialog = false;
        try {
            skipProgressDialog = options.getBoolean("skipProgressDialog");
        } catch (JSONException e) {}

        // Show the download dialog
        Map<String, Object> ret = msgBox.showDownloadDialog(
                downloadDialogOnClickNeg,
                downloadDialogOnClickPos,
                downloadDialogOnClickNeu,
                !skipProgressDialog);

        // Download the file
        downloadApk((AlertDialog) ret.get("dialog"), (ProgressBar) ret.get("progress"));
    }

    /**
     * Install manually
     * Download again
     */
    private OnClickListener downloadDialogOnClickNeu = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            //Implemented in DownloadHandler.java
        }
    };
    /**
     * Download again
     */
    private OnClickListener downloadDialogOnClickPos = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
            mHandler.sendEmptyMessage(Constants.DOWNLOAD_CLICK_START);
        }
    };
    /**
     * Update in background
     */
    private OnClickListener downloadDialogOnClickNeg = new OnClickListener() {
        @Override
        public void onClick(DialogInterface dialog, int which) {
            dialog.dismiss();
        }
    };

    /**
     * Download the APK file
     *
     * @param mProgress
     * @param mDownloadDialog
     */
    private void downloadApk(AlertDialog mDownloadDialog, ProgressBar mProgress) {
        LOG.d(TAG, "downloadApk" + mProgress);

        // Start the application download on a new thread
        downloadApkThread = new DownloadApkThread(mContext, mHandler, mProgress, mDownloadDialog, checkUpdateThread.getMHashMap(), options);
        this.cordova.getThreadPool().execute(downloadApkThread);
    }

}
