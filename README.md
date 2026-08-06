
[![NPM](https://nodei.co/npm/cordova-plugin-app-update.png?downloads=true&downloadRank=true)](https://nodei.co/npm/cordova-plugin-app-update/)

# cordova-plugin-app-update
App updater for Cordova/PhoneGap

# Install

### Latest published version on npm (with Cordova CLI >= 5.0.0) 

> `"cordova-android": "6.3.0"`

`cordova plugin add cordova-plugin-app-update --save`

# Usage

- Simple:
```js
var updateUrl = "http://192.168.0.1/version.xml";
window.AppUpdate.checkAppUpdate(onSuccess, onFail, updateUrl);
```

- Verbose
```js
var appUpdate = cordova.require('cordova-plugin-app-update.AppUpdate');
var updateUrl = "http://192.168.0.1/version.xml";
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
    <url>http://192.168.0.1/android.apk</url>
</update>
```

### `checkAppUpdate` code

```java
    /**
     * 对比版本号
     */
    int VERSION_NEED_UPDATE = 201; //检查到需要更新； need update
    int VERSION_UP_TO_UPDATE = 202; //软件是不需要更新；version up to date
    int VERSION_UPDATING = 203; //软件正在更新；version is updating

    /**
     * 版本解析错误
     */
    int VERSION_RESOLVE_FAIL = 301; //版本文件解析错误 version-xml file resolve fail
    int VERSION_COMPARE_FAIL = 302; //版本文件对比错误 version-xml file compare fail

    /**
     * 网络错误
     */
    int REMOTE_FILE_NOT_FOUND = 404;
    int NETWORK_ERROR = 405;

    /**
     * 没有相应的方法
     */
    int NO_SUCH_METHOD = 501;

    /**
     * Permissions
     */
    int PERMISSION_DENIED = 601;

    /**
     * 未知错误
     */
    int UNKNOWN_ERROR = 901;
```
# Languages
* 🇨🇳 zh
* 🇺🇸 en 
* 🇩🇪 de 
* 🇫🇷 fr 
* 🇵🇹 pt 
* 🇧🇩 bn 
* 🇵🇱 pl 
* 🇮🇹 it 
* 🇪🇸 es
* 🇷🇺 ru
* 🇰🇷 ko

# Platforms
Android only

# License
MIT

# :snowflake: :beers:

* Please let me know if you have any questions.


