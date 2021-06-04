package com.sroom.cslablogger3.devices;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import com.sroom.cslablogger3.AbstractLogger;
import com.sroom.cslablogger3.AbstractLoggerPainter;
import com.sroom.cslablogger3.AbstractLoggerThread;
import com.sroom.cslablogger3.ArgValue;
import com.sroom.cslablogger3.DeviceProfile;
import com.sroom.cslablogger3.IAsyncCallback;
import com.sroom.cslablogger3.ILoggingDeviceBuilder;
import com.sroom.cslablogger3.Profile;
import com.sroom.cslablogger3.SingleChoiceSelector;
import com.sroom.cslablogger3.TextDataWriter;

import java.util.Date;

import com.sroom.cslablogger3.R;

public final class AndroidLight implements ILoggingDeviceBuilder {

	@Override
	public DeviceProfile initDeviceProfile() {
        return new DeviceProfile(
            "android light",
            R.drawable.ic_androidlight,
            true,
            new ArgValue[] {
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
        private TextDataWriter _csv;
        
        @Override
        public void init(Context context) {
            super.init(context);
            final Logger self = this;
            
            Profile profile = this._profile;
            if (profile.getArgValue("save-data").equals("1")) {
                this._csv = new TextDataWriter("light", "time,light1,light2,light3");
                // this._csv.setVerbose(true);
                this._csv.setIntentConverter(new TextDataWriter.IIntentConverter() {
                        @Override
                        public String convert(Intent intent) {
                            float[] items = intent.getFloatArrayExtra("data");
                            return items[0] + "," + items[1] + "," + items[2];
                        }
                    });
                this._csv.setMultiDirSort(true);
                this._csv.setExceptionCatch(new TextDataWriter.IExceptionCatch() {
                        @Override
                        public void caught(Exception e) {
                            self.onException(new LoggerException("Android Light CSV Error!"));
                            e.printStackTrace();
                        }
                    });
            }
            this.setPackSize(2);
        }
        
        @Override
        public void start(final IAsyncCallback callback) {
            this.registerListener(this._csv);
            this.thread.start();
            callback.done(true);
        }

        @Override
        public void stop(final IAsyncCallback callback) {
            this.unregisterListener(this._csv);
            this.thread.finish(new IAsyncCallback() {
                    @Override
                    public void done(boolean result) {
                        callback.done(result);
                    }
                });
        }
        
        @Override
        public void finish() {
            if (this._csv != null) {
                this._csv.close();
            }
        }
        
        ////////////////////////////////////////////////////////////////////////////
        final AbstractLoggerThread thread = new AbstractLoggerThread() {
                private SensorManager _manager = null;

                private long _saved_time = 0;
                private Intent _saved_value = null;
            
                @Override
                public void begin() {
                    Logger owner = Logger.this;
                    this._manager =
                        (SensorManager)owner._context.getSystemService(Context.SENSOR_SERVICE);
                    Sensor sensor = this._manager.getDefaultSensor(Sensor.TYPE_LIGHT);
                    this._manager.registerListener(this.listener, sensor, SensorManager.SENSOR_DELAY_FASTEST);
                    this.setInterval(250);
                }
            
                @Override
                public void exec() {
                    if (this._saved_value != null) {
                        long now = System.currentTimeMillis();
                        if ((now - this._saved_time) > 50) {
                            Date dt = new Date();
                            sendData(dt, this._saved_value);
                            _saved_time  = System.currentTimeMillis();
                        }
                    }
                }
            
                @Override
                public void end() {
                    this._manager.unregisterListener(this.listener);
                }

                final private SensorEventListener listener = new SensorEventListener() {
                        @Override
                        public void onAccuracyChanged(Sensor sensor, int accuracy) {
                        
                        }
                        @Override
                        public void onSensorChanged(SensorEvent event) {
                            if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                            
                                Date dt = new Date();
                                float light1 = event.values[0];
                                float light2 = event.values[1];
                                float light3 = event.values[2];

                                Intent intent = new Intent();
                                intent.putExtra("data", new float[] {light1, light2, light3});
                                sendData(dt, intent);
                            
                                _saved_time  = System.currentTimeMillis();
                                _saved_value = intent;
                            }                        
                        }                    
                    };            
            };
    }
    
    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {
        private final float MaxValue = 1100.0f;
        private final int GraphLength = 40;
        private final int[] COLORS = new int[] { 0xFFFFFF00, 0xFFFFFF00, 0xFFFFFF00 };
        
        private int _head = 0;
        private int _fill = 0;
        
        private float[][] _values = new float[GraphLength][3];
        
        @Override
        protected boolean setData_impl(Intent intent) {
            this._values[this._head] = intent.getFloatArrayExtra("data");
                
            this._head += 1;
            if (this._fill < GraphLength) {
                this._fill = this._head;
            }
            if (this._head >= GraphLength) {
                this._head = 0;
                this._fill = GraphLength;
            }
            
            return true;
        }
        
        @Override
        public void draw(Canvas canvas) {
            float dx = (float)canvas.getWidth()  / (GraphLength-1);
            float dh = (float)canvas.getHeight();

            Paint paint = new Paint();

            canvas.drawRGB(0x00, 0x00, 0x33);
            
            int offset;
            if (this._fill < GraphLength) {
                offset = 0;
            } else {
                offset = this._head;
            }
            
            float[] last_values = null;
            for (int i = 1; i < GraphLength; i++) {
                
                int i1 = (i + offset + GraphLength - 1) % GraphLength;
                int i2 = (i + offset + GraphLength    ) % GraphLength;
                
                if (i2 >= this._fill) {
                    break;
                }
                float x = i * dx;

                for (int j = 0; j < 1; j++) {
                    float y1 = dh - (this._values[i1][j] / MaxValue) * dh;
                    float y2 = dh - (this._values[i2][j] / MaxValue) * dh;
                    paint.setColor(COLORS[j]);
                    canvas.drawLine(x-dx, y1, x, y2, paint);
                }
                
                last_values = this._values[i2];
            }

            if (last_values != null) {
                paint.setTextSize(20);
                paint.setAntiAlias(true);
                paint.setColor(0xFFFFFFFF);
                paint.setStyle(Paint.Style.STROKE);

                for (int j = 0; j < 1; j++) {
                    String s = String.format("%6.2f", last_values[j]);
                    canvas.drawText(s, 10, 5 + 20 * (j+1), paint);
                }
            }
        }        
    }

}
