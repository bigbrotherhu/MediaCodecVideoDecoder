package com.h264.demo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

public class TestCodecActivity extends AppCompatActivity {

    private String h264Path = "/mnt/sdcard/720pq.h264";
    private File h264File = new File(h264Path);
    private InputStream is = null;
    private FileInputStream fs = null;

    private SurfaceView mSurfaceView;
    private Button mReadButton;

    Thread readFileThread;
    boolean isInit = false;

    // Video Constants
    private final static int VIDEO_WIDTH = 1280;
    private final static int VIDEO_HEIGHT = 720;
    private final static int TIME_INTERNAL = 30;
    private final static int HEAD_OFFSET = 512;

    private MediaCodecVideoDecoder mediaCodecVideoDecoder;
    private int mCount = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_codec);

        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView1);
        mReadButton = (Button) findViewById(R.id.btn_readfile);
        mReadButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (h264File.exists()) {
                    if (!isInit) {
                        initDecoder();
                        isInit = true;
                    }
                    try {
                        Thread.sleep(200);
                        readFileThread = new Thread(readFile);
                        readFileThread.start();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                } else {
                    Toast.makeText(getApplicationContext(),
                            "H264 file not found", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        readFileThread.interrupt();
    }

    public void initDecoder() {
        mediaCodecVideoDecoder = new MediaCodecVideoDecoder();
        mediaCodecVideoDecoder.initDecode(2, VIDEO_WIDTH, VIDEO_HEIGHT, mSurfaceView);
    }


    public boolean onFrame(byte[] buf, int length) {
        int inputBufferIndex = mediaCodecVideoDecoder.dequeueInputBuffer();
        if(inputBufferIndex < 0){
            return false;
        }
        mediaCodecVideoDecoder.queueInputBuffer(inputBufferIndex,buf,length,mCount * TIME_INTERNAL);
        mCount++;
        mediaCodecVideoDecoder.dequeueOutputBuffer();
        return true;
    }

    /**
     * Find H264 frame head
     *
     * @param buffer
     * @param len
     * @return the offset of frame head, return 0 if can not find one
     */
    static int findHead(byte[] buffer, int len) {
        int i;
        for (i = HEAD_OFFSET; i < len; i++) {
            if (checkHead(buffer, i))
                break;
        }
        if (i == len)
            return 0;
        if (i == HEAD_OFFSET)
            return 0;
        return i;
    }

    /**
     * Check if is H264 frame head
     *
     * @param buffer
     * @param offset
     * @return whether the src buffer is frame head
     */
    static boolean checkHead(byte[] buffer, int offset) {
        // 00 00 00 01
        if (buffer[offset] == 0 && buffer[offset + 1] == 0
                && buffer[offset + 2] == 0 && buffer[3] == 1)
            return true;
        // 00 00 01
        if (buffer[offset] == 0 && buffer[offset + 1] == 0
                && buffer[offset + 2] == 1)
            return true;
        return false;
    }

    Runnable readFile = new Runnable() {

        @Override
        public void run() {
            int h264Read = 0;
            int frameOffset = 0;
            byte[] buffer = new byte[100000];
            byte[] framebuffer = new byte[200000];
            boolean readFlag = true;
            try {
                fs = new FileInputStream(h264File);
                is = new BufferedInputStream(fs);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            while (!Thread.interrupted() && readFlag) {
                try {
                    int length = is.available();
                    if (length > 0) {
                        // Read file and fill buffer
                        int count = is.read(buffer);
                        Log.i("count", "" + count);
                        h264Read += count;
                        Log.d("Read", "count:" + count + " h264Read:"
                                + h264Read);
                        // Fill frameBuffer
                        if (frameOffset + count < 200000) {
                            System.arraycopy(buffer, 0, framebuffer,
                                    frameOffset, count);
                            frameOffset += count;
                        } else {
                            frameOffset = 0;
                            System.arraycopy(buffer, 0, framebuffer,
                                    frameOffset, count);
                            frameOffset += count;
                        }

                        // Find H264 head
                        int offset = findHead(framebuffer, frameOffset);
                        Log.i("find head", " Head:" + offset);
                        while (offset > 0) {
                            if (checkHead(framebuffer, 0)) {
                                // Fill decoder
                                boolean flag = onFrame(framebuffer, offset);
                                if (flag) {
                                    byte[] temp = framebuffer;
                                    framebuffer = new byte[200000];
                                    System.arraycopy(temp, offset, framebuffer,
                                            0, frameOffset - offset);
                                    frameOffset -= offset;
                                    Log.e("Check", "is Head:" + offset);
                                    // Continue finding head
                                    offset = findHead(framebuffer, frameOffset);
                                }
                            } else {

                                offset = 0;
                            }

                        }
                        Log.d("loop", "end loop");
                    } else {
                        h264Read = 0;
                        frameOffset = 0;
                        readFlag = false;
                        mediaCodecVideoDecoder.release();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };
}
