package com.sroom.cslablogger3.devices;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Created by maekawa on 2016/04/28.
 */
public class AndroidCameraFloatingWindowService extends Service {

    WindowManager windowManager; //ウィンドウマネージャーのデータを格納
    LinearLayout L_layout1;		//レイアウトのデータを格納
    TextureView mTextureView;
    WindowManager.LayoutParams params;
    Camera mCamera;
    int _camera_facing;
    int _image_quality;

    boolean touchconsumedbyMove = false;
    int recButtonLastX;
    int recButtonLastY;
    int recButtonFirstX;
    int recButtonFirstY;
    int width=240;
    int height=320;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        Log.d("camera service","start");
        if(intent!=null) {
            _camera_facing = intent.getIntExtra("CAMERA_FACING", 0);
            _image_quality = intent.getIntExtra("IMAGE_QUALITY", 100);
        }else{
            _camera_facing =  0;
            _image_quality = 100;
        }
        //レイアウトをつくる
        L_layout1 = new LinearLayout(this);

        //レイアウトの背景を赤にする
        L_layout1.setBackgroundColor(Color.argb(0, 0, 0, 0));

        mTextureView=new TextureView(this);
        mTextureView.setKeepScreenOn(true);
        mTextureView.setSurfaceTextureListener(new CaptureTextureListener());
        L_layout1.addView(mTextureView);

        //ウィンドウのパラメータを設定
        params = new WindowManager.LayoutParams(
                width,						//横サイズ
                height,						//縦サイズ
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,	//なるべく上の階層で表示
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |	//他のアプリと端末ボタンを操作できる
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN |	//座標系をスクリーンに合わせる
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|	//WATCH_OUTSIDE_TOUCHと同時利用で
                        WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH|	//外側にタッチできる//端末ボタンが無効になる？
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,   //hardware accelerationを利用(TextureView使用のため)
                PixelFormat.TRANSLUCENT); 				//透過指定



        //ウィンドウをドラッグ＆ドロップで移動
        View.OnTouchListener moving = new View.OnTouchListener() {
            //@TargetApi(Build.VERSION_CODES.FROYO)
            @Override
            public boolean onTouch(View v, MotionEvent event) {

                int totalDeltaX = recButtonLastX - recButtonFirstX;
                int totalDeltaY = recButtonLastY - recButtonFirstY;

                switch(event.getActionMasked())
                {
                    case MotionEvent.ACTION_DOWN:
                        recButtonLastX = (int) event.getRawX();
                        recButtonLastY = (int) event.getRawY();
                        recButtonFirstX = recButtonLastX;
                        recButtonFirstY = recButtonLastY;
                        break;
                    case MotionEvent.ACTION_UP:
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int deltaX = (int) event.getRawX() - recButtonLastX;
                        int deltaY = (int) event.getRawY() - recButtonLastY;
                        recButtonLastX = (int) event.getRawX();
                        recButtonLastY = (int) event.getRawY();
                        if (Math.abs(totalDeltaX) >= 5  || Math.abs(totalDeltaY) >= 5) {
                            if (event.getPointerCount() == 1) {
                                params.x += deltaX;
                                params.y += deltaY;
                                touchconsumedbyMove = true;
                                windowManager.updateViewLayout(L_layout1,params);
                            }
                            else{
                                touchconsumedbyMove = false;
                            }
                        }else{
                            touchconsumedbyMove = false;
                        }
                        break;
                    default:
                        break;
                }
                return touchconsumedbyMove;
            }
        };

        L_layout1.setOnTouchListener(moving);
        //ウィンドウマネージャーを使う
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        //ウィンドウに、レイアウトを表示
        windowManager.addView(L_layout1, params);

        return START_STICKY;
    }
    @Override
    public void onCreate() {
        super.onCreate();
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        // サービスを終了するときに画面を消す
        windowManager.removeView(L_layout1);
    }
    @Override
    public IBinder onBind(Intent arg0) {
        // TODO 自動生成されたメソッド・スタブ

        //（注意）必ず戻り値を指定すること
        return null;
    }

    private final class CaptureTextureListener implements TextureView.SurfaceTextureListener {

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            stopVideo();
            return true;
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

            startVideo();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            Bitmap bmp = mTextureView.getBitmap();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, _image_quality, bos);
            //bmp.compress(Bitmap.CompressFormat.PNG, 100, bos);
            byte[] byteArray = bos.toByteArray();

            //ブロードキャストでデータを送る
            Intent i = new Intent();
            i.setAction(Intent.ACTION_CAMERA_BUTTON);
            i.putExtra("img",byteArray);
            getApplicationContext().sendBroadcast(i);
            //LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcastSync(i);
            //Log.d("cameraservice","send"+String.valueOf(byteArray.length));
        }
    }
    private void startVideo() {
        if (mCamera != null) {
            mCamera.release();
        }
            int n = Camera.getNumberOfCameras();

            this.mCamera = null;
            for (int i = 0; i < n; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == this._camera_facing) {
                    mCamera = Camera.open(i);
                }
            }
        //mCamera = Camera.open();
        Log.d("camera","startvideo");
        Camera.Parameters params = mCamera.getParameters();
        params.setPictureSize(height,width);
        mCamera.setParameters(params);

        if(this._camera_facing==0)
            mCamera.setDisplayOrientation(90);
        else if(this._camera_facing==1)
            mCamera.setDisplayOrientation(270);

        if (null != mTextureView) {
            try {
                mCamera.setPreviewTexture(mTextureView.getSurfaceTexture());
            } catch (IOException e) {
                Log.e("a", "Error setting preview texture", e);
                return;
            }
        }
        mCamera.startPreview();

    }

    private void stopVideo() {
        if (null == mCamera)
            return;
        try {
            mCamera.stopPreview();
            mCamera.setPreviewDisplay(null);
            mCamera.setPreviewTexture(null);
            mCamera.release();
        } catch (IOException e) {
            Log.w("a", e);
        }
        mCamera = null;
    }
}
