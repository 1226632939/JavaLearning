**Android包管理机制**
==================

------

![TOC]


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

------------------

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

--------------------------

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
#### 总结：
1. PMS发送INIT_COPY和MCS_BOUND类型的消息，控制PackageHandler来绑定DefaultContainerService，完成复制APK等工作。
2. 复制APK完成后，会开始进行安装APK的流程，包括安装前的检查、安装APK和安装后的收尾工作。
--------

8.PMS的创建过程
---
####8.1SyetemServer处理部分
PMS是在SyetemServer进程中被创建的，SyetemServer进程用来创建系统服务，SyetemServer处理和AMS和WMS的创建过程是类似的.

SyetemServer的入口main方法，main方法中只调用了SystemServer的run方法，如下所示。
>frameworks/base/services/java/com/android/server/SystemServer.java
```java
public static void main(String[] args) {
    new SystemServer().run();
}
        
private void run() {
    try {
        ...
        //创建消息Looper
         Looper.prepareMainLooper();
        //加载了动态库libandroid_servers.so
        System.loadLibrary("android_servers");//1
        performPendingShutdown();
        // 创建系统的Context
        createSystemContext();
        // 创建SystemServiceManager
        mSystemServiceManager = new SystemServiceManager(mSystemContext);//2
        mSystemServiceManager.setRuntimeRestarted(mRuntimeRestart);
        LocalServices.addService(SystemServiceManager.class, mSystemServiceManager);
        SystemServerInitThreadPool.get();
    } finally {
        traceEnd(); 
    }
    try {
        traceBeginAndSlog("StartServices");
        //启动引导服务
        startBootstrapServices();//3
        //启动核心服务
        startCoreServices();//4
        //启动其他服务
        startOtherServices();//5
        SystemServerInitThreadPool.shutdown();
    } catch (Throwable ex) {
        Slog.e("System", "******************************************");
        Slog.e("System", "************ Failure starting system services", ex);
        throw ex;
    } finally {
        traceEnd();
    }
    ...
}
```
在注释1处加载了动态库libandroid_servers.so。接下来在注释2处创建SystemServiceManager，它会对系统的服务进行创建、启动和生命周期管理。在注释3中的startBootstrapServices方法中用SystemServiceManager启动了ActivityManagerService、PowerManagerService、PackageManagerService等服务。在注释4处的startCoreServices方法中则启动了DropBoxManagerService、BatteryService、UsageStatsService和WebViewUpdateService。注释5处的startOtherServices方法中启动了CameraService、AlarmManagerService、VrManagerService等服务。这些服务的父类均为SystemService。从注释3、4、5的方法可以看出，官方把系统服务分为了三种类型，分别是引导服务、核心服务和其他服务，其中其他服务是一些非紧要和一些不需要立即启动的服务。这些系统服务总共有100多个，我们熟知的AMS属于引导服务，WMS属于其他服务，

MS属于引导服务，因此这里列出引导服务以及它们的作用

| 引导服务                  | 作用  | 
| :----                   | :----:  | 
| Installer               | 系统安装apk时的一个服务类，启动完成Installer服务之后才能启动其他的系统服务  | 
| ActivityManagerService  | 负责四大组件的启动、切换、调度。  | 
| PowerManagerService     | 计算系统中和Power相关的计算，然后决策系统应该如何反应  | 
| LightsService           | 管理和显示背光LED  | 
| DisplayManagerService   | 用来管理所有显示设备  | 
| UserManagerService      | 多用户模式管理  | 
| SensorService           | 为系统提供各种感应器服务  | 
| PackageManagerService   | 用来对apk进行安装、解析、删除、卸载等等操作  | 

查看启动引导服务的注释3处的startBootstrapServices方法。
>frameworks/base/services/java/com/android/server/SystemServer.java
```java
private void startBootstrapServices() {
    ...
     String cryptState = SystemProperties.get("vold.decrypt");//1
     if (ENCRYPTING_STATE.equals(cryptState)) {
            Slog.w(TAG, "Detected encryption in progress - only parsing core apps");
            mOnlyCore = true;
        } else if (ENCRYPTED_STATE.equals(cryptState)) {
            Slog.w(TAG, "Device encrypted - only parsing core apps");
            mOnlyCore = true;
        }
    ...    
    traceBeginAndSlog("StartPackageManagerService");
    mPackageManagerService = PackageManagerService.main(mSystemContext, installer,
            mFactoryTestMode != FactoryTest.FACTORY_TEST_OFF, mOnlyCore);//2
    mFirstBoot = mPackageManagerService.isFirstBoot();//3
    mPackageManager = mSystemContext.getPackageManager();
    traceEnd();
    ...
}
```
注释1处读取init.rc的vold.decrypt属性，如果它的值为”trigger_restart_min_framework”，说明我们加密了设备，这时mOnlyCore的值为true，表示只运行“核心”程序，这是为了创建一个极简的启动环境。 

注释2处的PMS的main方法主要用来创建PMS，注释3处获取boolean类型的变量mFirstBoot，它用于表示PMS是否首次被启动。mFirstBoot是后续WMS创建时所需要的参数，从这里就可以看出系统服务之间是有依赖关系的，它们的启动顺序不能随意被更改。

####8.2PMS构造方法
PMS的main方法如下所示。
>frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java
```java
public static PackageManagerService main(Context context, Installer installer,
         boolean factoryTest, boolean onlyCore) {
     PackageManagerServiceCompilerMapping.checkProperties();
     PackageManagerService m = new PackageManagerService(context, installer,
             factoryTest, onlyCore);
     m.enableSystemUserPackages();
     ServiceManager.addService("package", m);
     return m;
 }
```
main方法主要做了两件事，一个是创建PMS对象，另一个是将PMS注册到ServiceManager中。
PMS的构造方法大概有600多行，分为5个阶段，每个阶段会打印出相应的EventLog，EventLog用于打印Android系统的事件日志。

1. BOOT_PROGRESS_PMS_START（开始阶段）
2. BOOT_PROGRESS_PMS_SYSTEM_SCAN_START（扫描系统阶段）
3. BOOT_PROGRESS_PMS_DATA_SCAN_START（扫描Data分区阶段）
4. BOOT_PROGRESS_PMS_SCAN_END（扫描结束阶段）
5. BOOT_PROGRESS_PMS_READY（准备阶段）

####8.2.1开始阶段
PMS的构造方法中会获取一些包管理需要属性，如下所示。
>frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java
```java
public PackageManagerService(Context context, Installer installer,
            boolean factoryTest, boolean onlyCore) {
        LockGuard.installLock(mPackages, LockGuard.INDEX_PACKAGES);
        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "create package manager");
        //打印开始阶段日志
        EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_PMS_START,
                SystemClock.uptimeMillis())
        ...
        //用于存储屏幕的相关信息
        mMetrics = new DisplayMetrics();
        //Settings用于保存所有包的动态设置
        mSettings = new Settings(mPackages);
	    //在Settings中添加多个默认的sharedUserId
        mSettings.addSharedUserLPw("android.uid.system", Process.SYSTEM_UID,
                ApplicationInfo.FLAG_SYSTEM, ApplicationInfo.PRIVATE_FLAG_PRIVILEGED);//1
        mSettings.addSharedUserLPw("android.uid.phone", RADIO_UID,
                ApplicationInfo.FLAG_SYSTEM, ApplicationInfo.PRIVATE_FLAG_PRIVILEGED);
        mSettings.addSharedUserLPw("android.uid.log", LOG_UID,
                ApplicationInfo.FLAG_SYSTEM, ApplicationInfo.PRIVATE_FLAG_PRIVILEGED);
        ...
        mInstaller = installer;
        //创建Dex优化工具类
        mPackageDexOptimizer = new PackageDexOptimizer(installer, mInstallLock, context,
                "*dexopt*");
        mDexManager = new DexManager(this, mPackageDexOptimizer, installer, mInstallLock);
        mMoveCallbacks = new MoveCallbacks(FgThread.get().getLooper());
        mOnPermissionChangeListeners = new OnPermissionChangeListeners(
                FgThread.get().getLooper());
        getDefaultDisplayMetrics(context, mMetrics);
        Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "get system config");
        //得到全局系统配置信息。
        SystemConfig systemConfig = SystemConfig.getInstance();
        //获取全局的groupId 
        mGlobalGids = systemConfig.getGlobalGids();
        //获取系统权限
        mSystemPermissions = systemConfig.getSystemPermissions();
        mAvailableFeatures = systemConfig.getAvailableFeatures();
        Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
        mProtectedPackages = new ProtectedPackages(mContext);
        //安装APK时需要的锁，保护所有对installd的访问。
        synchronized (mInstallLock) {//1
        //更新APK时需要的锁，保护内存中已经解析的包信息等内容
        synchronized (mPackages) {//2
            //创建后台线程ServiceThread
            mHandlerThread = new ServiceThread(TAG,
                    Process.THREAD_PRIORITY_BACKGROUND, true /*allowIo*/);
            mHandlerThread.start();
            //创建PackageHandler绑定到ServiceThread的消息队列
            mHandler = new PackageHandler(mHandlerThread.getLooper());//3
            mProcessLoggingHandler = new ProcessLoggingHandler();
            //将PackageHandler添加到Watchdog的检测集中
            Watchdog.getInstance().addThread(mHandler, WATCHDOG_TIMEOUT);//4

            mDefaultPermissionPolicy = new DefaultPermissionGrantPolicy(this);
            mInstantAppRegistry = new InstantAppRegistry(this);
            //在Data分区创建一些目录
            File dataDir = Environment.getDataDirectory();//5
            mAppInstallDir = new File(dataDir, "app");
            mAppLib32InstallDir = new File(dataDir, "app-lib");
            mAsecInternalPath = new File(dataDir, "app-asec").getPath();
            mDrmAppPrivateInstallDir = new File(dataDir, "app-private");
            //创建多用户管理服务
            sUserManager = new UserManagerService(context, this,
                    new UserDataPreparer(mInstaller, mInstallLock, mContext, mOnlyCore), mPackages);
             ...
               mFirstBoot = !mSettings.readLPw(sUserManager.getUsers(false))//6
          ...     
}
```
在开始阶段中创建了很多PMS中的关键对象并赋值给PMS中的成员变量。
>* mSettings ：用于保存所有包的动态设置。注释1处将系统进程的sharedUserId添加到Settings中，sharedUserId用于进程间共享数据，比如两个App的之间的数据是不共享的，如果它们有了共同的sharedUserId，就可以运行在同一个进程中共享数据。
>* mInstaller ：Installer继承自SystemService，和PMS、AMS一样是系统的服务（虽然名称不像是服务），PMS很多的操作都是由Installer来完成的，比如APK的安装和卸载。在Installer内部，通过IInstalld和installd进行Binder通信，由位于nativie层的installd来完成具体的操作。
>* systemConfig：用于得到全局系统配置信息。比如系统的权限就可以通过SystemConfig来获取。
>* mPackageDexOptimizer ： Dex优化的工具类。
>* mHandler（PackageHandler类型） ：PackageHandler继承自Handler，在注释3处它绑定了后台线程ServiceThread的消息队列。PMS通过PackageHandler驱动APK的复制和安装工作，具体的请看在Android包管理机制（三）PMS处理APK的安装这篇文章。
>* PackageHandler处理的消息队列如果过于繁忙，有可能导致系统卡住， 因此在注释4处将它添加到Watchdog的监测集中。
>* Watchdog主要有两个用途，一个是定时检测系统关键服务（AMS和WMS等）是否可能发生死锁，还有一个是定时检测线程的消息队列是否长时间处于工作状态（可能阻塞等待了很长时间）。如果出现上述问题，Watchdog会将日志保存起来，必要时还会杀掉自己所在的进程，也就是SystemServer进程。
>* sUserManager（UserManagerService类型） ：多用户管理服务。 

除了创建这些关键对象，在开始阶段还有一些关键代码需要去讲解：
>* 注释1处和注释2处加了两个锁，其中mInstallLock是安装APK时需要的锁，保护所有对installd的访问；mPackages是更新APK时需要的锁，保护内存中已经解析的包信息等内容。
>* 注释5处后的代码创建了一些Data分区中的子目录，比如/data/app。
>* 注释6处会解析packages.xml等文件的信息，保存到Settings的对应字段中。packages.xml中记录系统中所有安装的应用信息，包括基本信息、签名和权限。如果packages.xml有安装的应用信息，那么注释6处Settings的readLPw方法会返回true，mFirstBoot的值为false，说明PMS不是首次被启动。

####8.2.2扫描系统阶段
```java
...
public PackageManagerService(Context context, Installer installer,
            boolean factoryTest, boolean onlyCore) {
...
            //打印扫描系统阶段日志
            EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_PMS_SYSTEM_SCAN_START,
                    startTime);
            ...
            //在/system中创建framework目录
            File frameworkDir = new File(Environment.getRootDirectory(), "framework");
            ...
            //扫描/vendor/overlay目录下的文件
            scanDirTracedLI(new File(VENDOR_OVERLAY_DIR), mDefParseFlags
                    | PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR
                    | PackageParser.PARSE_TRUSTED_OVERLAY, scanFlags | SCAN_TRUSTED_OVERLAY, 0);
            mParallelPackageParserCallback.findStaticOverlayPackages();
            //扫描/system/framework 目录下的文件
            scanDirTracedLI(frameworkDir, mDefParseFlags
                    | PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR
                    | PackageParser.PARSE_IS_PRIVILEGED,
                    scanFlags | SCAN_NO_DEX, 0);
            final File privilegedAppDir = new File(Environment.getRootDirectory(), "priv-app");
            //扫描 /system/priv-app 目录下的文件
            scanDirTracedLI(privilegedAppDir, mDefParseFlags
                    | PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR
                    | PackageParser.PARSE_IS_PRIVILEGED, scanFlags, 0);
            final File systemAppDir = new File(Environment.getRootDirectory(), "app");
            //扫描/system/app 目录下的文件
            scanDirTracedLI(systemAppDir, mDefParseFlags
                    | PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR, scanFlags, 0);
            File vendorAppDir = new File("/vendor/app");
            try {
                vendorAppDir = vendorAppDir.getCanonicalFile();
            } catch (IOException e) {
                // failed to look up canonical path, continue with original one
            }
            //扫描 /vendor/app 目录下的文件
            scanDirTracedLI(vendorAppDir, mDefParseFlags
                    | PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR, scanFlags, 0);

           //扫描/oem/app 目录下的文件
            final File oemAppDir = new File(Environment.getOemDirectory(), "app");
            scanDirTracedLI(oemAppDir, mDefParseFlags
                    | PackageParser.PARSE_IS_SYSTEM
                    | PackageParser.PARSE_IS_SYSTEM_DIR, scanFlags, 0);

            //这个列表代表有可能有升级包的系统App
            final List<String> possiblyDeletedUpdatedSystemApps = new ArrayList<String>();//1
            if (!mOnlyCore) {
                Iterator<PackageSetting> psit = mSettings.mPackages.values().iterator();
                while (psit.hasNext()) {
                    PackageSetting ps = psit.next();                 
                    if ((ps.pkgFlags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        continue;
                    }
                    //这里的mPackages的是PMS的成员变量，代表scanDirTracedLI方法扫描上面那些目录得到的 
                    final PackageParser.Package scannedPkg = mPackages.get(ps.name);
                    if (scannedPkg != null) {           
                        if (mSettings.isDisabledSystemPackageLPr(ps.name)) {//2
                           ...
                            //将这个系统App的PackageSetting从PMS的mPackages中移除
                            removePackageLI(scannedPkg, true);
                            //将升级包的路径添加到mExpectingBetter列表中
                            mExpectingBetter.put(ps.name, ps.codePath);
                        }
                        continue;
                    }
                   
                    if (!mSettings.isDisabledSystemPackageLPr(ps.name)) {
                       ...   
                    } else {
                        final PackageSetting disabledPs = mSettings.getDisabledSystemPkgLPr(ps.name);
                        //这个系统App升级包信息在mDisabledSysPackages中,但是没有发现这个升级包存在
                        if (disabledPs.codePath == null || !disabledPs.codePath.exists()) {//5
                            possiblyDeletedUpdatedSystemApps.add(ps.name);//
                        }
                    }
                }
            }
            ...        
}
```
/system可以称作为System分区，里面主要存储谷歌和其他厂商提供的Android系统相关文件和框架。Android系统架构分为应用层、应用框架层、系统运行库层（Native 层）、硬件抽象层（HAL层）和Linux内核层，除了Linux内核层在Boot分区，其他层的代码都在System分区。下面列出 System分区的部分子目录。

| 目录| 含义|
| :-----: | :-----:|
| app| 存放系统App，包括了谷歌内置的App也有厂商或者运营商提供的App |
| framework| 存放应用框架层的jar包 |
| priv-app| 存放特权App |
| lib| 存放so文件 |
| fonts| 存放系统字体文件 |
| media| 存放系统的各种声音，比如铃声、提示音，以及系统启动播放的动画 |

上面的代码还涉及到/vendor 目录，它用来存储厂商对Android系统的定制部分。

系统扫描阶段的主要工作有以下3点：

1. 创建/system的子目录，比如/system/framework、/system/priv-app和/system/app等等
2. 扫描系统文件，比如/vendor/overlay、/system/framework、/system/app等等目录下的文件。
3. 对扫描到的系统文件做后续处理。

主要来说第3点，一次OTA升级对于一个系统App会有三种情况:
* 这个系统APP无更新。
* 这个系统APP有更新。
* 新的OTA版本中，这个系统APP已经被删除。

当系统App升级，PMS会将该系统App的升级包设置数据`PackageSetting`存储到`Settings`的`mDisabledSysPackages`列表中（具体见PMS的`replaceSystemPackageLIF`方法），`mDisabledSysPackages`的类型为`ArrayMap<String, PackageSetting>`。`mDisabledSysPackages`中的信息会被PMS保存到`packages.xml`中的`<updated-package>`标签下（具体见`Settings`的`writeDisabledSysPackageLPr`方法）。

注释2处说明这个系统App有升级包，那么就将该系统App的`PackageSetting`从`mDisabledSysPackages`列表中移除，并将系统App的升级包的路径添加到`mExpectingBetter`列表中，`mExpectingBetter`的类型为`ArrayMap<String, File>`等待后续处理。

注释5处如果这个系统App的升级包信息存储在`mDisabledSysPackages`列表中，但是没有发现这个升级包存在，则将它加入到`possiblyDeletedUpdatedSystemApps`列表中，意为“系统App的升级包可能被删除”，之所以是“可能”，是因为系统还没有扫描Data分区，只能暂放到`possiblyDeletedUpdatedSystemApps`列表中，等到扫描完Data分区后再做处理。

####8.2.3扫描Data分区阶段
```java
public PackageManagerService(Context context, Installer installer,
            boolean factoryTest, boolean onlyCore) {
    ...        
    mSettings.pruneSharedUsersLPw();
    //如果没有加密设备，那么就开始扫描Data分区。
    if (!mOnlyCore) {
        //打印扫描Data分区阶段日志
        EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_PMS_DATA_SCAN_START,
                SystemClock.uptimeMillis());
        //扫描/data/app目录下的文件       
        scanDirTracedLI(mAppInstallDir, 0, scanFlags | SCAN_REQUIRE_KNOWN, 0);
        //扫描/data/app-private目录下的文件   
        scanDirTracedLI(mDrmAppPrivateInstallDir, mDefParseFlags
                | PackageParser.PARSE_FORWARD_LOCK,
                scanFlags | SCAN_REQUIRE_KNOWN, 0);
        //扫描完Data分区后，处理possiblyDeletedUpdatedSystemApps列表
        for (String deletedAppName : possiblyDeletedUpdatedSystemApps) {
            PackageParser.Package deletedPkg = mPackages.get(deletedAppName);
            // 从mSettings.mDisabledSysPackages变量中移除去此应用
            mSettings.removeDisabledSystemPackageLPw(deletedAppName);
            String msg;
          //1：如果这个系统App的包信息不在PMS的变量mPackages中，说明是残留的App信息，后续会删除它的数据。
            if (deletedPkg == null) {
                msg = "Updated system package " + deletedAppName
                        + " no longer exists; it's data will be wiped";
                // Actual deletion of code and data will be handled by later
                // reconciliation step
            } else {
            //2：如果这个系统App在mPackages中，说明是存在于Data分区，不属于系统App，那么移除其系统权限。
                msg = "Updated system app + " + deletedAppName
                        + " no longer present; removing system privileges for "
                        + deletedAppName;
                deletedPkg.applicationInfo.flags &= ~ApplicationInfo.FLAG_SYSTEM;
                PackageSetting deletedPs = mSettings.mPackages.get(deletedAppName);
                deletedPs.pkgFlags &= ~ApplicationInfo.FLAG_SYSTEM;
            }
            logCriticalInfo(Log.WARN, msg);
        }
         //遍历mExpectingBetter列表
        for (int i = 0; i < mExpectingBetter.size(); i++) {
            final String packageName = mExpectingBetter.keyAt(i);
            if (!mPackages.containsKey(packageName)) {
                //得到系统App的升级包路径
                final File scanFile = mExpectingBetter.valueAt(i);
                logCriticalInfo(Log.WARN, "Expected better " + packageName
                        + " but never showed up; reverting to system");
                int reparseFlags = mDefParseFlags;
                //3：根据系统App所在的目录设置扫描的解析参数
                if (FileUtils.contains(privilegedAppDir, scanFile)) {
                    reparseFlags = PackageParser.PARSE_IS_SYSTEM
                            | PackageParser.PARSE_IS_SYSTEM_DIR
                            | PackageParser.PARSE_IS_PRIVILEGED;
                } 
                ...
                //将packageName对应的包设置数据（PackageSetting）添加到mSettings的mPackages中
                mSettings.enableSystemPackageLPw(packageName);//4
                try {
                    //扫描系统App的升级包
                    scanPackageTracedLI(scanFile, reparseFlags, scanFlags, 0, null);//5
                } catch (PackageManagerException e) {
                    Slog.e(TAG, "Failed to parse original system package: "
                            + e.getMessage());
                }
            }
        }
    }
   //清除mExpectingBetter列表
    mExpectingBetter.clear();
...
}
```
/data可以称为Data分区，它用来存储所有用户的个人数据和配置文件。下面列出Data分区部分子目录：

| 目录| 含义|
| :----: | :----:|
| app| 存储用户自己安装的App|
| data| 存储所有已安装的App数据的目录，每个App都有自己单独的子目录 |
| app-private| App的私有存储空间 |
| app-lib|存储所有App的Jni库 |
| system| 存放系统配置文件 |
| anr| 用于存储ANR发生时系统生成的traces.txt文件 |

扫描Data分区阶段主要做了以下几件事：
1. 扫描/data/app和/data/app-private目录下的文件。
2. 遍历possiblyDeletedUpdatedSystemApps列表，注释1处如果这个系统App的包信息不在PMS的变量mPackages中，说明是残留的App信息，后续会删除它的数据。注释2处如果这个系统App的包信息在mPackages中，说明是存在于Data分区，不属于系统App，那么移除其系统权限。
3. 遍历mExpectingBetter列表，注释3处根据系统App所在的目录设置扫描的解析参数，注释4处的方法内部会将packageName对应的包设置数据（PackageSetting）添加到mSettings的mPackages中。注释5处扫描系统App的升级包，最后清除mExpectingBetter列表。

####8.2.4扫描结束阶段

```java
//打印扫描结束阶段日志
EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_PMS_SCAN_END,
                  SystemClock.uptimeMillis());
          Slog.i(TAG, "Time to scan packages: "
                  + ((SystemClock.uptimeMillis()-startTime)/1000f)
                  + " seconds");
          int updateFlags = UPDATE_PERMISSIONS_ALL;
          // 如果当前平台SDK版本和上次启动时的SDK版本不同，重新更新APK的授权
          if (ver.sdkVersion != mSdkVersion) {
              Slog.i(TAG, "Platform changed from " + ver.sdkVersion + " to "
                      + mSdkVersion + "; regranting permissions for internal storage");
              updateFlags |= UPDATE_PERMISSIONS_REPLACE_PKG | UPDATE_PERMISSIONS_REPLACE_ALL;
          }
          updatePermissionsLPw(null, null, StorageManager.UUID_PRIVATE_INTERNAL, updateFlags);
          ver.sdkVersion = mSdkVersion;
         //如果是第一次启动或者是Android M升级后的第一次启动，需要初始化所有用户定义的默认首选App
          if (!onlyCore && (mPromoteSystemApps || mFirstBoot)) {
              for (UserInfo user : sUserManager.getUsers(true)) {
                  mSettings.applyDefaultPreferredAppsLPw(this, user.id);
                  applyFactoryDefaultBrowserLPw(user.id);
                  primeDomainVerificationsLPw(user.id);
              }
          }
         ...
          //OTA后的第一次启动，会清除代码缓存目录。
          if (mIsUpgrade && !onlyCore) {
              Slog.i(TAG, "Build fingerprint changed; clearing code caches");
              for (int i = 0; i < mSettings.mPackages.size(); i++) {
                  final PackageSetting ps = mSettings.mPackages.valueAt(i);
                  if (Objects.equals(StorageManager.UUID_PRIVATE_INTERNAL, ps.volumeUuid)) {
                      clearAppDataLIF(ps.pkg, UserHandle.USER_ALL,
                              StorageManager.FLAG_STORAGE_DE | StorageManager.FLAG_STORAGE_CE
                                      | Installer.FLAG_CLEAR_CODE_CACHE_ONLY);
                  }
              }
              ver.fingerprint = Build.FINGERPRINT;
          }
          ...
         // 把Settings的内容保存到packages.xml中
          mSettings.writeLPr();
          Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
```
扫描结束结束阶段主要做了以下几件事：

1. 如果当前平台SDK版本和上次启动时的SDK版本不同，重新更新APK的授权。
2. 如果是第一次启动或者是Android M升级后的第一次启动，需要初始化所有用户定义的默认首选App。
3. OTA升级后的第一次启动，会清除代码缓存目录。
4. 把Settings的内容保存到packages.xml中，这样此后PMS再次创建时会读到此前保存的Settings的内容。

####8.2.5准备阶段
```java
    EventLog.writeEvent(EventLogTags.BOOT_PROGRESS_PMS_READY,
                SystemClock.uptimeMillis());
    ... 
    mInstallerService = new PackageInstallerService(context, this);//1
    ...
    Runtime.getRuntime().gc();//2
    Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
    Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "loadFallbacks");
    FallbackCategoryProvider.loadFallbacks();
    Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
    mInstaller.setWarnIfHeld(mPackages);
    LocalServices.addService(PackageManagerInternal.class, new PackageManagerInternalImpl());//3
    Trace.traceEnd(TRACE_TAG_PACKAGE_MANAGER);
}
```
注释1处创建PackageInstallerService，PackageInstallerService是用于管理安装会话的服务，它会为每次安装过程分配一个SessionId,

注释2处进行一次垃圾收集。注释3处将PackageManagerInternalImpl（PackageManager的本地服务）添加到LocalServices中，
LocalServices用于存储运行在当前的进程中的本地服务。

9.Apk解析分析
---

####9.1引入PackageParser

Android世界中有很多包，比如应用程序的APK，Android运行环境的JAR包（比如framework.jar）和组成Android系统的各种动态库so等等，由于包的种类和数量繁多，就需要进行包管理，但是包管理需要在内存中进行，而这些包都是以静态文件的形式存在的，就需要一个工具类将这些包转换为内存中的数据结构，这个工具就是包解析器PackageParser。

安装APK时需要调用PMS的installPackageLI方法：
>frameworks/base/services/core/java/com/android/server/pm/PackageManagerService.java
```java
private void installPackageLI(InstallArgs args, PackageInstalledInfo res) {
    ...
    PackageParser pp = new PackageParser();//1
    pp.setSeparateProcesses(mSeparateProcesses);
    pp.setDisplayMetrics(mMetrics);
    pp.setCallback(mPackageParserCallback);
    Trace.traceBegin(TRACE_TAG_PACKAGE_MANAGER, "parsePackage");
    final PackageParser.Package pkg;
    try {
        pkg = pp.parsePackage(tmpPackageFile, parseFlags);//2
    }
    ...
 }   
```
安装APK时，需要先在注释1处创建PackageParser，然后在注释2处调用PackageParser的parsePackage方法来解析APK。

####9.2PackageParser解析Apk
Android5.0引入了Split APK机制，这是为了解决65536上限以及APK安装包越来越大等问题。Split APK机制可以将一个APK，拆分成多个独立APK。

在引入了Split APK机制后，APK有两种分类：
* Single APK：安装文件为一个完整的APK，即base APK。Android称其为Monolithic。
* Mutiple APK：安装文件在一个文件目录中，其内部有多个被拆分的APK，这些APK由一个 base APK和一个或多个split APK组成。Android称其为Cluster。

PackageParser的parsePackage方法：
>frameworks/base/core/java/android/content/pm/PackageParser.java
```java
public Package parsePackage(File packageFile, int flags, boolean useCaches)
           throws PackageParserException {
       Package parsed = useCaches ? getCachedResult(packageFile, flags) : null;
       if (parsed != null) {
           return parsed;
       }
       if (packageFile.isDirectory()) {//1
           parsed = parseClusterPackage(packageFile, flags);
       } else {
           parsed = parseMonolithicPackage(packageFile, flags);
       }
       cacheResult(packageFile, flags, parsed);

       return parsed;
   }
```
注释1处，如果要解析的packageFile是一个目录，说明是Mutiple APK，就需要调用parseClusterPackage方法来解析，如果是Single APK则调用parseMonolithicPackage方法来解析。这里以复杂的parseClusterPackage方法为例，了解了这个方法，parseMonolithicPackage方法自然也看的懂。


![](https://github.com/1226632939/JavaLearning/blob/master/src/res/drawble/PackageParser.png)


>frameworks/base/core/java/android/content/pm/PackageParser.java
```java
private Package parseClusterPackage(File packageDir, int flags) throws PackageParserException {
        final PackageLite lite = parseClusterPackageLite(packageDir, 0);//1
       if (mOnlyCoreApps && !lite.coreApp) {//2
           throw new PackageParserException(INSTALL_PARSE_FAILED_MANIFEST_MALFORMED,
                   "Not a coreApp: " + packageDir);
       }
       ...
       try {
           final AssetManager assets = assetLoader.getBaseAssetManager();
           final File baseApk = new File(lite.baseCodePath);
           final Package pkg = parseBaseApk(baseApk, assets, flags);//3
           if (pkg == null) {
               throw new PackageParserException(INSTALL_PARSE_FAILED_NOT_APK,
                       "Failed to parse base APK: " + baseApk);
           }
           if (!ArrayUtils.isEmpty(lite.splitNames)) {
               final int num = lite.splitNames.length;//4
               pkg.splitNames = lite.splitNames;
               pkg.splitCodePaths = lite.splitCodePaths;
               pkg.splitRevisionCodes = lite.splitRevisionCodes;
               pkg.splitFlags = new int[num];
               pkg.splitPrivateFlags = new int[num];
               pkg.applicationInfo.splitNames = pkg.splitNames;
               pkg.applicationInfo.splitDependencies = splitDependencies;
               for (int i = 0; i < num; i++) {
                   final AssetManager splitAssets = assetLoader.getSplitAssetManager(i);
                   parseSplitApk(pkg, i, splitAssets, flags);//5
               }
           }
           pkg.setCodePath(packageDir.getAbsolutePath());
           pkg.setUse32bitAbi(lite.use32bitAbi);
           return pkg;
       } finally {
           IoUtils.closeQuietly(assetLoader);
       }
   }
```
注释1处调用parseClusterPackageLite方法用于轻量级解析目录文件，之所以要轻量级解析是因为解析APK是一个复杂耗时的操作，这里的逻辑并不需要APK所有的信息。parseClusterPackageLite方法内部会通过parseApkLite方法解析每个Mutiple APK，得到每个Mutiple APK对应的ApkLite（轻量级APK信息），然后再将这些ApkLite封装为一个PackageLite（轻量级包信息）并返回。

注释2处，mOnlyCoreApps用来指示PackageParser是否只解析“核心”应用，“核心”应用指的是AndroidManifest中属性coreApp值为true，只解析“核心”应用是为了创建一个极简的启动环境。mOnlyCoreApps在创建PMS时就一路传递过来，如果我们加密了设备，mOnlyCoreApps值就为true，具体的见Android包管理机制（四）PMS的创建过程这篇文章的第1小节。另外可以通过PackageParser的setOnlyCoreApps方法来设置mOnlyCoreApps的值。

`lite.coreApp`表示当前包是否包含“核心”应用，如果不满足注释2的条件就会抛出异常。

注释3处的parseBaseApk方法用于解析base APK，注释4处获取split APK的数量，根据这个数量在注释5处遍历调用parseSplitApk来解析每个split APK。这里主要查看parseBaseApk方法，如下所示。
>frameworks/base/core/java/android/content/pm/PackageParser.java
```java
private Package parseBaseApk(File apkFile, AssetManager assets, int flags)
           throws PackageParserException {
       final String apkPath = apkFile.getAbsolutePath();
       String volumeUuid = null;
       if (apkPath.startsWith(MNT_EXPAND)) {
           final int end = apkPath.indexOf('/', MNT_EXPAND.length());
           volumeUuid = apkPath.substring(MNT_EXPAND.length(), end);//1
       }
       ...
       Resources res = null;
       XmlResourceParser parser = null;
       try {
           res = new Resources(assets, mMetrics, null);
           parser = assets.openXmlResourceParser(cookie, ANDROID_MANIFEST_FILENAME);
           final String[] outError = new String[1];
           final Package pkg = parseBaseApk(apkPath, res, parser, flags, outError);//2
           if (pkg == null) {
               throw new PackageParserException(mParseError,
                       apkPath + " (at " + parser.getPositionDescription() + "): " + outError[0]);
           }
           pkg.setVolumeUuid(volumeUuid);//3
           pkg.setApplicationVolumeUuid(volumeUuid);//4
           pkg.setBaseCodePath(apkPath);
           pkg.setSignatures(null);
           return pkg;
       } catch (PackageParserException e) {
           throw e;
       }
       ...
   }
```
注释1处，如果APK的路径以/mnt/expand/开头，就截取该路径获取volumeUuid，注释3处用于以后标识这个解析后的Package，注释4处的用于标识该App所在的存储卷UUID。

注释2处又调用了parseBaseApk的重载方法，可以看出当前的parseBaseApk方法主要是为了获取和设置volumeUuid。parseBaseApk的重载方法如下所示。
>frameworks/base/core/java/android/content/pm/PackageParser.java
```java
private Package parseBaseApk(String apkPath, Resources res, XmlResourceParser parser, int flags,
           String[] outError) throws XmlPullParserException, IOException {
       ...
       final Package pkg = new Package(pkgName);//1
       //从资源中提取自定义属性集com.android.internal.R.styleable.AndroidManifest得到TypedArray 
       TypedArray sa = res.obtainAttributes(parser,
               com.android.internal.R.styleable.AndroidManifest);//2
       //使用typedarray获取AndroidManifest中的versionCode赋值给Package的对应属性        
       pkg.mVersionCode = pkg.applicationInfo.versionCode = sa.getInteger(
               com.android.internal.R.styleable.AndroidManifest_versionCode, 0);
       pkg.baseRevisionCode = sa.getInteger(
               com.android.internal.R.styleable.AndroidManifest_revisionCode, 0);
       pkg.mVersionName = sa.getNonConfigurationString(
               com.android.internal.R.styleable.AndroidManifest_versionName, 0);
       if (pkg.mVersionName != null) {
           pkg.mVersionName = pkg.mVersionName.intern();
       }
       pkg.coreApp = parser.getAttributeBooleanValue(null, "coreApp", false);//3
       //获取资源后要回收
       sa.recycle();
       return parseBaseApkCommon(pkg, null, res, parser, flags, outError);
   }
```

注释1处创建了Package对象，注释2处从资源中提取自定义属性集 com.android.internal.R.styleable.AndroidManifest得到TypedArray ，这个属性集所在的源码位置为frameworks/base/core/res/res/values/attrs_manifest.xml。接着用TypedArray读取APK的AndroidManifest中的versionCode、revisionCode和versionName的值赋值给Package的对应的属性。

注释3处读取APK的AndroidManifest中的coreApp的值。

最后会调用parseBaseApkCommon方法，这个方法非常长，主要用来解析APK的AndroidManifest中的各个标签，比如application、permission、uses-sdk、feature-group等等，其中四大组件的标签在application标签下，解析application标签的方法为parseBaseApplication。
>frameworks/base/core/java/android/content/pm/PackageParser.java
```java
  private boolean parseBaseApplication(Package owner, Resources res,
            XmlResourceParser parser, int flags, String[] outError)
        throws XmlPullParserException, IOException {
        ...
        while ((type = parser.next()) != XmlPullParser.END_DOCUMENT
                && (type != XmlPullParser.END_TAG || parser.getDepth() > innerDepth)) {
            if (type == XmlPullParser.END_TAG || type == XmlPullParser.TEXT) {
                continue;
            }
            String tagName = parser.getName();
            if (tagName.equals("activity")) {//1
                Activity a = parseActivity(owner, res, parser, flags, outError, false,
                        owner.baseHardwareAccelerated);//2
                if (a == null) {
                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
                    return false;
                }
                owner.activities.add(a);//3
            } else if (tagName.equals("receiver")) {
                Activity a = parseActivity(owner, res, parser, flags, outError, true, false);
                if (a == null) {
                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
                    return false;
                }
                owner.receivers.add(a);
            } else if (tagName.equals("service")) {
                Service s = parseService(owner, res, parser, flags, outError);
                if (s == null) {
                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
                    return false;
                }
                owner.services.add(s);
            } else if (tagName.equals("provider")) {
                Provider p = parseProvider(owner, res, parser, flags, outError);
                if (p == null) {
                    mParseError = PackageManager.INSTALL_PARSE_FAILED_MANIFEST_MALFORMED;
                    return false;
                }
                owner.providers.add(p);
             ...
            } 
        }
     ...
}
```

parseBaseApplication方法有近500行代码，这里只截取了解析四大组件相关的代码。注释1处如果标签名为activity，就调用注释2处的parseActivity方法解析activity标签并得到一个Activity对象（PackageParser的静态内部类），这个方法有300多行代码，解析一个activity标签就如此繁琐，activity标签只是Application中众多标签的一个，而Application只是AndroidManifest众多标签的一个，这让我们更加理解了为什么此前解析APK时要使用轻量级解析了。注释3处将解析得到的Activity对象保存在Package的列表activities中。其他的四大组件也是类似的逻辑。

PackageParser解析APK的代码逻辑非常庞大，基本了解本文所讲的就足够了，如果有兴趣可以自行看源码。

parseBaseApk方法主要的解析结构可以理解为以下简图。


![](https://github.com/1226632939/JavaLearning/blob/master/src/res/drawble/PackageParserApk.png)


####9.3.Package的数据结构

包被解析后，最终在内存是Package，Package是PackageParser的内部类，它的部分成员变量如下所示。
>frameworks/base/core/java/android/content/pm/PackageParser.java
```java
public final static class Package implements Parcelable {
    public String packageName;
    public String manifestPackageName;
    public String[] splitNames;
    public String volumeUuid;
    public String codePath;
    public String baseCodePath;
    ...
    public ApplicationInfo applicationInfo = new ApplicationInfo();
    public final ArrayList<Permission> permissions = new ArrayList<Permission>(0);
    public final ArrayList<PermissionGroup> permissionGroups = new ArrayList<PermissionGroup>(0);
    public final ArrayList<Activity> activities = new ArrayList<Activity>(0);//1
    public final ArrayList<Activity> receivers = new ArrayList<Activity>(0);
    public final ArrayList<Provider> providers = new ArrayList<Provider>(0);
    public final ArrayList<Service> services = new ArrayList<Service>(0);
    public final ArrayList<Instrumentation> instrumentation = new ArrayList<Instrumentation>(0);
...
}
```
注释1处，activities列表中存储了类型为Activity的对象，需要注意的是这个Acticity并不是我们常用的那个Activity，而是PackageParser的静态内部类，Package中的其他列表也都是如此。Package的数据结构简图如下所示。


![](https://github.com/1226632939/JavaLearning/blob/master/src/res/drawble/Package.png)


从这个简图中可以发现Package的数据结构是如何设计的：

>* Package中存有许多组件，比如Acticity、Provider、Permission等等，它们都继承基类Component。
>* 每个组件都包含一个info数据，比如Activity类中包含了成员变量ActivityInfo，这个ActivityInfo才是真正的Activity数据。
>* 四大组件的标签内可能包含<intent-filter>来过滤Intent信息，因此需要IntentInfo来保存组件的intent信息，组件基类Component依赖于IntentInfo，IntentInfo有三个子类ActivityIntentInfo、ServiceIntentInfo和ProviderIntentInfo，不同组件依赖的IntentInfo会有所不同，比如Activity继承自Component<ActivityIntentInfo> ，Permission继承自Component<IntentInfo> 。

最终的解析的数据会封装到Package中，除此之外在解析过程中还有两个轻量级数据结构ApkLite和PackageLite，因为这两个数据和Package没有太大的关联就没有在上图中表示。



