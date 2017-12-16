package ywq.ares.simpledownloadhelper;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/**
 * Created on 2017/12/16 21:32.
 * 基于DownloadManager的下载帮助工具，暂不支持断点续传功能
 *
 * @author ares
 */
// TODO: 2017/12/16  断点续传功能
public class DownloadHelper {


    //构建器
    private Builder builder;
    //下载器
    private DownloadManager downloadManager;
    //下载的ID
    private long downloadId;

    //定时任务
    private  ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(3);
   private Runnable command = new Runnable() {

        @Override
        public void run() {

            //更新进度
            updateProgress();

        }
    };

    //下载状态
    private DOWNLOAD_STATUS downloadStatus = DOWNLOAD_STATUS.WAIT;

    //目标文件
    private File targetFile;


    //广播监听下载状态
    private BroadcastReceiver downloadCompleteReceiver;

    private DownloadHelper(Builder builder) {

        this.builder = builder;
        if (TextUtils.isEmpty(this.builder.downloadUrl)) {
            throw new IllegalStateException("下载链接不能为空！！");
        }
    }


    public DOWNLOAD_STATUS getDownloadStatus() {
        return downloadStatus;
    }

    /**
     * 下载状态
     */
    public enum DOWNLOAD_STATUS {

        DOWNLOADING, FINISH, FAIL, WAIT
    }


    /**
     * 下载的文件类型
     */
    public enum FileType {

        APK, IMAGE, NORMAL

    }

    /**
     * 删除原文件重新开始
     */
    public void forceRestart() {

        downloadStatus = DOWNLOAD_STATUS.DOWNLOADING;

        if (targetFile != null && targetFile.exists()) {


            targetFile.delete();
        }
        start();
    }

    /**
     * 判断目标文件是否已存在
     *
     * @return
     */
    public boolean targetFileExist() {


        return targetFile != null && targetFile.exists();
    }


    /**
     * 取消下载
     */
    public void deleteDownloadFile() {

        downloadManager.remove(downloadId);
        if (targetFileExist()) {
            targetFile.delete();
        }
        end();

    }

    // TODO: 2017/12/16 暂停
    private void pause() {

    }


    /**
     * 开始下载
     */
    public void start() {

        downloadManager = (DownloadManager) this.builder.mContext.getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(this.builder.downloadUrl));
        targetFile = Environment.getExternalStoragePublicDirectory(this.builder.fileSavePath + File.separator + this.builder.fileSaveName);
        boolean exist = targetFile.exists();
        //是否强制删除旧文件
        if (this.builder.replaceExistFile) {
            targetFile.delete();
        }
        //查看是否存在原文件
        if (exist) {
            //设置当前状态为已完成
            downloadStatus = DOWNLOAD_STATUS.FINISH;
            if (this.builder.listener != null) {
                this.builder.listener.fileAlreadyExits(targetFile);
            }

            return;

        }

        //设置目标文件夹
        request.setDestinationInExternalPublicDir(this.builder.fileSavePath, this.builder.fileSaveName);


        //设置下载所需的网络环境
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);

        //是否显示在通知栏
        if (this.builder.notifyVisible) {
            //设置通知标题
            request.setTitle(this.builder.title);
            //设置通知描述
            request.setDescription(this.builder.desc);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        } else {
            // 必须加上 权限<uses-permission android:name="android.permission.DOWNLOAD_WITHOUT_NOTIFICATION" />，否则会抛出securityException
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);

        }

        //是否为apk
        if (this.builder.fileType == FileType.APK) {

            //当downloadManager调用openFile时会唤起相应的程序
            request.setMimeType("application/cn.trinea.download.file");
        }
        //开始下载
        downloadId = downloadManager.enqueue(request);

        downloadCompleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                checkStatus();
            }

        };
        //注册广播接收者，监听下载完成状态
        this.builder.mContext.registerReceiver(downloadCompleteReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        //开始更新下载进度
        scheduledExecutorService.scheduleAtFixedRate(command, 0, this.builder.progressRefreshTime, TimeUnit.MILLISECONDS);
        downloadStatus = DOWNLOAD_STATUS.DOWNLOADING;

    }


    //检查下载状态
    private void checkStatus() {
        DownloadManager.Query query = new DownloadManager.Query();
        //通过下载的id查找
        query.setFilterById(downloadId);
        Cursor c = downloadManager.query(query);
        if (c.moveToFirst()) {
            int status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));

            switch (status) {

                //下载完成
                case DownloadManager.STATUS_SUCCESSFUL:

                    if (this.builder.listener != null) {
                        this.builder.listener.onProgress(1, 1);

                        this.builder.listener.onSuccess(downloadManager.getUriForDownloadedFile(downloadId));
                    }

                    downloadStatus = DOWNLOAD_STATUS.FINISH;
                    if (this.builder.apkInstallHint && this.builder.fileType == FileType.APK) {

                        showApkInstallHint();

                    }


                    break;
                //下载失败
                case DownloadManager.STATUS_FAILED:

                    if (this.builder.listener != null) {
                        this.builder.listener.onFail();
                    }
                    downloadStatus = DOWNLOAD_STATUS.FAIL;

                    end();
                    break;
            }
        }
        c.close();
    }


    /**
     * 下载完毕后，弹出apk安装提示
     */
    private void showApkInstallHint() {
        //获取下载文件的Uri
        Uri downloadFileUri = downloadManager.getUriForDownloadedFile(downloadId);
        if (downloadFileUri != null) {
            File apkFile = new File(downloadFileUri.getEncodedPath());
            showApkInstallHint(apkFile);
        }
    }


    /**
     * 下载任务完成或失败后，注销广播等操作
     */
    private void end() {
        if (downloadCompleteReceiver != null) {
            try {

                this.builder.mContext.unregisterReceiver(downloadCompleteReceiver);

            } catch (Exception e) {
                e.printStackTrace();
            }
            downloadCompleteReceiver = null;

        }
        scheduledExecutorService.shutdown();




    }


    /**
     * 下载完毕后，弹出对应apk文件安装提示
     *
     * @param apkFile
     */
    private void showApkInstallHint(File apkFile) {


        if (apkFile == null || !apkFile.exists()) {

            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 24) {
            String authority = this.builder.mContext.getPackageName() + ".rxdownload.provider";
            Uri uriForFile = FileProvider.getUriForFile(this.builder.mContext, authority, apkFile);
            intent.setDataAndType(uriForFile, "application/vnd.android.package-archive");

        } else {
            intent.setDataAndType(Uri.fromFile(apkFile), "application/vnd.android.package-archive");

        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        this.builder.mContext.startActivity(intent);

        downloadStatus = DOWNLOAD_STATUS.FINISH;

        end();

    }

    /**
     * 弹出安装提示
     */
    public void installDownloadedApkFile() {

        showApkInstallHint(targetFile);

    }

    /**
     * 由于DownloadManager自身没有提供实时进度的api，所以通过以下定时器计算已下载文件的大小
     */
    private void updateProgress() {

        DownloadBean downloadBean = getDownloadStatus(downloadId);
        if (DownloadHelper.this.builder.listener != null) {

            DownloadHelper.this.builder.listener.onProgress(downloadBean.getDownloadedSize(), downloadBean.getTotalSize());
        }

    }

    /**
     * 通过query查询下载状态，包括已下载数据大小，总大小，下载状态
     *
     * @param downloadId
     * @return
     */
    private int[] getBytesAndStatus(long downloadId) {
        int[] bytesAndStatus = new int[]{
                -1, -1, 0
        };
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


    private DownloadBean getDownloadStatus(long downloadId) {


        DownloadBean downloadBean = new DownloadBean();
        int[] bytesAndStatus = getBytesAndStatus(downloadId);
        downloadBean.setDownloadedSize(bytesAndStatus[0]);
        downloadBean.setTotalSize(bytesAndStatus[1]);
        downloadBean.setStatus(bytesAndStatus[2]);
        return downloadBean;

    }

    private class DownloadBean {

        private int totalSize = 1;
        private int downloadedSize = 0;
        private int status = 0;

        public int getTotalSize() {
            return totalSize;
        }

        public void setTotalSize(int totalSize) {
            this.totalSize = totalSize;
        }

        public int getDownloadedSize() {
            return downloadedSize;
        }

        public void setDownloadedSize(int downloadedSize) {
            this.downloadedSize = downloadedSize;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }
    }


    /**
     * 构造器
     */
    public static class Builder {


        private Context mContext;
        private String title = "下载通知";
        private String downloadUrl;//下载链接
        private String desc = "正在下载文件";
        private String fileSaveName;
        private boolean replaceExistFile = false;//是否删除原有的文件
        private String fileSavePath = "csgstore";//默认路径
        private boolean notifyVisible = true;//是否显示在通知栏
        private FileType fileType = FileType.NORMAL;//文件类型
        private boolean apkInstallHint = false;//apk安装提醒
        private OnDownloadProgressListener listener;
        private long progressRefreshTime = 200;//进度刷新时间

        public Builder(@NonNull Context mContext) {
            this.mContext = mContext;
        }

        public Builder(@NonNull Context mContext, @NonNull String downloadUrl) {

            this(mContext);
            this.downloadUrl = downloadUrl;

        }

        public Builder(@NonNull Context mContext, @NonNull String downloadUrl, @NonNull String fileSaveName) {
            this(mContext, downloadUrl);
            this.fileSaveName = fileSaveName;
        }

        /**
         * @param downloadUrl 下载链接
         * @return
         */
        public Builder downloadUrl(@NonNull String downloadUrl) {
            this.downloadUrl = downloadUrl;
            return this;
        }

        /**
         * @param title 标题
         * @return
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * 并没什么卵用
         *
         * @param refreshTime 进度刷新时间（毫秒）
         * @return
         */
        public Builder refreshTime(long refreshTime) {
            this.progressRefreshTime = refreshTime;
            return this;
        }


        /**
         * @param desc 通知栏描述
         * @return
         */
        public Builder description(String desc) {
            this.desc = desc;

            return this;
        }


        /**
         * @param visible 是否显示在通知栏
         * @return
         */
        public Builder notifyVisible(boolean visible) {

            this.notifyVisible = visible;
            return this;
        }


        /**
         * 设置文件类型
         *
         * @param fileType
         * @return
         */
        public Builder fileType(FileType fileType) {
            this.fileType = fileType;
            return this;
        }

        /**
         * @param replaceExistFile 是否强制删除已下载的同名文件
         * @return
         */
        public Builder replaceExistFile(boolean replaceExistFile) {
            this.replaceExistFile = replaceExistFile;

            return this;
        }

        /**
         * @param apkInstallHint 是否弹出apk安装提示
         * @return
         */
        public Builder apkInstallHint(boolean apkInstallHint) {

            this.apkInstallHint = apkInstallHint;

            return this;
        }

        /**
         * @param fileSaveName 文件保存的名字 如test.apk或/download/test.apk
         * @return
         */
        public Builder fileSaveName(@NonNull String fileSaveName) {

            this.fileSaveName = fileSaveName;

            return this;
        }

        /**
         * @param fileSavePath 文件保存根目录
         * @return
         */
        public Builder fileSavePath(String fileSavePath) {

            this.fileSavePath = fileSavePath;

            return this;
        }

        public Builder onProgressListener(OnDownloadProgressListener listener) {

            this.listener = listener;
            return this;
        }

        public DownloadHelper build() {

            if (TextUtils.isEmpty(downloadUrl)) {
                throw new IllegalStateException("下载链接不能为空！！");
            }
            return new DownloadHelper(this);

        }


    }


    public interface OnDownloadProgressListener {

        void onProgress(int downloadedSize, int totalSize);

        void onSuccess(Uri fileUri);

        void onFail();

        void fileAlreadyExits(File file);

    }


}
