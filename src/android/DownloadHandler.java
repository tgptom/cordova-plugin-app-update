package com.vaenow.appupdate.android;

import org.apache.cordova.BuildHelper;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ProgressBar;
import androidx.core.content.FileProvider;
import java.io.File;

import org.apache.cordova.LOG;

/**
 * Created by LuoWen on 2015/12/14.
 */
public class DownloadHandler extends Handler {
    private static final String TAG = "DownloadHandler";

    private Context mContext;
    /* Progress bar */
    private ProgressBar mProgress;
    private File apkFile;
    private MsgHelper msgHelper;
    private AlertDialog mDownloadDialog;

    public DownloadHandler(Context mContext, ProgressBar mProgress, AlertDialog mDownloadDialog, File apkFile) {
        // Dialog and progress bar updates must always run on the UI thread
        super(Looper.getMainLooper());
        this.msgHelper = new MsgHelper(mContext.getPackageName(), mContext.getResources());
        this.mDownloadDialog = mDownloadDialog;
        this.mContext = mContext;
        this.mProgress = mProgress;
        this.apkFile = apkFile;
    }

    public void handleMessage(Message msg) {
        switch (msg.what) {
            // Downloading
            case Constants.DOWNLOAD:
                // Update the progress bar; the value is carried in the message
                applyProgress(mProgress, msg.arg1);
                break;
            case Constants.DOWNLOAD_FAILED:
                // Stop the indeterminate animation so a failed download does not look active
                if (mProgress != null && mProgress.isIndeterminate()) {
                    mProgress.setIndeterminate(false);
                }
                if (mDownloadDialog.isShowing()) {
                    mDownloadDialog.dismiss();
                }
                break;
            case Constants.DOWNLOAD_FINISH:
                updateMsgDialog();
                // Install the file
                installApk();
                break;
            default:
                break;
        }
    }

    /**
     * Post a progress value from the download thread to the UI thread.
     *
     * @param progress a percentage in 0..100, or {@link DownloadProgress#INDETERMINATE}
     */
    public void sendProgress(int progress) {
        obtainMessage(Constants.DOWNLOAD, progress, 0).sendToTarget();
    }

    /**
     * Show a percentage in 0..100, or an animated indeterminate bar for
     * {@link DownloadProgress#INDETERMINATE}. Must be called on the UI thread.
     */
    public static void applyProgress(ProgressBar progressBar, int progress) {
        if (progressBar == null) {
            return;
        }
        if (progress < 0) {
            if (!progressBar.isIndeterminate()) {
                LOG.d(TAG, "Progress mode: indeterminate (size unknown)");
                progressBar.setIndeterminate(true);
            }
            return;
        }
        if (progressBar.isIndeterminate()) {
            LOG.d(TAG, "Progress mode: determinate");
            progressBar.setIndeterminate(false);
        }
        progressBar.setMax(100);
        progressBar.setProgress(Math.min(100, progress));
    }

    public void updateMsgDialog() {
        mDownloadDialog.setTitle(msgHelper.getString(MsgHelper.DOWNLOAD_COMPLETE_TITLE));
        if (mDownloadDialog.isShowing()) {
            mDownloadDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.GONE); //Update in background
            mDownloadDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setVisibility(View.VISIBLE); //Install Manually
            mDownloadDialog.getButton(DialogInterface.BUTTON_POSITIVE).setVisibility(View.VISIBLE); //Download Again

            mDownloadDialog.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(downloadCompleteOnClick);
        }
    }

    private OnClickListener downloadCompleteOnClick = new OnClickListener() {
        @Override
        public void onClick(View view) {
            installApk();
        }
    };

    /**
     * Install the APK file
     */
    private void installApk() {
        LOG.d(TAG, "Installing APK");

        if (!apkFile.exists()) {
            LOG.e(TAG, "Could not find APK: " + apkFile);
            return;
        }

        LOG.d(TAG, "APK Filename: " + apkFile.toString());

        // Install the APK file using an Intent
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            LOG.d(TAG, "Build SDK Greater than or equal to Nougat");
            String applicationId = (String) BuildHelper.getBuildConfigValue((Activity) mContext, "APPLICATION_ID");
            Uri apkUri = FileProvider.getUriForFile(mContext, applicationId + ".appupdate.provider", apkFile);
            Intent i = new Intent(Intent.ACTION_INSTALL_PACKAGE);
            i.setData(apkUri);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
            mContext.startActivity(i);
        }else{
            LOG.d(TAG, "Build SDK less than Nougat");
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setDataAndType(Uri.parse("file://" + apkFile.toString()), "application/vnd.android.package-archive");
            mContext.startActivity(i);
        }

    }
}
