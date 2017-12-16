package ywq.ares.simpledownloadhelper;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private Button btnStart;

    private   DownloadHelper downloadHelper;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progressBar=(ProgressBar)findViewById(R.id.pb_download);
        btnStart=(Button) findViewById(R.id.btn_start);

        btnStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {


                if(downloadHelper==null){

                    btnStart.setText("下载中,点击取消下载");
                    DownloadHelper.Builder builder = new DownloadHelper.Builder(MainActivity.this).title("下载新版本")
                            .description("系在")
                            .downloadUrl("http://imtt.dd.qq.com/16891/B75F4314E7FC8D42F367299FB4104965.apk?fsname=com.tencent.cldts_1.0.13_17000.apk&csr=1bbd")
                            .fileSaveName("test.apk").fileSavePath("csgstore")
                            .notifyVisible(true)
                            .fileType(DownloadHelper.FileType.APK).apkInstallHint(true).onProgressListener(new DownloadHelper.OnDownloadProgressListener() {
                                @Override
                                public void onProgress(int downloadedSize, int totalSize) {

                                    int progress =(int)((downloadedSize*1.0f/totalSize)*100);

                                    Log.d("progress","progress="+progress);
                                    progressBar.setProgress(progress);

                                }

                                @Override
                                public void onSuccess(Uri fileUri) {

                                    btnStart.setText("停止下载并删除文件");
                                    btnStart.setEnabled(true);

                                }

                                @Override
                                public void onFail() {

                                }

                                @Override
                                public void fileAlreadyExits(File file) {
                                    progressBar.setProgress(100);
                                    btnStart.setEnabled(true);

                                    btnStart.setText("停止下载并删除文件");
                                    Toast.makeText(MainActivity.this,"文件已下载",Toast.LENGTH_SHORT).show();
                                }
                            });
                    downloadHelper = builder.build();
                    downloadHelper.start();
                }else{


                    downloadHelper.deleteDownloadFile();
                    progressBar.setProgress(0);
                    btnStart.setEnabled(true);

                    downloadHelper=null;
                    btnStart.setText("重新下载");

                }



            }
        });

    }
}
