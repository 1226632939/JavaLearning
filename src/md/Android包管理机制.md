**Android包管理机制**
==================

------
##1.PackageManager简介：
他是PMS的管理类，用于想应用程序进程提供一些功能，PackageManager是一个抽象类，他的具体实现类为ApplicationPackageManager,ApplicationPackageManager中的方法会通过IpackageManager与AMS进行进程间通信，因此PackageManager所提供的功能最终是由PMS来实现的，这么设计主要用以是为了避免系统服务PMS直接被访问。PackageManager提供了一些功能，主要有以下几点：
1. 获取一个应用程序的所有信息；
2. 获取四大组件的信息；
3. 查询permission相关信息；
4. 获取包的信息；
5. 安装、卸载APK。
------
##2.PackageInstallerActivity解析：
从功能上来说，PackageInstallerActivity才是应用安装起PackageInstaller真正的入口，PackagerInstallerActivity的onCreate防染如下所示。
> packages/apps/PackageInstaller/src/com/android/packageinstaller/PackageInstallerActivity.java

```java
@Override
protected void onCreate(Bundle icicle) {
    super.onCreate(icicle);
    if (icicle != null) {
        mAllowUnknownSources = icicle.getBoolean(ALLOW_UNKNOWN_SOURCES_KEY);
    }
    mPm = getPackageManager();
    mIpm = AppGlobals.getPackageManager();
    mAppOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
    mInstaller = mPm.getPackageInstaller();
    mUserManager = (UserManager) getSystemService(Context.USER_SERVICE);
    ...
    //根据Uri的Scheme进行预处理
    boolean wasSetUp = processPackageUri(packageUri);//1
    if (!wasSetUp) {
        return;
    }
    bindUi(R.layout.install_confirm, false);
    //判断是否是未知来源的应用，如果开启允许安装未知来源选项则直接初始化安装
    checkIfAllowedAndInitiateInstall();//2
}
```
首先初始化安装所需要的各种对象：

| PackageManager     | 用于向应用程序进程提供一些功能，最终的功能是由PMS来实现的   | 
| :--------:           | :-----:  | 
| IPackageManager    | 一个AIDL的接口，用于和PMS进行进程间通信 |  
| AppOpsManager      |  用于权限动态检测,在Android4.3中被引入  |  
| PackageInstaller   |   提供安装、升级和删除应用程序功能  | 
| UserManager        |   用于多用户管理  | 

注释1处代码如下：`boolean wasSetUp = processPackageUri(packageUri);`
>packages/apps/PackageInstaller/src/com/android/packageinstaller/PackageInstallerActivity.java
```java
    private boolean processPackageUri(final Uri packageUri) {
        mPackageURI = packageUri;
        // 1.得到packageUrl的Scheme协议
        final String scheme = packageUri.getScheme();
        // 2.根据得到的Scheme协议分别对package协议和file协议进行处理
        switch (scheme) {
            case SCHEME_PACKAGE: {
                try {
                     ...
                } break;
            case SCHEME_FILE: {
                // 根据PackageUrl创建一个新的File。注释2处的内部会用PackageParser的parsePackage方法解析这个File
                // 这个File其实是个APK文件
                File sourceFile = new File(packageUri.getPath());
                //得到sourceFile的包信息，Package包含了该APK所有的信息
                PackageParser.Package parsed = PackageUtil.getPackageInfo(this, sourceFile);//2
                if (parsed == null) {
                    Log.w(TAG, "Parse error when parsing manifest. Discontinuing installation");
                    showDialogInner(DLG_PACKAGE_ERROR);
                    setPmResult(PackageManager.INSTALL_FAILED_INVALID_APK);
                    return false;
                }
                // 讲Package根据uid、用户状态信息和PackageManager的配置等变量对包信息Package做进一步处理得到包信息PackageInfo
                mPkgInfo = PackageParser.generatePackageInfo(parsed, null,
                PackageManager.GET_PERMISSIONS, 0, 0, null,
                new PackageUserState());//3
                mAppSnippet = PackageUtil.getAppSnippet(this, mPkgInfo.applicationInfo, sourceFile);
            } break;
            // 如果不是这两个协议，关闭PackageInstallerActivity并return false，
            default: {
                Log.w(TAG, "Unsupported scheme " + scheme);
                setPmResult(PackageManager.INSTALL_FAILED_INVALID_URI);
                finish();
                return false;
            }
        }
        // 回到onCreate方法 继续往下走
        return true;
    }
```

checkIfAllowedAndInitiateInstall方法如下所示:`checkIfAllowedAndInitiateInstall();`
>packages/apps/PackageInstaller/src/com/android/packageinstaller/PackageInstallerActivity.java
```java
private void checkIfAllowedAndInitiateInstall() {
       //判断如果允许安装未知来源或者根据Intent判断得出该APK不是未知来源
       if (mAllowUnknownSources || !isInstallRequestFromUnknownSource(getIntent())) {
           //初始化安装
           initiateInstall();
           return;
       }
       // 如果管理员限制来自未知源的安装, 就弹出提示Dialog或者跳转到设置界面
       if (isUnknownSourcesDisallowed()) {
           if ((mUserManager.getUserRestrictionSource(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
                   Process.myUserHandle()) & UserManager.RESTRICTION_SOURCE_SYSTEM) != 0) {    
               showDialogInner(DLG_UNKNOWN_SOURCES_RESTRICTED_FOR_USER);
               return;
           } else {
               startActivity(new Intent(Settings.ACTION_SHOW_ADMIN_SUPPORT_DETAILS));
               finish();
           }
       } else {
           handleUnknownSources();//3
       }
   }
```
初始化安装：` initiateInstall()`
>packages/apps/PackageInstaller/src/com/android/packageinstaller/PackageInstallerActivity.java
```java
private void initiateInstall() {
      // 得到包名
      String pkgName = mPkgInfo.packageName;
      String[] oldName = mPm.canonicalToCurrentPackageNames(new String[] { pkgName });
      if (oldName != null && oldName.length > 0 && oldName[0] != null) {
          pkgName = oldName[0];
          mPkgInfo.packageName = pkgName;
          mPkgInfo.applicationInfo.packageName = pkgName;
      }
      try {
          //根据包名获取应用程序信息
          mAppInfo = mPm.getApplicationInfo(pkgName,
                  PackageManager.MATCH_UNINSTALLED_PACKAGES);
          if ((mAppInfo.flags&ApplicationInfo.FLAG_INSTALLED) == 0) {
              mAppInfo = null;
          }
      } catch (NameNotFoundException e) {
          mAppInfo = null;
      }
      //初始化安装确认界面
      startInstallConfirm();
  }
```
初始化安装确认界面：`startInstallConfirm()`
```java
private void startInstallConfirm() {
    // startInstallConfirm方法中首先初始化安装确认界面，就是我们平常安装APK时出现的界面，界面上有确认和取消按钮并会列出安装该APK需要访问的系统权限。
    // 省略初始化界面代码
     ...
     // 创建AppSecurityPermissions,他会提取APK中权限信息并展示出来,
     // 这个负责展示的View是AppSecurityPermissions的内部类PermissionItemView。
     AppSecurityPermissions perms = new AppSecurityPermissions(this, mPkgInfo);
     final int N = perms.getPermissionCount(AppSecurityPermissions.WHICH_ALL);
     if (mAppInfo != null) {
         msg = (mAppInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                 ? R.string.install_confirm_question_update_system
                 : R.string.install_confirm_question_update;
         mScrollView = new CaffeinatedScrollView(this);
         mScrollView.setFillViewport(true);
         boolean newPermissionsFound = false;
         if (!supportsRuntimePermissions) {
             newPermissionsFound =
                     (perms.getPermissionCount(AppSecurityPermissions.WHICH_NEW) > 0);
             if (newPermissionsFound) {
                 permVisible = true;
                // 调用AppSecurityPermissions的getPermissionsView方法来获取PermissionItemView，
                // 并将PermissionItemView添加到CaffeinatedScrollView中，
                // 这样安装该APK需要访问的系统权限就可以全部的展示出来了，
                // PackageInstaller的初始化工作就完成了。
                 mScrollView.addView(perms.getPermissionsView(
                         AppSecurityPermissions.WHICH_NEW));//2
             }
         }
     ...
 }
```
#### 总结：
1. 根据Url的Scheme协议不同，跳转不同的界面，conten协议跳转到InstallStart，其他的跳转到PackageInstallerActivity。如果是Android N及更高版本会条钻到InstallStart。
2. installStart讲content协议的Url转化为File协议，然后跳转到PackageInstallerActivity。
3. PackageInstallerActivity会分别对package协议和File的Url进行处理，如果file协议会解析APK文件得到包信息PackageInfo。
4. packageInstallerActivty中会对位置来源进行处理，如果允许安装位置来源活根据Intent判断得出该APK不是未知来源，就会初始化安装界面，如果管理员限制来自未知的安装，就会弹出提示Dialog或者跳转到设置界面。
------
##3.PackageInstaller中的处理：
在PackageInstallerActivity调用startInstallConfirm方法初始化安装界面后，这个安装确认界面就会呈现给用户，用户如果想要安装这个应用程序就会点击确定按钮，就会调用PackageInstallerActivity的onClick方法，如下所示：
>packages/apps/PackageInstaller/src/com/android/packageinstaller/PackageInstallerActivity.java
```java

public void onClick(View v) {
        if (v == mOk) {
            if (mOk.isEnabled()) {
                if (mOkCanInstall || mScrollView == null) {
                    if (mSessionId != -1) {
                        mInstaller.setPermissionsResult(mSessionId, true);
                        finish();
                    } else {
                        // startInstall方法用于跳转到InstallInstalling这个Activity，并关闭掉当前的PackageInstallerActivity。
                        startInstall();
                    }
                } else {
                    mScrollView.pageScroll(View.FOCUS_DOWN);
                }
            }
        } else if (v == mCancel) {
            ...
            finish();
        }
    }
```