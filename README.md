# SimpleDownloadHelper
## how to use 
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
