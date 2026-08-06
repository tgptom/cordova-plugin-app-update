package com.vaenow.appupdate.android;

import android.Manifest;
import android.os.Build;
import android.net.Uri;
import android.provider.Settings;
import android.content.Intent;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.BuildHelper;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by LuoWen on 2015/10/27.
 */
public class CheckAppUpdate extends CordovaPlugin {
    public static final String TAG = "CheckAppUpdate";

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (action.equals("checkAppUpdate")) {
            if (!getUpdateManager().options(args, callbackContext)) {
                return true;
            }
            getUpdateManager().checkUpdate();
            return true;
        }

        callbackContext.error(Utils.makeJSON(Constants.NO_SUCH_METHOD, "No such method: " + action));
        return false;
    }

    //////////
    // Update Manager
    //////////

    // UpdateManager singleton
    private UpdateManager updateManager = null;

    // Generate or retrieve the UpdateManager singleton
    public UpdateManager getUpdateManager() {
        if (updateManager == null)
            updateManager = new UpdateManager(cordova.getActivity(), cordova,
                    new UpdateManager.InstallPermissionRequester() {
                        @Override
                        public void runWithInstallPermission(Runnable action) {
                            requestInstallPermission(action);
                        }
                    });

        return updateManager;
    }

    //////////
    // Permissions
    //////////

    private static final int INSTALL_PERMISSION_REQUEST_CODE = 0;
    private final AtomicReference<Runnable> pendingInstallAction = new AtomicReference<Runnable>();

    // Prompt user for install permission if we don't already have it.
    private void requestInstallPermission(Runnable action) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!cordova.getActivity().getPackageManager().canRequestPackageInstalls()) {
                String applicationId = (String) BuildHelper.getBuildConfigValue(cordova.getActivity(), "APPLICATION_ID");
                Uri packageUri = Uri.parse("package:" + applicationId);
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .setData(packageUri);
                if (!pendingInstallAction.compareAndSet(null, action)) {
                    getUpdateManager().permissionDenied("An install permission request is already in progress");
                    return;
                }
                cordova.setActivityResultCallback(this);
                cordova.getActivity().startActivityForResult(intent, INSTALL_PERMISSION_REQUEST_CODE);
                return;
            }
        }
        action.run();
    }

    // React to user's response to our request for install permission.
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == INSTALL_PERMISSION_REQUEST_CODE) {
            if (!cordova.getActivity().getPackageManager().canRequestPackageInstalls()) {
                pendingInstallAction.set(null);
                getUpdateManager().permissionDenied("Permission Denied: " + Manifest.permission.REQUEST_INSTALL_PACKAGES);
                return;
            }

            Runnable action = pendingInstallAction.getAndSet(null);
            if (action != null) {
                action.run();
            }
        }
    }
}
