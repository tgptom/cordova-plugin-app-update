package com.vaenow.appupdate.android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.widget.ProgressBar;
import org.json.JSONObject;
import org.apache.cordova.LOG;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

/**
 * File download thread
 */
public class DownloadApkThread implements Runnable {
    private static final String TAG = "DownloadApkThread";
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final long OLD_DOWNLOAD_AGE_MS = 24L * 60L * 60L * 1000L;

    /* Parsed XML data */
    HashMap<String, String> mHashMap;
    /* Download destination */
    private String mSavePath;
    /* Download progress */
    private int progress;
    private DownloadHandler downloadHandler;
    private Handler mHandler;
    private AuthenticationOptions authentication;
    private long uniqueVersionId;
    private Context mContext;
    private File apkFile;

    public DownloadApkThread(Context mContext, Handler mHandler, ProgressBar mProgress, AlertDialog mDownloadDialog, HashMap<String, String> mHashMap, JSONObject options) {
        this.mHashMap = mHashMap;
        this.mHandler = mHandler;
        this.mContext = mContext;
        this.authentication = new AuthenticationOptions(options);

        File downloadDirectory = mContext.getExternalFilesDir(null);
        if (downloadDirectory == null) {
            downloadDirectory = new File(mContext.getFilesDir(), "download");
        }
        this.mSavePath = new File(downloadDirectory, "appupdate").getAbsolutePath();
        this.uniqueVersionId = System.currentTimeMillis();
        this.apkFile = new File(this.mSavePath, "update-" + this.uniqueVersionId + ".apk");
        this.downloadHandler = new DownloadHandler(mContext, mProgress, mDownloadDialog, apkFile);
    }


    @Override
    public void run() {
        downloadAndInstall();
    }

    private void downloadAndInstall() {
        HttpURLConnection conn = null;
        File temporaryFile = null;
        try {
            File file = new File(mSavePath);
            // Check whether the download directory exists
            if (!file.exists() && !file.mkdirs()) {
                throw new IOException("Failed to create directory: " + mSavePath);
            }
            deleteOldDownloads(file);
            temporaryFile = new File(apkFile.getAbsolutePath() + ".part");

            URL url = new URL(mHashMap.get("url"));
            // Open the connection
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(!authentication.hasCredentials());

            if(this.authentication.hasCredentials()){
                conn.setRequestProperty("Authorization", this.authentication.getEncodedAuthorization());
            }

            int status = conn.getResponseCode();
            if (status < HttpURLConnection.HTTP_OK || status >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new IOException("Unexpected HTTP status " + status);
            }
            // Get the file size
            long length = conn.getContentLengthLong();

            long count = 0;
            // Read buffer
            byte buf[] = new byte[1024];

            try (InputStream is = conn.getInputStream(); FileOutputStream fos = new FileOutputStream(temporaryFile)) {
                // Write to the file
                while (true) {
                    int numread = is.read(buf);
                    if (numread == -1) {
                        break;
                    }
                    count += numread;
                    // Calculate download progress
                    progress = (length > 0) ? (int) Math.min(100, (count * 100) / length) : 0;
                    downloadHandler.updateProgress(progress);
                    // Update progress
                    downloadHandler.sendEmptyMessage(Constants.DOWNLOAD);
                    // Write to the file
                    fos.write(buf, 0, numread);
                }
                fos.flush();
            }

            if (length >= 0 && count != length) {
                throw new IOException("Incomplete download: expected " + length + " bytes but received " + count);
            }
            validateApk(temporaryFile);
            if (!temporaryFile.renameTo(apkFile)) {
                throw new IOException("Failed to finalize downloaded APK");
            }

            temporaryFile = null;
            downloadHandler.sendEmptyMessage(Constants.DOWNLOAD_FINISH);
            mHandler.sendEmptyMessage(Constants.DOWNLOAD_FINISH);
        } catch (IOException e) {
            LOG.e(TAG, "APK download failed", e);
            mHandler.sendEmptyMessage(Constants.NETWORK_ERROR);
        } finally {
            if (temporaryFile != null && temporaryFile.exists() && !temporaryFile.delete()) {
                LOG.w(TAG, "Unable to delete incomplete APK: " + temporaryFile);
            }
            if (conn != null) {
                conn.disconnect();
            }
        }

    }

    private void validateApk(File apkFile) throws IOException {
        PackageInfo packageInfo = mContext.getPackageManager().getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
        if (packageInfo == null || !mContext.getPackageName().equals(packageInfo.packageName)) {
            throw new IOException("Downloaded file is not an APK update for this application");
        }
    }

    private void deleteOldDownloads(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        long expirationTime = System.currentTimeMillis() - OLD_DOWNLOAD_AGE_MS;
        for (File file : files) {
            if ((file.getName().endsWith(".apk") || file.getName().endsWith(".part"))
                    && file.lastModified() < expirationTime
                    && !file.delete()) {
                LOG.w(TAG, "Unable to delete old download: " + file);
            }
        }
    }
}