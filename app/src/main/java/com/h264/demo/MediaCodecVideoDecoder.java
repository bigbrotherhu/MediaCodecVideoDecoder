package com.h264.demo;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceView;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public class MediaCodecVideoDecoder {

    private static final String VP8_MIME_TYPE = "video/x-vnd.on2.vp8";
    private static final String VP9_MIME_TYPE = "video/x-vnd.on2.vp9";
    private static final String H264_MIME_TYPE = "video/avc";
    private static final String[] supportedVp8HwCodecPrefixes = new String[]{"OMX.qcom.", "OMX.Nvidia.", "OMX.Exynos.", "OMX.Intel."};
    private static final String[] supportedVp9HwCodecPrefixes = new String[]{"OMX.qcom.", "OMX.Exynos."};
    private static final String[] supportedH264HwCodecPrefixes = new String[]{"OMX.qcom.", "OMX.Exynos.", "OMX.rk.", "OMX.sprd.", "OMX.amlogic.", "OMX.IMG.TOPAZ.", "OMX.IMG.MSVDX.", "OMX.hisi.", "OMX.k3.", "OMX.allwinner.", "OMX.MTK.", "OMX.Nvidia.", "OMX.Intel.", "OMX.MS."};
    private static final List<Integer> supportedColorList = Arrays.asList(19, 21, 2141391872, 2141391876);

    private MediaCodec mediaCodec;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;

    /**
     * 获取手机支持的硬件解码器类型以及对应的解码器名称
     */
    private MediaCodecVideoDecoder.DecoderProperties findDecoder(String mime, String[] supportedCodecPrefixes) {
        if (Build.VERSION.SDK_INT < 19) {
            return null;
        } else {
            Log.i("MediaCodecVideoDecoder", "Trying to find HW decoder for mime " + mime);

            for(int i = 0; i < MediaCodecList.getCodecCount(); ++i) {
                MediaCodecInfo info = null;
                try {
                    info = MediaCodecList.getCodecInfoAt(i);
                } catch (IllegalArgumentException var13) {
                    Log.e("MediaCodecVideoDecoder", "Cannot retrieve decoder codec info", var13);
                }

                if (info != null && !info.isEncoder()) {
                    String name = null;
                    String[] var5 = info.getSupportedTypes();
                    int var6 = var5.length;

                    int var7;
                    for(var7 = 0; var7 < var6; ++var7) {
                        String mimeType = var5[var7];
                        if (mimeType.equals(mime)) {
                            name = info.getName();
                            break;
                        }
                    }

                    if (name != null) {
                        Log.i("MediaCodecVideoDecoder", "Found candidate decoder " + name);
                        boolean supportedCodec = false;
                        String[] var15 = supportedCodecPrefixes;
                        var7 = supportedCodecPrefixes.length;

                        int supportedColorFormat;
                        for(supportedColorFormat = 0; supportedColorFormat < var7; ++supportedColorFormat) {
                            String codecPrefix = var15[supportedColorFormat];
                            if (name.startsWith(codecPrefix)) {
                                supportedCodec = true;
                                break;
                            }
                        }

                        if (supportedCodec) {
                            MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(mime);
                            int[] var17 = capabilities.colorFormats;
                            supportedColorFormat = var17.length;

                            int colorFormat;
                            for(int var20 = 0; var20 < supportedColorFormat; ++var20) {
                                colorFormat = var17[var20];
                                Log.d("MediaCodecVideoDecoder", "   Color: 0x" + Integer.toHexString(colorFormat));
                            }

                            if (name.startsWith("OMX.rk.")) {
                                return new MediaCodecVideoDecoder.DecoderProperties(name, 21);
                            }

                            Iterator var19 = supportedColorList.iterator();

                            while(var19.hasNext()) {
                                supportedColorFormat = (Integer)var19.next();
                                int[] var21 = capabilities.colorFormats;
                                colorFormat = var21.length;

                                for(int var11 = 0; var11 < colorFormat; ++var11) {
                                    int codecColorFormat = var21[var11];
                                    if (codecColorFormat == supportedColorFormat) {
                                        Log.d("MediaCodecVideoDecoder", "Found target decoder " + name + ". Color: 0x" + Integer.toHexString(codecColorFormat));
                                        return new MediaCodecVideoDecoder.DecoderProperties(name, codecColorFormat);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Log.d("MediaCodecVideoDecoder", "No HW decoder found for mime " + mime);
            return null;
        }
    }

    public boolean isVp8HwSupported() {
        return findDecoder(VP8_MIME_TYPE, supportedVp8HwCodecPrefixes) != null;
    }

    public boolean isVp9HwSupported() {
        return findDecoder(VP9_MIME_TYPE, supportedVp9HwCodecPrefixes) != null;
    }

    public boolean isH264HwSupported() {
        return findDecoder(H264_MIME_TYPE, supportedH264HwCodecPrefixes) != null;
    }


    /**
     * 初始化解码器
     */
    public boolean initDecode(int codec,int width, int height, SurfaceView surface){
        String mime = null;
        String[] supportedCodecPrefixes = null;
        MediaCodecVideoDecoder.VideoCodecType type = MediaCodecVideoDecoder.VideoCodecType.values()[codec];
        if (type == MediaCodecVideoDecoder.VideoCodecType.VIDEO_CODEC_VP8) {
            mime = "video/x-vnd.on2.vp8";
            supportedCodecPrefixes = supportedVp8HwCodecPrefixes;
        } else if (type == MediaCodecVideoDecoder.VideoCodecType.VIDEO_CODEC_VP9) {
            mime = "video/x-vnd.on2.vp9";
            supportedCodecPrefixes = supportedVp9HwCodecPrefixes;
        } else {
            if (type != MediaCodecVideoDecoder.VideoCodecType.VIDEO_CODEC_H264) {
                throw new RuntimeException("initDecode: Non-supported codec " + type);
            }
            mime = "video/avc";
            supportedCodecPrefixes = supportedH264HwCodecPrefixes;
        }
        MediaCodecVideoDecoder.DecoderProperties properties = findDecoder(mime, supportedCodecPrefixes);
        if (properties == null) {
            throw new RuntimeException("Cannot find HW decoder for " + type);
        } else {
            Log.d("MediaCodecVideoDecoder", "Java initDecode: " + type + ". Color: 0x" + Integer.toHexString(properties.colorFormat));
            try {
                mediaCodec = MediaCodec.createDecoderByType(mime);
                if (mediaCodec == null) {
                    Log.e("MediaCodecVideoDecoder", "Can not create media decoder");
                    return false;
                } else {
                    MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
                    mediaCodec.configure(format,  surface.getHolder().getSurface(), null, 0);//第四个参数为：0 硬解码
                    mediaCodec.start();
                    this.outputBuffers = this.mediaCodec.getOutputBuffers();
                    this.inputBuffers = this.mediaCodec.getInputBuffers();
                    return true;
                }
            } catch (Exception var10) {
                Log.e("MediaCodecVideoDecoder", "initDecode failed", var10);
                return false;
            }
        }
    }

    /**
     * dequeueInputBuffer
     */
    public int dequeueInputBuffer() {
        try {
            return this.mediaCodec.dequeueInputBuffer(10000L);
        } catch (IllegalStateException var2) {
            Log.e("MediaCodecVideoDecoder", "dequeueIntputBuffer failed", var2);
            return -2;
        }
    }
    /**
     *  queueInputBuffer
     */
    public boolean queueInputBuffer(int inputBufferIndex, byte[] buf,int size, long presentationTimeStamUs) {
        try {
            this.inputBuffers[inputBufferIndex].position(0);
            this.inputBuffers[inputBufferIndex].limit(size);
            this.inputBuffers[inputBufferIndex].clear();
            this.inputBuffers[inputBufferIndex].put(buf, 0, size);
            this.mediaCodec.queueInputBuffer(inputBufferIndex, 0, size, presentationTimeStamUs, 0);
            return true;
        } catch (IllegalStateException var10) {
            Log.e("MediaCodecVideoDecoder", "decode failed", var10);
            return false;
        }
    }
    /**
     *  dequeueOutputBuffer
     */
    public boolean dequeueOutputBuffer() {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int outputBufferIndex = this.mediaCodec.dequeueOutputBuffer(bufferInfo, 10000L);
        if (outputBufferIndex == -3) {
            this.outputBuffers = this.mediaCodec.getOutputBuffers();
            Log.i("MediaCodecVideoDecoder", "Decoder output buffers changed: " + this.outputBuffers.length);
            return false;
        }
        if (outputBufferIndex == -2) {
            MediaFormat format = this.mediaCodec.getOutputFormat();
            Log.i("MediaCodecVideoDecoder", "Decoder format changed: " + format.toString());
            return false;
        }
        if (outputBufferIndex == -1) {
            Log.i("MediaCodecVideoDecoder", "Decoder null");
            return false;
        }
        while (outputBufferIndex >= 0) {
            this.mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
            outputBufferIndex = this.mediaCodec.dequeueOutputBuffer(bufferInfo, 10000L);
        }
        Log.i("MediaCodecVideoDecoder", "Decoder end");
        return true;
    }
    /**
     * 回收资源
     */
    public void release() {
        final CountDownLatch releaseDone = new CountDownLatch(1);
        Runnable runMediaCodecRelease = new Runnable() {
            public void run() {
                try {
                    Log.i("MediaCodecVideoDecoder", "Java releaseDecoder on release thread");
                    MediaCodecVideoDecoder.this.mediaCodec.stop();
                    MediaCodecVideoDecoder.this.mediaCodec.release();
                    Log.i("MediaCodecVideoDecoder", "Java releaseDecoder on release thread done");
                } catch (Exception var2) {
                    Log.e("MediaCodecVideoDecoder", "Media decoder release failed", var2);
                }
                MediaCodecVideoDecoder.this.mediaCodec = null;
                releaseDone.countDown();
            }
        };
        (new Thread(runMediaCodecRelease)).start();
        Log.d("MediaCodecVideoDecoder", "Java releaseDecoder done");
    }


    private static class DecoderProperties {
        public final String codecName;
        public final int colorFormat;

        public DecoderProperties(String codecName, int colorFormat) {
            this.codecName = codecName;
            this.colorFormat = colorFormat;
        }
    }


    public enum VideoCodecType {
        VIDEO_CODEC_VP8,
        VIDEO_CODEC_VP9,
        VIDEO_CODEC_H264;

        private VideoCodecType() {
        }
    }

}
