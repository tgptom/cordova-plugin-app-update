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
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

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
            if (isTlsCertificateFailure(e)) {
                LOG.e(TAG, "TLS certificate validation failed while retrieving update metadata", e);
                mHandler.obtainMessage(Constants.NETWORK_ERROR, makeTlsNetworkError()).sendToTarget();
            } else {
                LOG.e(TAG, "Unable to retrieve update metadata", e);
                mHandler.sendEmptyMessage(Constants.NETWORK_ERROR);
            }
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
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(!authentication.hasCredentials());

            if (this.authentication.hasCredentials()) {
                conn.setRequestProperty("Authorization", this.authentication.getEncodedAuthorization());
            }

            conn.setDoInput(true);
            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                throw new FileNotFoundException(path);
            }
            if (status < HttpURLConnection.HTTP_OK || status >= HttpURLConnection.HTTP_MULT_CHOICE) {
                throw new IOException("Unexpected HTTP status " + status);
            }
            return new DisconnectingInputStream(conn);
        } catch (IOException e) {
            if (conn != null) {
                conn.disconnect();
            }
            throw e;
        } catch (RuntimeException e) {
            if (conn != null) {
                conn.disconnect();
            }
            throw e;
        }
    }

    private boolean isTlsCertificateFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SSLHandshakeException
                    || current instanceof SSLPeerUnverifiedException
                    || current instanceof CertPathValidatorException
                    || current instanceof CertificateException) {
                return true;
            }
            if (current instanceof SSLException && current.getMessage() != null) {
                String lowerMessage = current.getMessage().toLowerCase(Locale.US);
                if (lowerMessage.contains("trust anchor")
                        || lowerMessage.contains("certificate")
                        || lowerMessage.contains("certpath")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private JSONObject makeTlsNetworkError() {
        JSONObject error = Utils.makeJSON(Constants.NETWORK_ERROR, "tls certificate validation failed");
        try {
            error.put("type", "tls_certificate_error");
        } catch (Exception ignored) {
        }
        return error;
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

        try {
            new URL(updateData.get("url"));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Invalid APK URL", e);
        }
        return versionCode;
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