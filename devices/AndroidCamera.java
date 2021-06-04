package com.sroom.cslablogger3.devices;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.os.BatteryManager;
import android.os.IBinder;
import android.util.Log;

import com.sroom.cslablogger3.AbstractLogger;
import com.sroom.cslablogger3.AbstractLoggerPainter;
import com.sroom.cslablogger3.AbstractLoggerThread;
import com.sroom.cslablogger3.ArgValue;
import com.sroom.cslablogger3.DeviceProfile;
import com.sroom.cslablogger3.IAsyncCallback;
import com.sroom.cslablogger3.ILoggingDeviceBuilder;
import com.sroom.cslablogger3.JPEGDataWriter;
import com.sroom.cslablogger3.Profile;
import com.sroom.cslablogger3.SingleChoiceSelector;

import java.io.ByteArrayOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.sroom.cslablogger3.R;
import com.sroom.cslablogger3.SqlQuery;
import com.sroom.cslablogger3.sqlImage;

public final class AndroidCamera implements ILoggingDeviceBuilder {
    
	@Override
	public DeviceProfile initDeviceProfile() {
		return new DeviceProfile(
            "android camera",
            R.drawable.ic_androidcamera,
            DeviceProfile.TYPE_BUILTIN,
            new ArgValue[] {
                new ArgValue("camera-facing", "FRONT",
                             new SingleChoiceSelector("BACK,FRONT")),
                new ArgValue("image-quality", "30",
                             new SingleChoiceSelector("high=100,middle=60,low=30")),
                new ArgValue("save-data", "1",
                             new SingleChoiceSelector("YES=1,NO=0")),
            }
        );
	}
    
	@Override
	public AbstractLogger getLogger() {
		return new Logger();
	}
    
	@Override
	public AbstractLoggerPainter getPainter() {
		return new Painter();
	}
    
    ////////////////////////////////////////////////////////////////////////////
    class Logger extends AbstractLogger {
        private JPEGDataWriter _jpg = null;
        private Camera _cam = null;
        private Camera.Size _size = null;
        private int _camera_facing = 0;
        private int _image_quality = 100;
        private Intent camera_service;

        @Override
        public void init(Context context) {
            super.init(context);

            Profile profile = this._profile;
            if (profile.getArgValue("save-data").equals("1")) {
                this._jpg = new JPEGDataWriter("cam2/" + profile.getTag());
            }

            this._camera_facing = CameraInfo.CAMERA_FACING_BACK;
            if (profile.getArgValue("camera-facing").equalsIgnoreCase("front")) {
                this._camera_facing = CameraInfo.CAMERA_FACING_FRONT;
            }
            this._image_quality = Integer.valueOf(profile.getArgValue("image-quality"));
            camera_service = new Intent(context, AndroidCameraFloatingWindowService.class);
            camera_service.putExtra("CAMERA_FACING",this._camera_facing);
            camera_service.putExtra("IMAGE_QUALITY",this._image_quality);

            context.startService(camera_service);

            ///
            ConnectSQL(context,SqlQuery._AndroidCamera);
            ///
        }

        @Override
        public void connect(final IAsyncCallback callback) {
            callback.done(true);
//            int n = Camera.getNumberOfCameras();
//
//            this._cam = null;
//            for (int i = 0; i < n; i++) {
//                CameraInfo info = new CameraInfo();
//                Camera.getCameraInfo(i, info);
//                if (info.facing == this._camera_facing) {
//                    this._cam = Camera.open(i);
//                    this._size = this._cam.getParameters().getPreviewSize();
//                }
//            }
//            callback.done(this._cam != null);
        }

        @Override
        public void start(final IAsyncCallback callback) {
            this.registerListener(this._jpg);
            callback.done(true);
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_CAMERA_BUTTON);
            _context.registerReceiver(this._receiver, filter);
//            if (this._cam != null) {
//                this._cam.setPreviewCallback(this._previewCallback);
//                this._cam.startPreview();
//                callback.done(true);
//            } else {
//                callback.done(false);
//            }

            ///
            TableName(SqlQuery._AndroidCamera);
            ///
        }

        @Override
        public void stop(final IAsyncCallback callback) {
            this.unregisterListener(this._jpg);
//            if (this._cam != null) {
//                this._cam.setPreviewCallback(null);
//                this._cam.stopPreview();
//            }
//            callback.done(true);

            _context.unregisterReceiver(this._receiver);
            _context.stopService(camera_service);
            callback.done(true);

        }

        @Override
        public void disconnect(final IAsyncCallback callback) {
//            if (this._cam != null) {
//                this._cam.release();
//            }
//            this._cam = null;
//
            callback.done(true);
            _service = null;
        }

        @Override
        public void finish() {
            CloseSQL();
        }

        @Override
        protected void SqlCreateTable(){
            String[] columnname=new String[]{"image","timestamp"};//timestamp,image
            String[] columntype=new String[]{SqlQuery._BINARY,SqlQuery._DATETIME};//バイナリの型
            this.sqlQuery.CreateTable(tablename, columnname, columntype);
        }

        final private BroadcastReceiver _receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!intent.getAction().equals(Intent.ACTION_CAMERA_BUTTON)) return;
                try {
                    final Logger logger = Logger.this;

                    byte[] data = intent.getByteArrayExtra("img");
                    Log.d("cameradata",String.valueOf(data.length));
                    if(data!=null&&data.length>0) {
                        Date dt = new Date();

                        Intent i = new Intent();
                        i.putExtra("data", data);

                        ///SQL操作
                        CreateTable();


                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");

                        AddData(sdf.format(dt),data);
                        ///

                        logger.sendData(dt, i);
                    }
                } catch (Exception e) {
                }


            }
        };

//        private Camera.PreviewCallback _previewCallback = new Camera.PreviewCallback() {
//                @Override
//                public void onPreviewFrame(byte[] bytes, Camera camera) {
//                    final Logger logger = Logger.this;
//                    final int width  = logger._size.width;
//                    final int height = logger._size.height;
//                    if (logger._cam != null) {
//                        logger._cam.setPreviewCallback(null);
//
//                        int[] rgb = new int[width * height]; // ARGB8888
//                        try {
//                            Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
//                            decodeYUV420SP(rgb, bytes, width, height);
//                            bmp.setPixels(rgb, 0, width, 0, 0, width, height);
//
//                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
//                            if (bmp.compress(Bitmap.CompressFormat.JPEG, logger._image_quality, stream)) {
//                                byte[] data = stream.toByteArray();
//
//                                Date dt = new Date();
//                                Intent intent = new Intent();
//                                intent.putExtra("data", data);
//
//                                logger.sendData(dt, intent);
//                            }
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }
//                        if (logger._cam != null) {
//                            logger._cam.setPreviewCallback(this);
//                        }
//                    }
//                }
//            };

        /**
         * YUV420データをBitmapに変換します
         *
         * @param rgb
         * @param yuv420sp
         * @param width
         * @param height
         */
        // https://groups.google.com/group/android-sdk-japan/browse_thread/thread/09f3545c7f7cfdac/018d2eb85fb9cb44?hl=ja
        // YUV420 to BMP
        private void decodeYUV420SP(int[] rgb, byte[] yuv420sp, int width, int height) {
            final int frameSize = width * height;

            for (int j = 0, yp = 0; j < height; j++) {
                int uvp = frameSize + (j >> 1) * width, u = 0, v = 0;
                for (int i = 0; i < width; i++, yp++) {
                    int y = (0xff & ((int) yuv420sp[yp])) - 16;
                    if (y < 0) y = 0;
                    if ((i & 1) == 0) {
                        v = (0xff & yuv420sp[uvp++]) - 128;
                        u = (0xff & yuv420sp[uvp++]) - 128;
                    }
                    int y1192 = 1192 * y;
                    int r = (y1192 + 1634 * v);
                    int g = (y1192 - 833 * v - 400 * u);
                    int b = (y1192 + 2066 * u);
                    if (r < 0) r = 0;
                    else if (r > 262143) r = 262143;
                    if (g < 0) g = 0;
                    else if (g > 262143) g = 262143;
                    if (b < 0) b = 0;
                    else if (b > 262143) b = 262143;

                    rgb[yp] = 0xff000000 | ((r << 6) & 0xff0000) |
                            ((g >> 2) & 0xff00) | ((b >> 10) & 0xff);
                }
            }
        }
    }
    
    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {
        
        private Bitmap _bitmap = null;
        
        @Override
        protected boolean setData_impl(Intent intent) {
            boolean onDraw = false;
            byte[] data = intent.getByteArrayExtra("data");
            Log.d("camerapainter",String.valueOf(data.length));
            if (data != null) {
                this._bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                onDraw = true;
            }
            return onDraw;        
        }
        
        @Override
        public void draw(Canvas canvas) {
            int src_width  = this._bitmap.getWidth();
            int src_height = this._bitmap.getHeight();
            int dst_width  = canvas.getWidth();
            int dst_height = canvas.getHeight();

            Rect src = new Rect(0, 0, src_width, src_height);

            Rect dst;
            if (src_width < src_height) {
                double zoom = src_height / (double)src_width;
                int width  = dst_width;
                int height = (int)(dst_width * zoom);
                int top = (dst_height - height) / 2;
                dst = new Rect(0, top, width, top+height);
            } else {
                double zoom = src_width / (double)src_height;
                int width  = (int)(dst_height * zoom);
                int height = dst_height;
                
                int left = (dst_width - width) / 2;
                dst = new Rect(left, 0, left+width, height);
            }
            
            Paint paint = new Paint();
            canvas.drawBitmap(this._bitmap, src, dst, paint);
        }        
    }    
}
