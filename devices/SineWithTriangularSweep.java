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
import android.util.Log;

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
import java.util.Arrays;
import java.util.stream.Stream;

import java.lang.*;

public final class SineWithTriangularSweep implements ILoggingDeviceBuilder {

    @Override
    public DeviceProfile initDeviceProfile() {
        return new DeviceProfile(
                "sinewithtriangularsweep",
                R.drawable.sinetriangular,
                true,
                new ArgValue[]{
                        new ArgValue("save-data", "1",
                                new SingleChoiceSelector("YES=1,NO=0")),
                        new ArgValue("swave-count", "1",
                                new SingleChoiceSelector("24=24,20=20,16=16,8=8,6=6,4=4,2=2,1=1")), /*Change the swave count here*/
                        new ArgValue("frequency","18000",
                                new SingleChoiceSelector("20Hz=20,30Hz=30,50Hz=50,75Hz=75,250Hz=250")), /*Change the frequency here*/

                }
        );
    }

    @Override
    public AbstractLogger getLogger() {
        return new Logger();
    }

    @Override
    public AbstractLoggerPainter getPainter() {

       int j = Integer.valueOf(initDeviceProfile().getArgValue("swave-count"));


        sinewithtriangularsweep thread = new sinewithtriangularsweep(j);
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
    class sinewithtriangularsweep extends Thread {
        private static final String TAG         = "AudioActivity";
        private AudioTrack audioTrack  = null;
        protected short[]           buffer      = null;
        protected int               SAMPLERATE  = 44100;//44100; // [Hz]
        protected static final int  CHANNEL     = 1;     // 1:MONO, 2:STEREO // Keep this as one for this application
        protected static final int  BITRATE     = 16;    // [bit/sec]

        // signal funcion params
        private double amplification = 1.0;  // [0.0, 1.0]
        private double frequency = Double.valueOf(initDeviceProfile().getArgValue("frequency"));      // [Hz] Now the user can choose the desired frequency with in the program
        private double duration = 10.0;       // [sec]

        //変数の宣言
        private int multiThreadInt;

        //スレッド作成時に実行される処理
        public sinewithtriangularsweep(int multiThreadInt){
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
                    AudioFormat.CHANNEL_OUT_MONO,//CHANNEL_CONFIGURATION_MONO,
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

            //short[] sweep_buffer;
            //short[] inv_sweep_buffer;
            double sweep_len = 0.05; //in seconds
            double sine_len = 0.25; //in seconds
            int sine_samples = (int) (sine_len * SAMPLERATE);
            int sweep_samples = (int) (sweep_len * SAMPLERATE);
            int inv_sweep_samples = (int) (sweep_len * SAMPLERATE);

            short sine18_buffer[] = new short[sine_samples*CHANNEL];
            short sine21_buffer[] = new short[sine_samples*CHANNEL];
            short sweep_buffer[] = new short[sweep_samples*CHANNEL];
            short inv_sweep_buffer[] = new short[inv_sweep_samples*CHANNEL];
            //sine_buffer = new short[ sine_samples * CHANNEL ];
            //sweep_buffer = new short[ sweep_samples * CHANNEL ];
            //inv_sweep_buffer = new short[ inv_sweep_samples * CHANNEL ];
            //buffer = new short[(sine_buffer.length+sweep_buffer.length)];

            double inv_sweep_signal = 0;
            double sweep_signal = 0;
            double sine18_signal = 0;
            double sine21_signal = 0;
            //for (int i = 0; i < SAMPLES; i++) {
            //    signal = generateSignal(i);
            //    buffer[i] = (short)( signal * Short.MAX_VALUE );
            //}
            for (int i = 0; i < sine_samples; i++) {
                sine18_signal = generateSine18Signal(i, sine_len);
                sine18_buffer[i] = (short)( sine18_signal * Short.MAX_VALUE );
            }

            for (int i = 0; i < sweep_samples; i++) {
                sweep_signal = generateSweepSignal(i, sweep_len);
                sweep_buffer[i] = (short)( sweep_signal * Short.MAX_VALUE );
            }

            for (int i = 0; i < sine_samples; i++) {
                sine21_signal = generateSine21Signal(i, sine_len);
                sine21_buffer[i] = (short)( sine21_signal * Short.MAX_VALUE );
            }

            for (int i = 0; i < inv_sweep_samples; i++) {
                inv_sweep_signal = generateInvSweepSignal(i, sweep_len);
                inv_sweep_buffer[i] = (short)( inv_sweep_signal * Short.MAX_VALUE );
            }

            //buffer = sine_buffer + sweep_buffer;
            int s21_len = sine21_buffer.length;
            int s18_len = sine18_buffer.length;
            int sw_len = sweep_buffer.length;
            int isw_len = inv_sweep_buffer.length;
            //System.arraycopy(sine_buffer,0, buffer, 0, fal);
            //System.arraycopy(sweep_buffer,0, buffer,fal,sal);
            int num_rep = 17;
            short[] result = new short[(s18_len+sw_len+isw_len+s21_len)*num_rep];

            for (int i=0; i<num_rep; i++) {
                System.arraycopy(sine18_buffer, 0, result, (s18_len+sw_len+s21_len+isw_len)*i, s18_len);
                System.arraycopy(sweep_buffer, 0, result, s18_len*(i+1) + (sw_len+s21_len+isw_len)*i, sw_len);
                System.arraycopy(sine21_buffer, 0, result, (s18_len+sw_len)*(i+1) + (s21_len+isw_len)*i, s21_len);
                System.arraycopy(inv_sweep_buffer, 0, result, (s18_len+sw_len+s21_len)*(i+1) + (isw_len)*i, isw_len);
            }
            /*System.arraycopy(sine_buffer, 0, result, 0, fal);
                System.arraycopy(sweep_buffer, 0, result, fal, sal);
                System.arraycopy(inv_sweep_buffer, 0, result, fal + sal, tal);*/
            //System.arraycopy(sine_buffer,0,result, fal+sal+tal,fal);
            buffer = result;
        }


        public double generateSine18Signal(int sample, double sine_len) {
            double t = (double)(sample) / SAMPLERATE;
            double signal;
            signal = amplification * Math.sin(2.0 * Math.PI * 18000 * t);
            return signal;
        }

        public double generateSine21Signal(int sample, double sine_len) {
            double t = (double)(sample) / SAMPLERATE;
            double signal;
            signal = amplification * Math.sin(2.0 * Math.PI * 21000 * t);
            return signal;
        }

        public double generateSweepSignal(int sample, double sweep_len){
            double signal;
            double t = (double)(sample) / SAMPLERATE;
            double fre2 = 21000;
            //frequency = 36000; //Remove after testing
            double t0 = sweep_len;//1;//0.001;
            double a = (fre2-frequency)/t0;
            signal = amplification * Math.sin(2.0 * Math.PI * (a * t * t / 2 + (frequency * t)));
            return signal;
        }

        public double generateInvSweepSignal(int sample, double sweep_len){
            double signal;
            double t = (double)(sample) / SAMPLERATE;
            double fre2 = 21000;
            //frequency = 36000; //Remove after testing
            double t0 = sweep_len;
            double a = (fre2-frequency)/t0;
            signal = amplification * Math.sin(2.0 * Math.PI * (((frequency - fre2)/t0) * t * t / 2 + (fre2 * t)));
            return signal;
        }

        public double generateSineTriangularSweep(int sample){
            double signal = 0;
            //double t = (double)(sample) / SAMPLERATE;
            double fre2 = 21000;
            //frequency = 36000; //Remove after testing
            double t0 = 0.001;
            double t1 = 0.25;
            double a = (fre2-frequency)/t0;
            int basic_sig_len =  (int) (SAMPLERATE*(t1 + t0*2)); //This also changes the sine wave length
            int quotient = sample/basic_sig_len;
            int remainder = sample%basic_sig_len;
            double t = (double)(remainder) / SAMPLERATE;

            //quotient = Math.floor(quotient);
            //Log.d("sweeplen", "Sweep length = "+sweep_len);
            Log.d("sample", "Sample = "+sample);
            Log.i("info", "Quotient = "+quotient);
            Log.i("info", "Remainder = "+quotient%2);
            //quotient%2 == 0
            if(quotient%4 == 0) { //This also changes the length of the sweep
                signal = amplification * Math.sin(2.0 * Math.PI * 18000 * t1);
            }

            else if(quotient%4 == 1) {
                signal = amplification * Math.sin(2.0 * Math.PI * (a * t * t / 2 + (frequency * t)));
            }

            //else if(quotient%4 == 1){
            /*if (quotient%2 == 0) { //This also changes the length of the sweep
                signal = amplification * Math.sin(2.0 * Math.PI * 21000 * t);
            }*/

            //else if (quotient%4 == 2){
            else if (quotient%4 == 2){
                signal = amplification * Math.sin(2.0 * Math.PI * (((frequency - fre2)/t0) * t * t / 2 + (fre2 * t)));
            }

            if(quotient%4 == 3) { //This also changes the length of the sweep
                signal = amplification * Math.sin(2.0 * Math.PI * 18000 * t1);
            }

            //else if (quotient%4 == 3){
            /*else if (quotient%2 == 1){
                signal = amplification * Math.sin(2.0 * Math.PI * 18000 * t);
            }*/

            /*if(quotient%20 >= 0 && quotient%20 < 10) {
                signal = amplification * Math.sin(2.0 * Math.PI * (a * t * t / 2 + (frequency * t)));
            }*/
            //if (quotient%20 >= 0 && quotient%20 < 10) { //This also changes the length of the sweep
            //signal = amplification * Math.sin(2.0 * Math.PI * 18000 * t);
            //}

            //else if (quotient%4 == 2){
            /*else if (quotient%20 >= 10 && quotient%20 < 20){
                signal = amplification * Math.sin(2.0 * Math.PI * (((frequency - fre2)/t0) * t * t / 2 + (fre2 * t)));
            }*/

            //else if (quotient%20 >= 10 && quotient%20 < 20){
            //signal = amplification * Math.sin(2.0 * Math.PI * 18000 * t);
            //}




            return signal;

        }

        //tachikawa comment
        //音声の定義
        public double generateSignal(int sample){

            double t = (double)(sample) / SAMPLERATE;
            //double tt= (double)(sample) / SAMPLERATE;//
            double fre2 = 96000;
            double t0 = duration;
            //double t1 = 0.1;//
            double a = (fre2-frequency)/t0;
            //double b = (fre2-frequency)/t1;//
            //double signal1 = amplification * Math.sin(2.0 * Math.PI * (a * t * t / 2 +( frequency * t)  ));
			double signal = amplification * Math.sin(2.0 * Math.PI * frequency * t); //sin wave with a frequency of "frequency"

            return signal;
        }

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
                //swaveを出した時刻を記録する
                //フォルダ名
                Calendar cal = Calendar.getInstance();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                String strDate = sdf.format(cal.getTime());

                //ファイル名
                SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String strDate2 = sdf2.format(cal.getTime());

                //ファイル生成
                File DataDir = new File(C.SAVE_DIR + "sinewithtriangularsweep/" + strDate);
                DataDir.mkdir();
                String FILE = C.SAVE_DIR + "sinewithtriangularsweep/" + strDate + "/" + strDate2 + ".csv";


                for (int i = 0; i < multiThreadInt; i++) {


                    try {
                        Thread.sleep(500);
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

