**Android包管理机制**
==================

------
1.PackageManager简介：
---
他是PMS的管理类，用于想应用程序进程提供一些功能，PackageManager是一个抽象类，他的具体实现类为ApplicationPackageManager,ApplicationPackageManager中的方法会通过IpackageManager与AMS进行进程间通信，因此PackageManager所提供的功能最终是由PMS来实现的，这么设计主要用以是为了避免系统服务PMS直接被访问。PackageManager提供了一些功能，主要有以下几点：
1. 获取一个应用程序的所有信息；
2. 获取四大组件的信息；
3. 查询permission相关信息；
4. 获取包的信息；
5. 安装、卸载APK。
------
2.PackageInstallerActivity解析：
---
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
3.PackageInstaller中的处理：
---
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

4.Java框架层的处理
---
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

5.PackageHandler处理安装消息
---
APK的信息交由PMS后，PMS通过向PackageHandler发送消息来驱动APK的复制和安装工作。
![PackageHandler 处理安装消息的调用时序图](https://github.com/1226632939/JavaLearning/blob/master/src/res/drawble/PMS.png)

PMS的installStage方法：
>frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java
```java
void installStage(String packageName, File stagedDir, String stagedCid,
           IPackageInstallObserver2 observer, PackageInstaller.SessionParams sessionParams,
           String installerPackageName, int installerUid, UserHandle user,
           Certificate[][] certificates) {
       ...
       // 创建了类型为INIT_COPY的消息
       final Message msg = mHandler.obtainMessage(INIT_COPY);
       final int installReason = fixUpInstallReason(installerPackageName, installerUid,
               sessionParams.installReason);
       // 创建InstallParams，对应于包的安装数据
       final InstallParams params = new InstallParams(origin, null, observer,
               sessionParams.installFlags, installerPackageName, sessionParams.volumeUuid,
               verificationInfo, user, sessionParams.abiOverride,
               sessionParams.grantedRuntimePermissions, certificates, installReason);
       params.setTraceMethod("installStage").setTraceCookie(System.identityHashCode(params));
       msg.obj = params;
       ...
       // 将InstallParams通过消息发送出去。
       mHandler.sendMessage(msg);
   }
```
###5.1对INIT_COPY的消息的处理
PackageHandler继承自Handler,它被定义在PMS中，doHandleMessage方法用于处理各个类型的消息，来查看对INIT_COPY类型消息的处理。处理INIT_COPY类型的消息的代码如下所示。
>frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java#PackageHandler
```java
void doHandleMessage(Message msg) {
           switch (msg.what) {
               case INIT_COPY: {
                   HandlerParams params = (HandlerParams) msg.obj;
                   int idx = mPendingInstalls.size();
                   if (DEBUG_INSTALL) Slog.i(TAG, "init_copy idx=" + idx + ": " + params);
                   //mBound用于标识是否绑定了DefaultContainerService服务，默认值为false
                   if (!mBound) {
                       Trace.asyncTraceBegin(TRACE_TAG_PACKAGE_MANAGER, "bindingMCS",
                               System.identityHashCode(mHandler));
                       //如果没有绑定服务，重新绑定，connectToService方法内部如果绑定成功会将mBound置为true
                       if (!connectToService()) {//2
                           Slog.e(TAG, "Failed to bind to media container service");
                           params.serviceError();
                           Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, "bindingMCS",
                                   System.identityHashCode(mHandler));
                           if (params.traceMethod != null) {
                               Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, params.traceMethod,
                                       params.traceCookie);
                           }
                           //绑定服务失败则return
                           return;
                       } else {
                           //绑定服务成功，将请求添加到ArrayList类型的mPendingInstalls中，等待处理
                           mPendingInstalls.add(idx, params);
                       }
                   } else {
                   //已经绑定服务
                       mPendingInstalls.add(idx, params);
                       if (idx == 0) {
                           mHandler.sendEmptyMessage(MCS_BOUND);//3
                       }
                   }
                   break;
               }
               ....
               }
   }
}
```
DefaultContainerService是用于检查和复制可移动文件的服务，这是一个比较耗时的操作，因此DefaultContainerService没有和PMS运行在同一进程中，它运行在com.android.defcontainer进程，通过IMediaContainerService和PMS进行IPC通信，如下图所示。

![](https://github.com/1226632939/JavaLearning/blob/master/src/res/drawble/IMediaContainerService.png)

connectToService方法：
>**frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java#PackageHandler **
```java
private boolean connectToService() {
          if (DEBUG_SD_INSTALL) Log.i(TAG, "Trying to bind to" +
                  " DefaultContainerService");
          Intent service = new Intent().setComponent(DEFAULT_CONTAINER_COMPONENT);
          Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
          // bindServiceAsUser方法会传入mDefContainerConn，bindServiceAsUser方法的处理逻辑和我们调用bindService是类似的，服务建立连接后，会调用onServiceConnected方法
          if (mContext.bindServiceAsUser(service, mDefContainerConn,
                  Context.BIND_AUTO_CREATE, UserHandle.SYSTEM)) {
              Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
              // 如果绑定DefaultContainerService成功，mBound会置为ture 。
              mBound = true;
              return true;
          }
          Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
          return false;
      }
```
onServiceConnected方法：
>frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java
```java
class DefaultContainerConnection implements ServiceConnection {
      public void onServiceConnected(ComponentName name, IBinder service) {
          if (DEBUG_SD_INSTALL) Log.i(TAG, "onServiceConnected");
          final IMediaContainerService imcs = IMediaContainerService.Stub
                  .asInterface(Binder.allowBlocking(service));
          // 发送了MCS_BOUND类型的消息
          mHandler.sendMessage(mHandler.obtainMessage(MCS_BOUND, Object));
      }
      public void onServiceDisconnected(ComponentName name) {
          if (DEBUG_SD_INSTALL) Log.i(TAG, "onServiceDisconnected");
      }
  }
```
###5.2对MCS_BOUND类型的消息的处理
查看对MCS_BOUND类型消息的处理：
>ameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java
```java
case MCS_BOUND: {
            if (DEBUG_INSTALL) Slog.i(TAG, "mcs_bound");
            if (msg.obj != null) {
                mContainerService = (IMediaContainerService) msg.obj;
                Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, "bindingMCS",
                        System.identityHashCode(mHandler));
            }
            if (mContainerService == null) {
                // 如果满足条件，说明还没有绑定服务，
                if (!mBound) {
                      Slog.e(TAG, "Cannot bind to media container service");
                      for (HandlerParams params : mPendingInstalls) {
                          // 负责处理服务发生错误的情况
                          params.serviceError();
                          Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, "queueInstall",
                                        System.identityHashCode(params));
                          if (params.traceMethod != null) {
                          Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER,
                           params.traceMethod, params.traceCookie);
                          }
                          return;
                      }   
                          //绑定失败，清空安装请求队列
                          mPendingInstalls.clear();
                   } else {
                          //继续等待绑定服务
                          Slog.w(TAG, "Waiting to connect to media container service");
                   }
            } else if (mPendingInstalls.size() > 0) {
              ...
              else {
                   Slog.w(TAG, "Empty queue");
                   }
            break;
        }
```
消息带Object类型的参数:\
如果MCS_BOUND类型消息带Object类型的参数就不会满足注释1处的条件，就会调用注释2处的判断，如果安装请求数不大于0就会打印出注释6处的log，说明安装请求队列是空的。安装完一个APK后，就会在注释5处发出MSC_BOUND消息，继续处理剩下的安装请求直到安装请求队列为空。
注释3处得到安装请求队列第一个请求HandlerParams ，如果HandlerParams 不为null就会调用注释4处的HandlerParams的startCopy方法，用于开始复制APK的流程。
>frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java
```java
case MCS_BOUND: {
            if (DEBUG_INSTALL) Slog.i(TAG, "mcs_bound");
            if (msg.obj != null) {
            ...
            }
            if (mContainerService == null) {//1
             ...
            } else if (mPendingInstalls.size() > 0) {//2
                          HandlerParams params = mPendingInstalls.get(0);//3
                        if (params != null) {
                            Trace.asyncTraceEnd(TRACE_TAG_PACKAGE_MANAGER, "queueInstall",
                                    System.identityHashCode(params));
                            Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "startCopy");
                            if (params.startCopy()) {//4
                                if (DEBUG_SD_INSTALL) Log.i(TAG,
                                        "Checking for more work or unbind...");
                                 //如果APK安装成功，删除本次安装请求
                                if (mPendingInstalls.size() > 0) {
                                    mPendingInstalls.remove(0);
                                }
                                if (mPendingInstalls.size() == 0) {
                                    if (mBound) {
                                    //如果没有安装请求了，发送解绑服务的请求
                                        if (DEBUG_SD_INSTALL) Log.i(TAG,
                                                "Posting delayed MCS_UNBIND");
                                        removeMessages(MCS_UNBIND);
                                        Message ubmsg = obtainMessage(MCS_UNBIND);
                                        sendMessageDelayed(ubmsg, 10000);
                                    }
                                } else {
                                    if (DEBUG_SD_INSTALL) Log.i(TAG,
                                            "Posting MCS_BOUND for next work");
                                   //如果还有其他的安装请求，接着发送MCS_BOUND消息继续处理剩余的安装请求       
                                    mHandler.sendEmptyMessage(MCS_BOUND);//5
                                }
                            }
                            Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
                        }else {
                        Slog.w(TAG, "Empty queue");//6
                    }
            break;
        }
```
####总结：
1. PackageInstaller安装APK时会将APK的信息交由PMS处理，PMS通过向PackageHandler发送消息来驱动APK的复制和安装工作。
------------------------------

6.复制apk
---
![复制APK的时序图](https://github.com/1226632939/JavaLearning/blob/master/src/res/drawble/copyApk.png)

HandlerParams是PMS中的抽象类，它的实现类为PMS内部的额类InstallParams。HandlerParams的StartCopy方法如下：
>frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java#HandlerParams
```java
final boolean startCopy() {
           boolean res;
           try {
               if (DEBUG_INSTALL) Slog.i(TAG, "startCopy " + mUser + ": " + this);
               //startCopy方法尝试的次数，超过了4次，就放弃这个安装请求
               if (++mRetries > MAX_RETRIES) {//1
                   Slog.w(TAG, "Failed to invoke remote methods on default container service. Giving up");
                   mHandler.sendEmptyMessage(MCS_GIVE_UP);//2
                   handleServiceError();
                   return false;
               } else {
                   handleStartCopy();//3
                   res = true;
               }
           } catch (RemoteException e) {
               if (DEBUG_INSTALL) Slog.i(TAG, "Posting install MCS_RECONNECT");
               mHandler.sendEmptyMessage(MCS_RECONNECT);
               res = false;
           }
           handleReturnCode();//4
           return res;
       }
```
注释1处的mRetries用于记录startCopy方法调用的次数，调用startCopy方法时会先自动加1，如果次数大于4次就放弃这个安装请求：在注释2处发送MCS_GIVE_UP类型消息，将第一个安装请求（本次安装请求）从安装请求队列mPendingInstalls中移除掉。注释4处用于处理复制APK后的安装APK逻辑，第3小节中会再次提到它。注释3处调用了抽象方法handleStartCopy，它的实现在InstallParams中，如下所示。
>frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java#InstallParams
```java
public void handleStartCopy() throws RemoteException {
       ...
       //确定APK的安装位置。onSd：安装到SD卡， onInt：内部存储即Data分区，ephemeral：安装到临时存储（Instant Apps安装）            
       final boolean onSd = (installFlags & PackageManager.INSTALL_EXTERNAL) != 0;
       final boolean onInt = (installFlags & PackageManager.INSTALL_INTERNAL) != 0;
       final boolean ephemeral = (installFlags & PackageManager.INSTALL_INSTANT_APP) != 0;
       PackageInfoLite pkgLite = null;
       if (onInt && onSd) {
         // APK不能同时安装在SD卡和Data分区
           Slog.w(TAG, "Conflicting flags specified for installing on both internal and external");
           ret = PackageManager.INSTALL_FAILED_INVALID_INSTALL_LOCATION;
         //安装标志冲突，Instant Apps不能安装到SD卡中
       } else if (onSd && ephemeral) {
           Slog.w(TAG,  "Conflicting flags specified for installing ephemeral on external");
           ret = PackageManager.INSTALL_FAILED_INVALID_INSTALL_LOCATION;
       } else {
            //获取APK的少量的信息
           pkgLite = mContainerService.getMinimalPackageInfo(origin.resolvedPath, installFlags,
                   packageAbiOverride);//1
           if (DEBUG_EPHEMERAL && ephemeral) {
               Slog.v(TAG, "pkgLite for install: " + pkgLite);
           }
       ...
       if (ret == PackageManager.INSTALL_SUCCEEDED) {
            //判断安装的位置
           int loc = pkgLite.recommendedInstallLocation;
           if (loc == PackageHelper.RECOMMEND_FAILED_INVALID_LOCATION) {
               ret = PackageManager.INSTALL_FAILED_INVALID_INSTALL_LOCATION;
           } else if (loc == PackageHelper.RECOMMEND_FAILED_ALREADY_EXISTS) {
               ret = PackageManager.INSTALL_FAILED_ALREADY_EXISTS;
           } 
           ...
           }else{
             loc = installLocationPolicy(pkgLite);//2
             ...
           }
       }
       //根据InstallParams创建InstallArgs对象
       final InstallArgs args = createInstallArgs(this);//3
       mArgs = args;
       if (ret == PackageManager.INSTALL_SUCCEEDED) {
              ...
           if (!origin.existing && requiredUid != -1
                   && isVerificationEnabled(
                         verifierUser.getIdentifier(), installFlags, installerUid)) {
                 ...
           } else{
               ret = args.copyApk(mContainerService, true);//4
           }
       }
       mRet = ret;
   }
```
handleStartCopy方法的代码很多，这里截取关键的部分。
注释1处通过IMediaContainerService跨进程调用DefaultContainerService的getMinimalPackageInfo方法，该方法轻量解析APK并得到APK的少量信息，轻量解析的原因是这里不需要得到APK的全部信息，APK的少量信息会封装到PackageInfoLite中。接着在注释2处确定APK的安装位置。注释3处创建了InstallArgs，InstallArgs 是一个抽象类，定义了APK的安装逻辑，比如复制和重命名APK等，它有3个子类，都被定义在PMS中，如下图所示。

![](https://github.com/1226632939/JavaLearning/blob/master/src/res/drawble/InstallArgs.png)

其中FileInstallArgs用于处理安装到非ASEC的存储空间的APK，也就是内部存储空间（Data分区），AsecInstallArgs用于处理安装到ASEC中（mnt/asec）即SD卡中的APK。MoveInstallArgs用于处理已安装APK的移动的逻辑。\
对APK进行检查后就会在注释4处调用InstallArgs的copyApk方法进行安装。\
不同的InstallArgs子类会有着不同的处理，这里以FileInstallArgs为例。FileInstallArgs的copyApk方法中会直接return FileInstallArgs的doCopyApk方法：
>frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java#FileInstallArgs
```java

private int doCopyApk(IMediaContainerService imcs, boolean temp) throws RemoteException {
        ...
         try {
             final boolean isEphemeral = (installFlags & PackageManager.INSTALL_INSTANT_APP) != 0;
             //创建临时文件存储目录
             final File tempDir =
                     mInstallerService.allocateStageDirLegacy(volumeUuid, isEphemeral);//1
             codeFile = tempDir;
             resourceFile = tempDir;
         } catch (IOException e) {
             Slog.w(TAG, "Failed to create copy file: " + e);
             return PackageManager.INSTALL_FAILED_INSUFFICIENT_STORAGE;
         }
         ...
         int ret = PackageManager.INSTALL_SUCCEEDED;
         ret = imcs.copyPackage(origin.file.getAbsolutePath(), target);//2
         ...
         return ret;
     }
```
注释1处用于创建临时存储目录，比如/data/app/vmdl18300388.tmp，其中18300388是安装的sessionId。注释2处通过IMediaContainerService跨进程调用DefaultContainerService的copyPackage方法，这个方法会在DefaultContainerService所在的进程中将APK复制到临时存储目录，比如/data/app/vmdl18300388.tmp/base.apk。目前为止APK的复制工作就完成了，接着就是APK的安装过程了。

-----------------

7.安装APK
---

![安装APK的时序图](https://github.com/1226632939/JavaLearning/blob/master/src/res/drawble/InstallationAPK.png)

回到APK的复制调用链的头部方法：HandlerParams的startCopy方法，在注释4处会调用handleReturnCode方法，它的实现在InstallParams中，如下所示:
>frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java
```java
void handleReturnCode() {
    if (mArgs != null) {
        processPendingInstall(mArgs, mRet);
    }
}

    private void processPendingInstall(final InstallArgs args, final int currentStatus) {
        mHandler.post(new Runnable() {
            public void run() {
                mHandler.removeCallbacks(this);
                PackageInstalledInfo res = new PackageInstalledInfo();
                res.setReturnCode(currentStatus);
                res.uid = -1;
                res.pkg = null;
                res.removedInfo = null;
                if (res.returnCode == PackageManager.INSTALL_SUCCEEDED) {
                    //安装前处理
                    args.doPreInstall(res.returnCode);//1
                    synchronized (mInstallLock) {
                        installPackageTracedLI(args, res);//2
                    }
                    //安装后收尾
                    args.doPostInstall(res.returnCode, res.uid);//3
                }
              ...
            }
        });
    }
```
handleReturnCode方法中只调用了processPendingInstall方法，注释1处用于检查APK的状态的，在安装前确保安装环境的可靠，如果不可靠会清除复制的APK文件，注释3处用于处理安装后的收尾操作，如果安装不成功，删除掉安装相关的目录与文件。主要来看注释2处的installPackageTracedLI方法，其内部会调用PMS的installPackageLI方法。
>frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java
```java
private void installPackageLI(InstallArgs args, PackageInstalledInfo res) {
    ...
    PackageParser pp = new PackageParser();
    pp.setSeparateProcesses(mSeparateProcesses);
    pp.setDisplayMetrics(mMetrics);
    pp.setCallback(mPackageParserCallback);
    Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "parsePackage");
    final PackageParser.Package pkg;
    try {
        //解析APK
        pkg = pp.parsePackage(tmpPackageFile, parseFlags);//1
    } catch (PackageParserException e) {
        res.setError("Failed parse during installPackageLI", e);
        return;
    } finally {
        Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
    }
    ...
    pp = null;
    String oldCodePath = null;
    boolean systemApp = false;
    synchronized (mPackages) {
        // 检查APK是否存在
        if ((installFlags & PackageManager.INSTALL_REPLACE_EXISTING) != 0) {
            String oldName = mSettings.getRenamedPackageLPr(pkgName);//获取没被改名前的包名
            if (pkg.mOriginalPackages != null
                    && pkg.mOriginalPackages.contains(oldName)
                    && mPackages.containsKey(oldName)) {
                pkg.setPackageName(oldName);//2
                pkgName = pkg.packageName;
                replace = true;//设置标志位表示是替换安装
                if (DEBUG_INSTALL) Slog.d(TAG, "Replacing existing renamed package: oldName="
                        + oldName + " pkgName=" + pkgName);
            } 
            ...
        }
        PackageSetting ps = mSettings.mPackages.get(pkgName);
        //查看Settings中是否存有要安装的APK的信息，如果有就获取签名信息
        if (ps != null) {//3
            if (DEBUG_INSTALL) Slog.d(TAG, "Existing package: " + ps);
            PackageSetting signatureCheckPs = ps;
            if (pkg.applicationInfo.isStaticSharedLibrary()) {
                SharedLibraryEntry libraryEntry = getLatestSharedLibraVersionLPr(pkg);
                if (libraryEntry != null) {
                    signatureCheckPs = mSettings.getPackageLPr(libraryEntry.apk);
                }
            }
            //检查签名的正确性
            if (shouldCheckUpgradeKeySetLP(signatureCheckPs, scanFlags)) {
                if (!checkUpgradeKeySetLP(signatureCheckPs, pkg)) {
                    res.setError(INSTALL_FAILED_UPDATE_INCOMPATIBLE, "Package "
                            + pkg.packageName + " upgrade keys do not match the "
                            + "previously installed version");
                    return;
                }
            } 
            ...
        }

        int N = pkg.permissions.size();
        for (int i = N-1; i >= 0; i--) {
           //遍历每个权限，对权限进行处理
            PackageParser.Permission perm = pkg.permissions.get(i);
            BasePermission bp = mSettings.mPermissions.get(perm.info.name);
         
            }
        }
    }
    if (systemApp) {
        if (onExternal) {
            //系统APP不能在SD卡上替换安装
            res.setError(INSTALL_FAILED_INVALID_INSTALL_LOCATION,
                    "Cannot install updates to system apps on sdcard");
            return;
        } else if (instantApp) {
            //系统APP不能被Instant App替换
            res.setError(INSTALL_FAILED_INSTANT_APP_INVALID,
                    "Cannot update a system app with an instant app");
            return;
        }
    }
    ...
    //重命名临时文件
    if (!args.doRename(res.returnCode, pkg, oldCodePath)) {//4
        res.setError(INSTALL_FAILED_INSUFFICIENT_STORAGE, "Failed rename");
        return;
    }

    startIntentFilterVerifications(args.user.getIdentifier(), replace, pkg);

    try (PackageFreezer freezer = freezePackageForInstall(pkgName, installFlags,
            "installPackageLI")) {
       
        if (replace) {//5
         //替换安装   
           ...
            replacePackageLIF(pkg, parseFlags, scanFlags | SCAN_REPLACING, args.user,
                    installerPackageName, res, args.installReason);
        } else {
        //安装新的APK
            installNewPackageLIF(pkg, parseFlags, scanFlags | SCAN_DELETE_DATA_ON_FAILURES,
                    args.user, installerPackageName, volumeUuid, res, args.installReason);
        }
    }

    synchronized (mPackages) {
        final PackageSetting ps = mSettings.mPackages.get(pkgName);
        if (ps != null) {
            //更新应用程序所属的用户
            res.newUsers = ps.queryInstalledUsers(sUserManager.getUserIds(), true);
            ps.setUpdateAvailable(false /*updateAvailable*/);
        }
        ...
    }
}
```
installPackageLI方法的代码有将近500行，这里截取主要的部分，主要做了几件事：

创建PackageParser解析APK。
 1. 检查APK是否存在，如果存在就获取此前没被改名前的包名并在注释1处赋值给PackageParser.Package类型的pkg，在注释3处将标志位replace置为true表示是替换安装。
 2. 注释3处，如果Settings中保存有要安装的APK的信息，说明此前安装过该APK，则需要校验APK的签名信息，确保安全的进行替换。
 3. 在注释4处将临时文件重新命名，比如前面提到的/data/app/vmdl18300388.tmp/base.apk，重命名为/data/app/包名-1/base.apk。这个新命名的包名会带上一个数字后缀1，每次升级一个已有的App，这个数字会不断的累加。
 4. 系统APP的更新安装会有两个限制，一个是系统APP不能在SD卡上替换安装，另一个是系统APP不能被Instant App替换。
 5. 注释5处根据replace来做区分，如果是替换安装就会调用replacePackageLIF方法，其方法内部还会对系统APP和非系统APP进行区分处理，如果是新安装APK会调用installNewPackageLIF方法。

以新安装APK为例，会调用PMS的installNewPackageLIF方法。
>frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java
```java
private void installNewPackageLIF(PackageParser.Package pkg, final int policyFlags,
           int scanFlags, UserHandle user, String installerPackageName, String volumeUuid,
           PackageInstalledInfo res, int installReason) {
       ...
       try {
           //扫描APK
           PackageParser.Package newPackage = scanPackageTracedLI(pkg, policyFlags, scanFlags,
                   System.currentTimeMillis(), user);
           //更新Settings信息
           updateSettingsLI(newPackage, installerPackageName, null, res, user, installReason);
           if (res.returnCode == PackageManager.INSTALL_SUCCEEDED) {
               //安装成功后，为新安装的应用程序准备数据
               prepareAppDataAfterInstallLIF(newPackage);

           } else {
               //安装失败则删除APK
               deletePackageLIF(pkgName, UserHandle.ALL, false, null,
                       PackageManager.DELETE_KEEP_DATA, res.removedInfo, true, null);
           }
       } catch (PackageManagerException e) {
           res.setError("Package couldn't be installed in " + pkg.codePath, e);
       }
       Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
   }
```
installNewPackageLIF主要做了以下3件事：
1. 扫描APK，将APK的信息存储在PackageParser.Package类型的newPackage中，一个Package的信息包含了1个base APK以及0个或者多个split APK。
2. 更新该APK对应的Settings信息，Settings用于保存所有包的动态设置。
3. 如果安装成功就为新安装的应用程序准备数据，安装失败就删除APK。
####总结：
1. PMS发送INIT_COPY和MCS_BOUND类型的消息，控制PackageHandler来绑定DefaultContainerService，完成复制APK等工作。
2. 复制APK完成后，会开始进行安装APK的流程，包括安装前的检查、安装APK和安装后的收尾工作。



