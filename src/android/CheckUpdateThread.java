package com.vaenow.appupdate.android;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Handler;
import org.apache.cordova.LOG;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;

/**
 * Created by LuoWen on 2015/12/14.
 */
public class CheckUpdateThread implements Runnable {
    private static final int CONNECT_TIMEOUT_MS = 10000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final String TAG = "CheckUpdateThread";

    /* Parsed XML data */
    HashMap<String, String> mHashMap;
    private Context mContext;
    private List<Version> queue;
    private String packageName;
    private String updateXmlUrl;
    private AuthenticationOptions authentication;
    private Handler mHandler;

    private void setMHashMap(HashMap<String, String> mHashMap) {
        this.mHashMap = mHashMap;
    }

    public HashMap<String, String> getMHashMap() {
        return mHashMap;
    }

    public CheckUpdateThread(Context mContext, Handler mHandler, List<Version> queue, String packageName, String updateXmlUrl, JSONObject options) {
        this.mContext = mContext;
        this.queue = queue;
        this.packageName = packageName;
        this.updateXmlUrl = updateXmlUrl;
        this.authentication = new AuthenticationOptions(options);
        this.mHandler = mHandler;
    }

    @Override
    public void run() {
        try {
            long versionCodeLocal = getVersionCodeLocal(mContext);
            long versionCodeRemote = getVersionCodeRemote();

            queue.clear();
            queue.add(new Version(versionCodeLocal, versionCodeRemote));
            mHandler.sendEmptyMessage(Constants.VERSION_COMPARE_START);
        } catch (FileNotFoundException e) {
            LOG.e(TAG, "Update metadata was not found", e);
            mHandler.sendEmptyMessage(Constants.REMOTE_FILE_NOT_FOUND);
        } catch (IOException e) {
            LOG.e(TAG, "Unable to retrieve update metadata", e);
            mHandler.sendEmptyMessage(Constants.NETWORK_ERROR);
        } catch (Exception e) {
            LOG.e(TAG, "Unable to parse update metadata", e);
            mHandler.sendEmptyMessage(Constants.VERSION_RESOLVE_FAIL);
        }
    }

    /**
     * Return a file input stream from a URL
     *
     * @param path
     * @return
     */
    private InputStream returnFileIS(String path) throws IOException {
        LOG.d(TAG, "returnFileIS..");

        URL url = new URL(path);
        requireHttps(url, "Update metadata URL");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setInstanceFollowRedirects(!authentication.hasCredentials());

        if(this.authentication.hasCredentials()){
            conn.setRequestProperty("Authorization", this.authentication.getEncodedAuthorization());
        }

        conn.setDoInput(true);
        int status = conn.getResponseCode();
        requireHttps(conn.getURL(), "Update metadata redirect URL");
        if (status == HttpURLConnection.HTTP_NOT_FOUND) {
            conn.disconnect();
            throw new FileNotFoundException(path);
        }
        if (status < HttpURLConnection.HTTP_OK || status >= HttpURLConnection.HTTP_MULT_CHOICE) {
            conn.disconnect();
            throw new IOException("Unexpected HTTP status " + status);
        }
        return new DisconnectingInputStream(conn);
    }

    /**
     * Get the installed application version code
     * <p/>
     * It's weird, I don't know why.
     * <pre>
     * versionName -> versionCode
     * 0.0.1    ->  12
     * 0.3.4    ->  3042
     * 3.2.4    ->  302042
     * 12.234.221 -> 1436212
     * </pre>
     *
     * @param context
     * @return
     */
    private long getVersionCodeLocal(Context context) throws NameNotFoundException {
        LOG.d(TAG, "getVersionCode..");

        PackageInfo packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return packageInfo.getLongVersionCode();
        }
        return packageInfo.versionCode;
    }

    /**
     * Get the server application version code
     *
     * @return
     */
    private long getVersionCodeRemote() throws Exception {
        HashMap<String, String> updateData;
        try (InputStream is = returnFileIS(updateXmlUrl)) {
            ParseXmlService service = new ParseXmlService();
            setMHashMap(service.parseXml(is));
            updateData = getMHashMap();
        }

        if (updateData == null || !updateData.containsKey("version")
                || !updateData.containsKey("name") || !updateData.containsKey("url")) {
            throw new IllegalArgumentException("Update metadata must contain version, name, and url");
        }

        long versionCode = Long.parseLong(updateData.get("version"));
        if (versionCode <= 0) {
            throw new IllegalArgumentException("Version code must be positive");
        }

        URL apkUrl;
        try {
            apkUrl = new URL(updateData.get("url"));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid APK URL", e);
        }
        requireHttps(apkUrl, "APK URL");
        return versionCode;
    }

    private void requireHttps(URL url, String label) {
        if (!"https".equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException(label + " must use HTTPS");
        }
    }

    private static class DisconnectingInputStream extends java.io.FilterInputStream {
        private final HttpURLConnection connection;

        DisconnectingInputStream(HttpURLConnection connection) throws IOException {
            super(connection.getInputStream());
            this.connection = connection;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                connection.disconnect();
            }
        }
    }
}