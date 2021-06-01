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
startInstall方法用于跳转到InstallInstalling这个Activity，并关闭掉当前的PackageInstallerActivity。startInstall方法:`startInstall();`
>packages/apps/PackageInstaller/src/com/android/packageinstaller/PackageInstallerActivity.java
```java
private void startInstall() {
     Intent newIntent = new Intent();
     newIntent.putExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO,
             mPkgInfo.applicationInfo);
     newIntent.setData(mPackageURI);
     // InstallInstalling主要用于向包管理器发送包的信息并处理包管理的回调
     newIntent.setClass(this, InstallInstalling.class);
     String installerPackageName = getIntent().getStringExtra(
             Intent.EXTRA_INSTALLER_PACKAGE_NAME);
     if (mOriginatingURI != null) {
         newIntent.putExtra(Intent.EXTRA_ORIGINATING_URI, mOriginatingURI);
     }
     ...
     if(localLOGV) Log.i(TAG, "downloaded app uri="+mPackageURI);
     startActivity(newIntent);
     finish();
 }
```
InstallINstalling的onCreate方法中会分别对package和content协议的Uri进行处理。InstallInstalling的onCreate方法：
>packages/apps/PackageInstaller/src/com/android/packageinstaller/InstallInstalling.java
```java
@Override
 protected void onCreate(@Nullable Bundle savedInstanceState) {
     super.onCreate(savedInstanceState);
     setContentView(R.layout.install_installing);
     ApplicationInfo appInfo = getIntent()
             .getParcelableExtra(PackageUtil.INTENT_ATTR_APPLICATION_INFO);
     mPackageURI = getIntent().getData();
     if ("package".equals(mPackageURI.getScheme())) {
         try {
             getPackageManager().installExistingPackage(appInfo.packageName);
             launchSuccess();
         } catch (PackageManager.NameNotFoundException e) {
             launchFailure(PackageManager.INSTALL_FAILED_INTERNAL_ERROR, null);
         }
     } else {
     // 根据mPackageURI创建一个对应的File 
         final File sourceFile = new File(mPackageURI.getPath());
         PackageUtil.initSnippetForNewApp(this, PackageUtil.getAppSnippet(this, appInfo,
                 sourceFile), R.id.app_snippet);
         // 如果savedInstanceState不为null，获取此前保存的mSessionId和mInstallId
         // 其中mSessionld是安装包的绘画id，mInstallId是等待安装事件id；
         if (savedInstanceState != null) {
             mSessionId = savedInstanceState.getInt(SESSION_ID);
             mInstallId = savedInstanceState.getInt(INSTALL_ID);
            // 向InstallEventReceiver注册一个观察者
            // launchFinishBasedOnResult会接收到安装事件的回调，无论安装成功或者失败都会关闭当前的Activity(InstallInstalling)。
            // 如果savedInstanceState为null，代码的逻辑也是类似的，注释3处创建SessionParams，它用来代表安装会话的参数。
             try {
                 InstallEventReceiver.addObserver(this, mInstallId,
                         this::launchFinishBasedOnResult);
             } catch (EventResultPersister.OutOfIdsException e) {
      
             }
         } else {
             PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                     PackageInstaller.SessionParams.MODE_FULL_INSTALL);//3
             params.referrerUri = getIntent().getParcelableExtra(Intent.EXTRA_REFERRER);
             params.originatingUri = getIntent()
                     .getParcelableExtra(Intent.EXTRA_ORIGINATING_URI);
             params.originatingUid = getIntent().getIntExtra(Intent.EXTRA_ORIGINATING_UID,
                     UID_UNKNOWN);
             // 根据mPackageUri对包（APK）进行轻量级的解析，并将解析的参数赋值给SessionParams。
             File file = new File(mPackageURI.getPath());
             try {
                 PackageParser.PackageLite pkg = PackageParser.parsePackageLite(file, 0);
                 params.setAppPackageName(pkg.packageName);
                 params.setInstallLocation(pkg.installLocation);
                 params.setSize(
                         PackageHelper.calculateInstalledSize(pkg, false, params.abiOverride));
             } catch (PackageParser.PackageParserException e) {
                ...
             }
             try {
                // 向InstallEventReceiver注册一个观察者返回一个新的mInstallId，其中InstallEventReceiver继承自BroadcastReceiver，
                // 用于接收安装事件并回调给EventResultPersister。
                 mInstallId = InstallEventReceiver
                         .addObserver(this, EventResultPersister.GENERATE_NEW_ID,
                                 this::launchFinishBasedOnResult);//6
             } catch (EventResultPersister.OutOfIdsException e) {
                 launchFailure(PackageManager.INSTALL_FAILED_INTERNAL_ERROR, null);
             }
             try {
                 // PackageInstaller的createSession方法内部会通过IPackageInstaller与PackageInstallerService进行进程间通信，
                 // 最终调用的是PackageInstallerService的createSession方法来创建并返回mSessionId。
                 mSessionId = getPackageManager().getPackageInstaller().createSession(params);//7
             } catch (IOException e) {
                 launchFailure(PackageManager.INSTALL_FAILED_INTERNAL_ERROR, null);
             }
         }
          ...
         mSessionCallback = new InstallSessionCallback();
     }
 }
```
InstallInstalling的onResume方法：
>packages/apps/PackageInstaller/src/com/android/packageinstaller/InstallInstalling.java
```java
@Override
 protected void onResume() {
     super.onResume();
     if (mInstallingTask == null) {
         PackageInstaller installer = getPackageManager().getPackageInstaller();
         // 处根据mSessionId得到SessionInfo，SessionInfo代表安装会话的详细信息。
         PackageInstaller.SessionInfo sessionInfo = installer.getSessionInfo(mSessionId);
         // 如果sessionInfo不为Null并且不是活动的，就创建并执行InstallingAsyncTask。
         if (sessionInfo != null && !sessionInfo.isActive()) {//2
             mInstallingTask = new InstallingAsyncTask();
             mInstallingTask.execute();
         } else {
             mCancelButton.setEnabled(false);
             setFinishOnTouchOutside(false);
         }
     }
 }
```
InstallingAsyncTask的doInBackground方法中会根据包(APK)的Uri，将APK的信息通过IO流的形式写入到PackageInstaller.Session中。InstallingAsyncTask的onPostExecute方法:
>packages/apps/PackageInstaller/src/com/android/packageinstaller/InstallInstalling.java
```java
@Override
 protected void onPostExecute(PackageInstaller.Session session) {
     if (session != null) {
         Intent broadcastIntent = new Intent(BROADCAST_ACTION);
         broadcastIntent.setPackage(
                 getPackageManager().getPermissionControllerPackageName());
         broadcastIntent.putExtra(EventResultPersister.EXTRA_ID, mInstallId);
         // 创建了一个PendingIntent
         PendingIntent pendingIntent = PendingIntent.getBroadcast(
                 InstallInstalling.this,
                 mInstallId,
                 broadcastIntent,
                 PendingIntent.FLAG_UPDATE_CURRENT);
         // 将PendingIntent的IntentSender通过PackageInstaller.Session的commit方法发送出去
         session.commit(pendingIntent.getIntentSender());//1
         mCancelButton.setEnabled(false);
         setFinishOnTouchOutside(false);
     } else {
         getPackageManager().getPackageInstaller().abandonSession(mSessionId);
         if (!isCancelled()) {
             launchFailure(PackageManager.INSTALL_FAILED_INVALID_APK, null);
         }
     }
 }
```
mSession的类型为IPackageInstallerSession，这说明要通过IPackageInstallerSession来进行进程间的通信，最终会调用PackageInstallerSession的commit方法，这样代码逻辑就到了Java框架层的。PackageInstaller.Session的commit方法:
>frameworks/base/core/java/android/content/pm/PackageInstaller.java
```java
public void commit(@NonNull IntentSender statusReceiver) {
           try {
               mSession.commit(statusReceiver);
           } catch (RemoteException e) {
               throw e.rethrowFromSystemServer();
           }
       }
```
#### 总结：
1. 将APK的信息通过IO流的形式写入到PackageInstaller.Session中。
##4.Java框架层的处理
commit方法中会将包的信息封装为PackageInstallObserverAdapter ，它在PMS中被定义。
>frameworks/base/services/core/java/com/android/server/pm/PackageInstallerSession.java
```java
@Override
   public void commit(IntentSender statusReceiver) {
       Preconditions.checkNotNull(statusReceiver);
       ...
       mActiveCount.incrementAndGet();
       final PackageInstallObserverAdapter adapter = new PackageInstallObserverAdapter(mContext,
               statusReceiver, sessionId, mIsInstallerDeviceOwner, userId);
       // 向Handler发送一个类型为MSG_COMMIT的消息，其中adapter.getBinder()会得到IPackageInstallObserver2.Stub类型的观察者，从类型就知道这个观察者是可以跨进程进行回调的。
       mHandler.obtainMessage(MSG_COMMIT, adapter.getBinder()).sendToTarget();
   }
```
mHandler处理消息：
>frameworks/base/services/core/java/com/android/server/pm/PackageInstallerSession.java
```java
private final Handler.Callback mHandlerCallback = new Handler.Callback() {
      @Override
      public boolean handleMessage(Message msg) {
          final PackageInfo pkgInfo = mPm.getPackageInfo(
                  params.appPackageName, PackageManager.GET_SIGNATURES
                          | PackageManager.MATCH_STATIC_SHARED_LIBRARIES /*flags*/, userId);
          final ApplicationInfo appInfo = mPm.getApplicationInfo(
                  params.appPackageName, 0, userId);
          synchronized (mLock) {
              if (msg.obj != null) {
                  // 获取IPackageInstallObserver2类型的观察者mRemoteObserver，
                  mRemoteObserver = (IPackageInstallObserver2) msg.obj;
              }
              try {
                  // 截取最主要的信息，把代码逻辑带到PMS中
                  commitLocked(pkgInfo, appInfo);
              } catch (PackageManagerException e) {
                  final String completeMsg = ExceptionUtils.getCompleteMessage(e);
                  Slog.e(TAG, "Commit of session " + sessionId + " failed: " + completeMsg);
                  destroyInternal();
                  // 如果commitLocked方法出现PackageManagerException异常
                  dispatchSessionFinished(e.error, completeMsg, null);
              }
              return true;
          }
      }
  };
```
commitLocked这里截取最主要的信息，调用PMS的installStage方法，将代码逻辑带入了PMS中。
>frameworks/base/services/core/java/com/android/server/pm/PackageInstallerSession.java
```java
private void commitLocked(PackageInfo pkgInfo, ApplicationInfo appInfo)
          throws PackageManagerException {
     ...
      mPm.installStage(mPackageName, stageDir, stageCid, localObserver, params,
              installerPackageName, installerUid, user, mCertificates);
  }
```
如果commitLocked方法出现PackageManagerException异常，就会调用dispatchSessionFinished方法。
>frameworks/base/services/core/java/com/android/server/pm/PackageInstallerSession.java
```java
private void dispatchSessionFinished(int returnCode, String msg, Bundle extras) {
       mFinalStatus = returnCode;
       mFinalMessage = msg;
       if (mRemoteObserver != null) {
           try {
               // 调用IPackageInstallObserver2的onPackageInstalled方法
               mRemoteObserver.onPackageInstalled(mPackageName, returnCode, msg, extras);
           } catch (RemoteException ignored) {
           }
       }
       ...
   }
```
onPackageInstalled方法:
>frameworks/base/core/java/android/app/PackageInstallObserver.java
```java
public class PackageInstallObserver {
    private final IPackageInstallObserver2.Stub mBinder = new IPackageInstallObserver2.Stub() {
        ...
        @Override
        public void onPackageInstalled(String basePackageName, int returnCode,
                String msg, Bundle extras) {
            //  调用PackageInstallObserver的onPackageInstalled方法，实现这个方法的类为PackageInstallObserver的子类、前面提到的PackageInstallObserverAdapter。
            PackageInstallObserver.this.onPackageInstalled(basePackageName, returnCode, msg,
                    extras);
        }
    };
```
#### 总结：
1. 调用PackageInstaller.Session的commit方法，将APK的信息交由PMS处理。

##5.PackageHandler处理安装消息
APK的信息交由PMS后，PMS通过向PackageHandler发送消息来驱动APK的复制和安装工作。
![](res/drawble/PMS.png)