package com.sroom.cslablogger3.devices;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import com.sroom.cslablogger3.AbstractAudioDataWriter;
import com.sroom.cslablogger3.AbstractLogger;
import com.sroom.cslablogger3.AbstractLoggerPainter;
import com.sroom.cslablogger3.AbstractLoggerThread;
import com.sroom.cslablogger3.ArgValue;
import com.sroom.cslablogger3.C;
import com.sroom.cslablogger3.DeviceProfile;
import com.sroom.cslablogger3.IAsyncCallback;
import com.sroom.cslablogger3.ILoggingDeviceBuilder;
import com.sroom.cslablogger3.MP3DataWriter;
import com.sroom.cslablogger3.Profile;
import com.sroom.cslablogger3.SingleChoiceSelector;
import com.sroom.cslablogger3.WAVDataWriter;
import com.sroom.cslablogger3.WAVZIPDataWriter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.sroom.cslablogger3.R;

public final class AndroidMicrophone implements ILoggingDeviceBuilder {

    @Override
    public DeviceProfile initDeviceProfile() {
        return new DeviceProfile(
            "android microphone",
            R.drawable.ic_androidmicrophone,
            true,
            new ArgValue[] {
                new ArgValue("samplerate", "192000",
                                 new SingleChoiceSelector("384kHz=384000,192kHz=192000,96KHz=96000,48KHz=48000,44.1KHz=44100,8000Hz=8000")),
                new ArgValue("rec-time", "50",
                                 new SingleChoiceSelector("50msec=50,100msec=100,200msec=200")),
                new ArgValue("interval", "1000",
                                 new SingleChoiceSelector("100msec=100,250msec=250,500msec=500,1sec=1000,1.5sec=1500,2sec=2000,10sec=10000,20sec=20000,60sec=60000")),
                new ArgValue("amplitude", "1",
                                 new SingleChoiceSelector("0db=1")),
                new ArgValue("use-bluetooth-headset", "0",
                                 new SingleChoiceSelector("ON=1,OFF=0")),
                new ArgValue("recording-mode", "1",
                             new SingleChoiceSelector("SEQUENCE=1,SNAPSHOT=0")),
                new ArgValue("sequence-time", "60",
                             new SingleChoiceSelector("5sec=5,10sec=10,15sec=15,30sec=30,60sec=60,120sec=120")),
                new ArgValue("file-type", "wav",
                             new SingleChoiceSelector("wav")),//"mp3,wav,zip")), //disabling mp3 and zip for the time being
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
        private AbstractAudioDataWriter _adw = null;
        private int _samplerate = 8000;
        private int _rec_time = 50;
        private int _interval = 1000;
        private int _amplitude = 1;
        private boolean _use_bluetooth_headset = false;
        private boolean _sequence_recording_mode = false;
        private int _sequence_time = 30;
        private RecThread _thread = null;

        @Override
        public void init(Context context) {
            super.init(context);

            Profile profile = this._profile;

            this._samplerate = Integer.valueOf(profile.getArgValue("samplerate"));
            this._rec_time   = Integer.valueOf(profile.getArgValue("rec-time"));
            this._interval   = Integer.valueOf(profile.getArgValue("interval"));
            this._amplitude  = Integer.valueOf(profile.getArgValue("amplitude"));
            this._use_bluetooth_headset = profile.getArgValue("use-bluetooth-headset").equals("1");
            this._sequence_recording_mode = profile.getArgValue("recording-mode").equals("1");
            this._sequence_time = Integer.valueOf(profile.getArgValue("sequence-time"));

            if (profile.getArgValue("save-data").equals("1")) {
                if (profile.getArgValue("file-type").equals("wav")) {
                    this._adw = new WAVDataWriter("wav", this._samplerate);
                } else if (profile.getArgValue("file-type").equals("zip")) {
                    this._adw = new WAVZIPDataWriter("wav", this._samplerate);
                } else {
                    this._adw = new MP3DataWriter("wav", this._samplerate);
                }
            }
        }

        @Override
        public void start(final IAsyncCallback callback) {
            AudioManager audioManager = (AudioManager)this._context.getSystemService("audio");
            audioManager.setBluetoothScoOn(false);
            audioManager.stopBluetoothSco();
            if (this._use_bluetooth_headset) {
                Log.d("CSLabLogger", "use-bluetooth-headset");
                audioManager.startBluetoothSco();
            }
            audioManager.setBluetoothScoOn(true);

            switch (audioManager.getMode()) {
            case AudioManager.MODE_NORMAL:
                Log.d("CSLabLogger", "MODE_NORMAL"); break;
            case AudioManager.MODE_IN_CALL:
                Log.d("CSLabLogger", "MODE_IN_CALL"); break;
            case AudioManager.MODE_RINGTONE:
                Log.d("CSLabLogger", "MODE_RINGTONE"); break;
            default:
                Log.d("CSLabLogger", "MODE is UNKNOWN"); break;
            }

            switch (audioManager.getRouting(AudioManager.MODE_NORMAL)) {
            case AudioManager.ROUTE_SPEAKER:
                Log.d("CSLabLogger", "ROUTE_SPEAKER"); break;
            case AudioManager.ROUTE_BLUETOOTH:
                Log.d("CSLabLogger", "ROUTE_BLUETOOTH"); break;
            case AudioManager.ROUTE_HEADSET:
                Log.d("CSLabLogger", "ROUTE_HEADSET"); break;
            case AudioManager.ROUTE_EARPIECE:
                Log.d("CSLabLogger", "ROUTE_EARPIECE"); break;
            default:
                Log.d("CSLabLogger", "ROUTE is UNKNOWN:" + audioManager.getRouting(AudioManager.MODE_NORMAL)); break;
            }

            // this.registerListener(this._adw);
            if (this._sequence_recording_mode) {
                this._thread = new SequenceRecThread();
            } else {
                this._thread = new SnapShotRecThread();
            }
            this._thread.start();
            callback.done(true);
        }

        @Override
        public void stop(final IAsyncCallback callback) {
            // this.unregisterListener(this._adw);
            this._thread.finish(new IAsyncCallback() {
                    @Override
                    public void done(boolean result) {
                        callback.done(result);
                    }
                });
        }

        ////////////////////////////////////////////////////////////////////////////
        abstract class RecThread extends AbstractLoggerThread {
            protected AbstractAudioDataWriter _adw = null;
            protected AudioRecord _rec = null;
            protected short[] _buffer = null;

            public RecThread() {
                final Logger owner = Logger.this;

                int audio_len = (int)owner._samplerate * owner._rec_time / 1000;
                int audio_min_len =
                    AudioRecord.getMinBufferSize(owner._samplerate,
                                                 AudioFormat.CHANNEL_IN_STEREO,//Thilina
                                                 AudioFormat.ENCODING_PCM_16BIT);
                if (audio_len < audio_min_len) audio_len = audio_min_len;

                this._buffer = new short[audio_len];
                this._rec = new AudioRecord(MediaRecorder.AudioSource.MIC,
                                            owner._samplerate,
                                            AudioFormat.CHANNEL_IN_STEREO,//Thilina
                                            AudioFormat.ENCODING_PCM_16BIT, this._buffer.length * 2); //This configuration writes input from both mics into one buffer in order of L,L,R,R,L,L,R,R,...
                this._adw = owner._adw;
            }

            @Override public void begin() {}
            @Override public void exec() {}
            @Override public void end() {
                if (this._adw != null) {
                    while (this._adw.isWriting()) {
                        try {
                            Thread.sleep(250);
                        } catch (InterruptedException e) {}
                    }
                }
            }

            protected double[] byte2double(short[] b, int len) {
                double[] result = new double[len];
                for (int i = 0; i < len; i++) {
                    result[i] = b[i];
                }
                return result;
            }
        }

        class SequenceRecThread extends RecThread {
            @Override
            public void begin() {

                Calendar cal = Calendar.getInstance();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                String strDate = sdf.format(cal.getTime());

                //ファイル名
                SimpleDateFormat sdf2 = new SimpleDateFormat("yyyyMMdd_HHmmss");
                String strDate2 = sdf2.format(cal.getTime());

                SimpleDateFormat aud_start = new SimpleDateFormat("yyyyMMdd_HH:mm:ss.SS");
                String astart = aud_start.format(cal.getTime());

                final Logger owner = Logger.this;

                this._rec.startRecording();

                int signal_index = 0;
                long signal_start_time = 0;
                short[] signal = new short[owner._samplerate * owner._sequence_time];

                long prev_rec = 0;
                int interval = owner._interval;

                while (! this.isFinished()) {
                    Date dt = new Date();
                    int len = this._rec.read(this._buffer, 0, this._buffer.length);

                    long now = dt.getTime();
                    if (now - prev_rec >= interval) {
                        double[] audioData = byte2double(this._buffer, len);
                        for (int i = 0; i < audioData.length; i++) {
                            audioData[i] *= owner._amplitude;
                        }
                        Intent intent = new Intent();
                        intent.putExtra("data", audioData);
                        sendData(dt, intent);
                        prev_rec = now;
                    }

                    if (this._adw != null) {
                        for (int i = 0; i < len; i++) {
                            if (signal_index == 0) {
                                signal_start_time = now;
                            }
                            signal[signal_index++] = this._buffer[i];
                            if (signal_index >= signal.length) {
                                short[] clone = signal.clone();
                                Intent intent2 = new Intent();
                                intent2.putExtra("dt", signal_start_time);
                                intent2.putExtra("samplerate", owner._samplerate);
                                intent2.putExtra("buffer", clone);
                                this._adw.onReceive(0, intent2);
                                signal_index = 0;
                            }
                        }
                    }
                }
                if (this._adw != null) {
                    for (int i = signal_index; i < signal.length; i++) {
                        signal[i] = 0;
                    }
                    Date dt = new Date();
                    long now = dt.getTime();
                    Intent intent2 = new Intent();
                    intent2.putExtra("dt", now);
                    intent2.putExtra("samplerate", owner._samplerate);
                    intent2.putExtra("buffer", signal);
                    this._adw.onReceive(0, intent2);
                }

                this._rec.stop();
                Calendar cal2 = Calendar.getInstance();
                SimpleDateFormat aud_stop = new SimpleDateFormat("yyyyMMdd_HH:mm:ss.SSS");
                String astop = aud_stop.format(cal2.getTime());
                File DataDir = new File(C.SAVE_DIR + "aud_times/" + strDate);
                DataDir.mkdir();
                String FILE = C.SAVE_DIR + "aud_times/" + strDate + "/" + strDate2 + ".csv";


                try {
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                            new FileOutputStream(FILE, true), "UTF-8"));
                    String write_str = astart+ "-" + astop + "\n";
                    bw.write(write_str);
                    bw.close();
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        class SnapShotRecThread extends RecThread {
            @Override
            public void exec() {
                final Logger owner = Logger.this;

                Date dt = new Date();

                this._rec.startRecording();
                int len = this._rec.read(this._buffer, 0, this._buffer.length);
                this._rec.stop();

                if (len > 0) {
                    double[] audioData = byte2double(this._buffer, len);
                    for (int i = 0; i < audioData.length; i++) {
                        audioData[i] *= owner._amplitude;
                    }
                    Intent intent = new Intent();
                    intent.putExtra("data", audioData);
                    sendData(dt, intent);

                    if (this._adw != null) {
                        Intent intent2 = new Intent();
                        intent2.putExtra("dt", dt.getTime());
                        intent2.putExtra("samplerate", owner._samplerate);
                        intent2.putExtra("buffer", this._buffer);
                        this._adw.onReceive(0, intent2);
                    }

                    this.setInterval(owner._interval);
                } else {
                    this.setInterval(owner._interval * 3);
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {
        double[] _data = null;

        @Override
        protected boolean setData_impl(Intent intent) {
            this._data = intent.getDoubleArrayExtra("data");
            return true;
        }

        @Override
        public void draw(Canvas canvas) {
            float dx = (float)canvas.getWidth() / this._data.length;
            float dh = (float)canvas.getHeight() / 2.0f;
            Paint paint = new Paint();
            paint.setColor(0xFFFFFFFF);

            canvas.drawRGB(0x00, 0x00, 0x33);

            for (int i = 1; i < this._data.length; i++) {
                float x2 = i * dx;
                float x1 = x2 - dx;

                float y1 = (float)(dh - (this._data[i-1]/16384) * dh);
                float y2 = (float)(dh - (this._data[i  ]/16384) * dh);

                canvas.drawLine(x1, y1, x2, y2, paint);
            }
        }
    }
}
