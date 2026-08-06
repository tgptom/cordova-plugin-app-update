# cordova-plugin-app-update
App updater for Cordova/PhoneGap

# Install

### Latest published version on npm (with Cordova CLI >= 5.0.0)

`cordova plugin add cordova-plugin-app-update --save`

# Usage

- Simple:
```js
var updateUrl = "https://192.168.0.1/version.xml";
window.AppUpdate.checkAppUpdate(onSuccess, onFail, updateUrl);
```

- Verbose
```js
var appUpdate = cordova.require('cordova-plugin-app-update.AppUpdate');
var updateUrl = "https://192.168.0.1/version.xml";
appUpdate.checkAppUpdate(onSuccess, onFail, updateUrl);
```

- Auth download
```js
appUpdate.checkAppUpdate(onSuccess, onFail, updateUrl, {
    'authType' : 'basic',
    'username' : 'test',
    'password' : 'test'
})
```

- Skip dialog boxes
```js
appUpdate.checkAppUpdate(onSuccess, onFail, updateUrl, {
    'skipPromptDialog' : true,
    'skipProgressDialog' : true
})
```

### versionCode

You can simply get the versionCode from typing those code in `Console`

```js
var versionCode = AppVersion.build
console.log(versionCode)  // 302048
```


versionName | versionCode
------- | ----------------
0.0.1  | 18
0.3.4  | 3048  
3.2.4   | 302048
12.234.221  | 1436218

### server version.xml file
 
```xml
<update>
    <version>302048</version>
    <name>name</name>
    <url>https://192.168.0.1/android.apk</url>
</update>
```

### Compatibility notes

- Recommended for modern Cordova Android projects (`cordova-android` 14.x/15.x).
- APK files are downloaded into app-specific external storage.

### `checkAppUpdate` code

```java
    /**
     * Version comparison
     */
    int VERSION_NEED_UPDATE = 201; // update available
    int VERSION_UP_TO_DATE = 202;  // version is up to date
    int VERSION_UPDATING = 203;    // update is in progress

    /**
     * Version parse errors
     */
    int VERSION_RESOLVE_FAIL = 301; // version-xml file resolve failed
    int VERSION_COMPARE_FAIL = 302; // version-xml file compare failed

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
```
# Languages
`zh` `en` `de` `fr` `pt` `bn` `pl` `it` `es` `ru` `ko`

# Platforms
Android

# License
MIT
