package com.sroom.cslablogger3.devices;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import com.sroom.cslablogger3.AbstractLogger;
import com.sroom.cslablogger3.AbstractLoggerPainter;
import com.sroom.cslablogger3.ArgValue;
import com.sroom.cslablogger3.C;
import com.sroom.cslablogger3.DeviceProfile;
import com.sroom.cslablogger3.IAsyncCallback;
import com.sroom.cslablogger3.ILoggingDeviceBuilder;
import com.sroom.cslablogger3.Profile;
import com.sroom.cslablogger3.R;
import com.sroom.cslablogger3.SingleChoiceSelector;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public final class TriangularSweep implements ILoggingDeviceBuilder {

    @Override
    public DeviceProfile initDeviceProfile() {
        return new DeviceProfile(
                "triangularsweep",
                R.drawable.ic_triangular,
                true,
                new ArgValue[]{
                        new ArgValue("save-data", "1",
                                new SingleChoiceSelector("YES=1,NO=0")),
                        new ArgValue("triangularsweep-count", "8",
                                new SingleChoiceSelector("24=24,20=20,16=16,8=8,6=6,4=4,2=2,1=1")), /*Change the swavesweep count here*/
                        new ArgValue("frequency","18000",
                                new SingleChoiceSelector("250Hz=250,18000Hz=18000,19000Hz=19000,20000Hz=20000,21000Hz=21000")), /*Change the frequency here*/

                }
        );
    }

    @Override
    public AbstractLogger getLogger() {
        return new Logger();
    }

    @Override
    public AbstractLoggerPainter getPainter() {

       int j = Integer.valueOf(initDeviceProfile().getArgValue("triangularsweep-count"));


        triangularsweep thread = new triangularsweep(j);
        thread.start();

            return new Painter();

    }

    ////////////////////////////////////////////////////////////////////////////
    class Logger extends AbstractLogger {

        private SensorManager _manager = null;

        @Override
        public void init(Context context) {
            super.init(context);

            Profile profile = this._profile;
            this.setPackSize(2);
        }

        @Override
        public void start(final IAsyncCallback callback) {
            this._manager =
                    (SensorManager)this._context.getSystemService(Context.SENSOR_SERVICE);
            Sensor sensor = this._manager.getDefaultSensor(Sensor.TYPE_PRESSURE);

            this._manager.registerListener(this.listener, sensor, SensorManager.SENSOR_DELAY_GAME);

            callback.done(true);
        }

        @Override
        public void stop(final IAsyncCallback callback) {

            if (this._manager != null) {
                this._manager.unregisterListener(this.listener);
            }
            this._manager = null;
            callback.done(true);
        }

        @Override
        public void finish() {

        }

        final private SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
            @Override
            public void onSensorChanged(SensorEvent event) {

            }
        };
    }


    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {
        private final float MaxValue = 15.0f;
        private final int CounterMax = 1;
        private final int GraphLength = 40;
        private final int[] COLORS = new int[]{0xFFFF0000};

        private int _counter = 0;
        private int _head = 0;
        private int _fill = 0;

        private float[][] _values = new float[GraphLength][1];




        @Override
        protected boolean setData_impl(Intent intent) {



            boolean onDraw = false;
            this._counter += 1;

            if (this._counter >= CounterMax) {
                this._values[this._head] = intent.getFloatArrayExtra("data");

                this._head += 1;
                if (this._fill < GraphLength) {
                    this._fill = this._head;
                }
                if (this._head >= GraphLength) {
                    this._head = 0;
                    this._fill = GraphLength;
                }
                this._counter = 0;
                onDraw = true;
            }
            return onDraw;
        }


        @Override
        public void draw(Canvas canvas) {
            float dx = (float)canvas.getWidth()  / (GraphLength-1);
            float dh = (float)canvas.getHeight() / 2.0f;
            Paint paint = new Paint();

            canvas.drawRGB(0x00, 0x00, 0x33);
            paint.setColor(0xFF999999);
            canvas.drawLine(0, dh, canvas.getWidth(), dh, paint);

            int offset;
            if (this._fill < GraphLength) {
                offset = 0;
            } else {
                offset = this._head;
            }

            float[] last_values = null;

        }
    }



    ////////////////////////////////////////////////////////////////////////////////////////////////////
    class triangularsweep extends Thread {
        private static final String TAG         = "AudioActivity";
        private AudioTrack audioTrack  = null;
        protected short[]           buffer      = null;
        protected int               SAMPLERATE  = 44100; // [Hz]
        protected static final int  CHANNEL     = 2;     // 1:MONO, 2:STEREO
        protected static final int  BITRATE     = 16;    // [bit/sec]

        // signal funcion params
        private double amplification = 1.0;  // [0.0, 1.0]
        private double frequency = Double.valueOf(initDeviceProfile().getArgValue("frequency"));      // [Hz] Now the user can choose the desired frequency with in the program
        private double duration = 10.0;       // [sec]

        //変数の宣言
        private int multiThreadInt;

        //スレッド作成時に実行される処理
        public triangularsweep(int multiThreadInt){
            this.multiThreadInt = multiThreadInt;
        }

        public void run() {
            generateBuffer();
            int bufferSizeInBytes = buffer.length * CHANNEL * BITRATE / 8;
            // cf
            // Log.v(TAG, "length:" + buffer.length);
            // Log.v(TAG, "bufferSize:" + bufferSizeInBytes);

            // create AudioTrack instance
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
                    SAMPLERATE,  //[Hz]
                    AudioFormat.CHANNEL_OUT_FRONT_LEFT,
                    AudioFormat.ENCODING_PCM_16BIT, //[bit]
                    bufferSizeInBytes, //[byte]
                    AudioTrack.MODE_STATIC);
            //	write buffer
            audioTrack.write(buffer, 0, buffer.length);

            if(audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
//                    audioTrack.stop();
//                    audioTrack.reloadStaticData();
//                    audioTrack.play();
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }

                task sub = new task(audioTrack,multiThreadInt);
                sub.start();



            }
            if(audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING){
                audioTrack.stop();
            }

        }


        public void generateBuffer(){
            short[] sine_buffer      = null;
            short[] sweep_buffer = null;
            int SAMPLES = (int) (duration * SAMPLERATE);
            buffer = new short[ SAMPLES * CHANNEL ];
            double signal = 0;
            double sweep_signal = 0;
            double sine_signal = 0;
            //for (int i = 0; i < SAMPLES; i++) {
            //    signal = generateSignal(i);
            //    buffer[i] = (short)( signal * Short.MAX_VALUE );
            //}
            for (int i = 0; i < SAMPLES; i++) {
                //sine_signal = generateSineSignal(i);
                //sweep_signal = generateSweepSignal(i);
                //signal = (sine_signal + sweep_signal)/2.0; // Should be devided by 2 so that the amplitude remains less than 1
                signal = generateTriangularSignal(i);
                buffer[i] = (short)( signal * Short.MAX_VALUE );
            }
            //for (int i = 0; i < SAMPLES; i++) {
            //    signal = generateSweepSignal(i);
            //    sweep_buffer[i] = (short)( signal * Short.MAX_VALUE );
            //}
            //buffer = sine_buffer + sweep_buffer;
        }

        public double generateSineSignal(int sample) {
            double t = (double)(sample) / SAMPLERATE;
            double signal;
            signal = amplification * Math.sin(2.0 * Math.PI * 22000 * t);
            return signal;
        }

        public double generateSweepSignal(int sample){
            double signal;
            double t = (double)(sample) / SAMPLERATE;
            double fre2 = 21000;
            //frequency = 36000; //Remove after testing
            double t0 = 0.1;
            double a = (fre2-frequency)/t0;
            if(sample<=4410) { //This also changes the length of the sweep
                signal = amplification * Math.sin(2.0 * Math.PI * (a * t * t / 2 + (frequency * t)));
            }
            else{
                signal = 0.0;
            }
            return signal;

        }

        public double generateTriangularSignal(int sample){
            double signal;
            double t = (double)(sample) / SAMPLERATE;
            double fre2 = 21000;
            //frequency = 36000; //Remove after testing
            double t0 = 0.1;
            double a = (fre2-frequency)/t0;
            if(sample<=4410) { //This also changes the length of the sweep
                signal = amplification * Math.sin(2.0 * Math.PI * (a * t * t / 2 + (frequency * t)));
            }
            else{
                signal = amplification * Math.sin(2.0 * Math.PI * (a * t * t / 2 + (fre2 * t)));
            }
            return signal;

        }
        //tachikawa comment
        //音声の定義
        /*public double generateSignal(int sample){

            double t = (double)(sample) / SAMPLERATE;
            //double tt= (double)(sample) / SAMPLERATE;//
            double fre2 = 21000;
            double t0 = duration;
            double t1 = 0.1;//
            //double a = (fre2-frequency)/t0;
            double b = (fre2-frequency)/t1;//
            double sweep_frequency = 18000;
            double signal;
            double sine_signal;
            double sweep_signal = 0.0;
            if(sample<=4410){
                signal = amplification * Math.sin(2.0 * Math.PI * (b * t * t / 2 +( sweep_frequency * t)  ));
                //sweep_signal = amplification * Math.sin(2.0 * Math.PI * (b * t * t / 2 +( sweep_frequency * t)  ));
            }
            //else{
            //    sweep_signal = 0.0;
            //}
            //sine_signal = amplification * Math.sin(2.0 * Math.PI * sweep_frequency * t);
            ////double signal1 = amplification * Math.sin(2.0 * Math.PI * (b * t * t / 2 +( frequency * t)  ));
			else{
                signal = amplification * Math.sin(2.0 * Math.PI * 17000 * t);
            }
            ////double signal = signal1+amplification * Math.sin(2.0 * Math.PI * frequency * t); //sin wave with a frequency of "frequency"
            //signal = sweep_signal + sine_signal;
            return signal;
        }*/

    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////
    class task extends Thread {

        private AudioTrack audio;
        private int multiThreadInt;

        public task(AudioTrack audioTrack, int multiThreadInt){
            audio = audioTrack;
            this.multiThreadInt = multiThreadInt;
        }

        //フォルダ名
        //Calendar cal = Calendar.getInstance();
        //SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        //String strDate = sdf.format(cal.getTime());

        //ファイル名
        //SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd_HHmmss");
        //String strDate2 = sdf2.format(cal.getTime());



        @Override
        public void run() {

                //tachikawa comment
                //swavesweepを出した時刻を記録する
                //フォルダ名
                Calendar cal = Calendar.getInstance();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                String strDate = sdf.format(cal.getTime());

                //ファイル名
                SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String strDate2 = sdf2.format(cal.getTime());

                //ファイル生成
                File DataDir = new File(C.SAVE_DIR + "triangularsweep/" + strDate);
                DataDir.mkdir();
                String FILE = C.SAVE_DIR + "triangularsweep/" + strDate + "/" + strDate2 + ".csv";


                for (int i = 0; i < multiThreadInt; i++) {


                    try {
                        Thread.sleep(500);//Thilina comment: time between each sweep. Change this value to change the time between each sweep.
                    } catch (InterruptedException e) {
                    }

                    audio.stop();
                    audio.reloadStaticData();

                    //テキストに書き込む内容
                    Calendar cal2 = Calendar.getInstance();
                    SimpleDateFormat sdf3 = new SimpleDateFormat("yyyyMMdd_HH:mm:ss.SSS");
                    String strDate3 = sdf3.format(cal2.getTime());


                    try {
                        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                                new FileOutputStream(FILE, true), "UTF-8"));
                        String write_str = strDate3 + "\n";
                        bw.write(write_str);
                        bw.close();
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //tachikawa comment
                    //音声を再生
                    audio.play();

                }

        }
    }

}

