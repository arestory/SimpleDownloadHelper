# SimpleDownloadHelper
## how to use 

> 一直听说过Android自带的DownloadManager，只是拖延症发作，一直没有时间去研究研究，其实在很多项目开发中，都有一个功能是非常重要的,那就是应用的检查更新了！基于DownloadManager，可以做一个轻量级的下载器，将下载任务交给系统去执行，减轻自身APP的压力，何乐而不为呢？！

# DownloadManager的基本使用姿势
## 通过getSystemService进行实例化
```java
DownloadManager downloadManager = (DownloadManager)context.getSystemService(Context.DOWNLOAD_SERVICE);
```
## 构建下载请求
``` java
DownloadManager.Request request = new DownloadManager.Request(Uri.parse("目标文件下载地址"));
//设置目标文件夹，如果你想在系统的storage目录下载一个testDownload/test/test.apk
request.setDestinationInExternalPublicDir("testDownload", "test/test.apk");
//设置下载所需的网络环境,设置了移动网络和WiFi环境下均能下载 request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
//通知栏设置
//显示在通知栏
request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
```
注意！！！如果选择不显示在通知栏，那么必须声明以下权限
```xml
<uses-permission android:name="android.permission.DOWNLOAD_WITHOUT_NOTIFICATION" />
```
然后才能设置不可见，否则将会抛出一个SecurityException
```
request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);

```
指定文件类型
```java
//设置文件类型为apk类型，当downloadManager调用openFile时会唤起相应的程序
request.setMimeType("application/cn.trinea.download.file");
 //开始下载,得到一个唯一的downloadId，大有用处
long downloadId = downloadManager.enqueue(request);
```
如何获取下载的情况呢
 ```java
private int[] getBytesAndStatus(long downloadId) {

        //构建一个数组，存放已下载文件大小、总大小、下载状态
        int[] bytesAndStatus = new int[]{
                -1, -1, 0
        };
        //通过构建下载请求时获得的downloadId进行文件查询
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        Cursor cursor = null;
        try {
            cursor = downloadManager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                //已经下载文件大小
                bytesAndStatus[0] = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                //下载文件的总大小
                bytesAndStatus[1] = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                //下载状态
                bytesAndStatus[2] = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return bytesAndStatus;
    }
```
注册监听文件下载成功的广播
```java
private BroadcastReceiver downloadCompleteReceiver;
downloadCompleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //文件下载成功时
                 DownloadManager.Query query = new DownloadManager.Query();
                //通过下载的id查找
                query.setFilterById(downloadId);
                Cursor c = downloadManager.query(query);
                if (c.moveToFirst()) {
                        int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
                       switch (status) {
                            //下载完成
                            case DownloadManager.STATUS_SUCCESSFUL:

                                  break;

                              }

                  }
            }
        };
//注册，这里只能拦截文件下载成功的广播，并不能进行进度监听，在适当的地方取消订阅广播
context.registerReceiver(downloadCompleteReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
```

网上很多博客都是通过自定义ContentObserver获取本地文件变化，其实可以另辟蹊径，通过一个定时器间隔调用以上getBytesAndStatus(long downloadId)能达到同样的效果，用Rxjava实现一个简单的定时器
 ```java
 /**
   * 由于DownloadManager自身没有提供实时进度的api，所以通过以下定时器获取已下载的文件大小
   */
    private void updateProgress() {
                //每隔0.5秒刷新一次进度，在适当的地方记得注销 timeDisposable
                 Disposable  timeDisposable = Observable.interval(500, TimeUnit.MILLISECONDS).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread()).subscribe(new DataConsumer<Long>() {
                    @Override
                    public void acceptData(@io.reactivex.annotations.NonNull Long aLong) {

                        int [ ]  bytesAndStatus = getBytesAndStatus(downloadId);
                        //todo 在这里进行回调即可
                    }
                });

    }

```

# 了解完DownloadManager基本的使用方式，那么基于它来封装一个简单易用的下载器吧！
>实现效果
 
```java
  DownloadHelper.Builder builder = new DownloadHelper.Builder(this).title("下载通知")
                    .description("正在下载新版本V1.2.0")
                    .downloadUrl("http://download.sj.qq.com/upload/connAssitantDownload/upload/MobileAssistant_1.apk")
                    .fileSaveName("MobileAssistant_1.apk").fileSavePath("testDownload")
                    .notifyVisible(true)
                    .fileType(DownloadHelper.FileType.APK).apkInstallHint(true).onProgressListener(new DownloadHelper.OnDownloadProgressListener() {
                        @Override
                        public void onProgress(int downloadedSize, int totalSize) {

                            int progress =(int)((downloadedSize*1.0f/totalSize)*100);
                            Logger.d("progress=%d",progress);
                             //进度回调

                        }

                        @Override
                        public void onSuccess(Uri fileUri) {
                          //文件下载成功回调的Uri
 

                        }

                        @Override
                        public void onFail() {
                          //文件下载失败
    

                        }

                        @Override
                        public void fileAlreadyExits(File file) { 
                        //当你想重复下载同样的文件时，本地检测是否存在同样的文件，进行回调
                          
                        }
                    });
            DownloadHelper downloadHelper = builder.build();
            //开始下载
            downloadHelper.start();
            //移除下载任务
            downloadHelper.deleteDownloadFile();
```

