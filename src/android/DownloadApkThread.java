package com.vaenow.appupdate.android;

import android.AuthenticationOptions;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.widget.ProgressBar;
import android.util.Base64;
import org.json.JSONObject;
import org.json.JSONException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.lang.*;

import java.nio.charset.StandardCharsets;

/**
 * File download thread
 */
public class DownloadApkThread implements Runnable {
    private String TAG = "DownloadApkThread";

    /* Parsed XML data */
    HashMap<String, String> mHashMap;
    /* Download destination */
    private String mSavePath;
    /* Download progress */
    private int progress;
    /* Whether the update has been canceled */
    private boolean cancelUpdate = false;
    private AlertDialog mDownloadDialog;
    private DownloadHandler downloadHandler;
    private Handler mHandler;
    private AuthenticationOptions authentication;
    private long uniqueVersionId;

    public DownloadApkThread(Context mContext, Handler mHandler, ProgressBar mProgress, AlertDialog mDownloadDialog, HashMap<String, String> mHashMap, JSONObject options) {
        this.mDownloadDialog = mDownloadDialog;
        this.mHashMap = mHashMap;
        this.mHandler = mHandler;
        this.authentication = new AuthenticationOptions(options);

        File downloadDirectory = mContext.getExternalFilesDir(null);
        if (downloadDirectory == null) {
            downloadDirectory = new File(mContext.getFilesDir(), "download");
        }
        this.mSavePath = new File(downloadDirectory, "appupdate").getAbsolutePath();
        this.uniqueVersionId = System.currentTimeMillis();
        this.downloadHandler = new DownloadHandler(mContext, mProgress, mDownloadDialog, this.mSavePath, mHashMap, this.uniqueVersionId);
    }


    @Override
    public void run() {
        downloadAndInstall();
        // Dismiss the download dialog
        // mDownloadDialog.dismiss();
    }

    public void cancelBuildUpdate() {
        this.cancelUpdate = true;
    }

    private void downloadAndInstall() {
        HttpURLConnection conn = null;
        try {
            File file = new File(mSavePath);
            // Check whether the download directory exists
            if (!file.exists() && !file.mkdirs()) {
                throw new IOException("Failed to create directory: " + mSavePath);
            }
            File apkFile = new File(mSavePath, mHashMap.get("name")+this.uniqueVersionId+".apk");

            URL url = new URL(mHashMap.get("url"));
            // Open the connection
            conn = (HttpURLConnection) url.openConnection();

            if(this.authentication.hasCredentials()){
                conn.setRequestProperty("Authorization", this.authentication.getEncodedAuthorization());
            }

            conn.connect();
            // Get the file size
            int length = conn.getContentLength();

            int count = 0;
            // Read buffer
            byte buf[] = new byte[1024];

            try (InputStream is = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(apkFile)) {
                // Write to the file
                do {
                    int numread = is.read(buf);
                    if (numread <= 0) {
                        // Download complete
                        downloadHandler.sendEmptyMessage(Constants.DOWNLOAD_FINISH);
                        mHandler.sendEmptyMessage(Constants.DOWNLOAD_FINISH);
                        break;
                    }
                    count += numread;
                    // Calculate download progress
                    progress = (length > 0) ? (int) (((float) count / length) * 100) : 0;
                    downloadHandler.updateProgress(progress);
                    // Update progress
                    downloadHandler.sendEmptyMessage(Constants.DOWNLOAD);
                    // Write to the file
                    fos.write(buf, 0, numread);
                } while (!cancelUpdate);// Stop downloading when canceled.
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
            mHandler.sendEmptyMessage(Constants.NETWORK_ERROR);
        } catch (IOException e) {
            e.printStackTrace();
            mHandler.sendEmptyMessage(Constants.NETWORK_ERROR);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }

    }
}