package com.sroom.cslablogger3.devices;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.preference.PreferenceManager;

import com.sroom.cslablogger3.AbstractLogger;
import com.sroom.cslablogger3.AbstractLoggerPainter;
import com.sroom.cslablogger3.ArgValue;
import com.sroom.cslablogger3.DeviceProfile;
import com.sroom.cslablogger3.IAsyncCallback;
import com.sroom.cslablogger3.ILoggingDeviceBuilder;
import com.sroom.cslablogger3.Profile;
import com.sroom.cslablogger3.SingleChoiceSelector;
import com.sroom.cslablogger3.Sql;
import com.sroom.cslablogger3.SqlQuery;
import com.sroom.cslablogger3.TaskQueue;
import com.sroom.cslablogger3.TextDataWriter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import com.sroom.cslablogger3.R;

public final class AndroidAccelerometer implements ILoggingDeviceBuilder {

	@Override
	public DeviceProfile initDeviceProfile() {
		return new DeviceProfile(
            "android accelerometer",
            R.drawable.ic_androidaccelerometer,
            true,
            new ArgValue[] {
                new ArgValue("samplerate", "10000",
                            new SingleChoiceSelector("5Hz=200000,10Hz=100000,15.625Hz=64000,20Hz=50000,25Hz=40000,31.25Hz=32000,50Hz=20000,62.5Hz=16000,100Hz=10000,125Hz=8000,200Hz=5000,250Hz=4000")),
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
        private TextDataWriter _csv = null;
        private SensorManager _manager = null;
        private int _samplerate = 10000;
        private long _timeStampOffset = Long.MAX_VALUE;
        
        @Override
        public void init(Context context) {
            super.init(context);
            final Logger self = this;
            
            Profile profile = this._profile;
            this._samplerate = Integer.valueOf(profile.getArgValue("samplerate"));

            if (profile.getArgValue("save-data").equals("1")) {
                this._csv = new TextDataWriter("acc2", "time,x,y,z");
                // this._csv.setVerbose(true);
                this._csv.setSplitSeconds(30);
                this._csv.setIntentConverter(new TextDataWriter.IIntentConverter() {
                        @Override
                        public String convert(Intent intent) {
                            float[] items = intent.getFloatArrayExtra("data");
                            return items[0] + "," + items[1] + "," + items[2];
                        }
                    });
                this._csv.setFileRename(new TextDataWriter.IFileRename() {
                        @Override
                        public String rename(Date dt) {
                            return String.format("%s_acc2.csv",
                                                 TextDataWriter.FilenameDateFormat.format(dt));
                        }
                    });
                this._csv.setMultiDirSort(true);
                this._csv.setExceptionCatch(new TextDataWriter.IExceptionCatch() {
                        @Override
                        public void caught(Exception e) {
                            self.onException(new LoggerException("Android Accelerometer CSV Error!"));
                            e.printStackTrace();
                        }
                    });
            }
            this.setPackSize(4);

            ///
            ConnectSQL(context,SqlQuery._AndroidAccelerometer);
            ///
        }
        
        @Override
        public void start(final IAsyncCallback callback) {
            this.registerListener(this._csv);
            this._manager =
                (SensorManager)this._context.getSystemService(Context.SENSOR_SERVICE);
            Sensor sensor = this._manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            
            this._manager.registerListener(this.listener, sensor, this._samplerate); //SensorManager.SENSOR_DELAY_GAME);
            
            callback.done(true);

            ///
            TableName(SqlQuery._AndroidAccelerometer);
            ///
        }
        
        @Override
        public void stop(final IAsyncCallback callback) {
            this.unregisterListener(this._csv);
            if (this._manager != null) {
                this._manager.unregisterListener(this.listener);
            }
            this._manager = null;
            _timeStampOffset = Long.MAX_VALUE;
            callback.done(true);
        }
        
        @Override
        public void finish() {
            if (this._csv != null) {
                this._csv.close();
            }

            CloseSQL();
        }

        @Override
        protected void SqlCreateTable(){
            String[] columnname=new String[]{"timestamp","x","y","z"};
            String[] columntype=new String[]{SqlQuery._DATETIME,SqlQuery._FLOAT,SqlQuery._FLOAT,SqlQuery._FLOAT};
            this.sqlQuery.CreateTable(tablename, columnname, columntype);
        }
        
        final private SensorEventListener listener = new SensorEventListener() {
                @Override
                public void onAccuracyChanged(Sensor sensor, int accuracy) {
                    
                }
                @Override
                public void onSensorChanged(SensorEvent event) {
                    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {


                        //The timestamps taken from the accelerometer have better precision, so we store
                        //an offset that allows us to translate each timestamp to the system time. 
                        long adjustedTimeStamp;
                        if (_timeStampOffset == Long.MAX_VALUE)
                        {
                            adjustedTimeStamp = System.currentTimeMillis();
                            _timeStampOffset = adjustedTimeStamp - (event.timestamp / 1000000);
                        }
                        else
                        {
                            adjustedTimeStamp = _timeStampOffset + (event.timestamp / 1000000);
                        }

                        Date dt = new Date(adjustedTimeStamp);
                        float x = event.values[0];
                        float y = event.values[1];
                        float z = event.values[2];
                        
                        Intent intent = new Intent();
                        intent.putExtra("data", new float[]{x, y, z});

                        ///SQL操作
                        CreateTable();

                        String[] data=new String[4];
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
                        data[0] = sdf.format(dt);
                        data[1]=Float.toString(x);
                        data[2]=Float.toString(y);
                        data[3]=Float.toString(z);

                        AddData(data);
                        ///

                        sendData(dt, intent);
                    }
                }
            };
    }
    
    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {
        private final float MaxValue = 15.0f;
        private final int CounterMax  =  1;
        private final int GraphLength = 40;
        private final int[] COLORS = new int[] {0xFFFF0000, 0xFF00FF00, 0xFF6666FF};
        
        private int _counter = 0;
        private int _head = 0;
        private int _fill = 0;
        
        private float[][] _values = new float[GraphLength][3];
        
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
            for (int i = 1; i < GraphLength; i++) {
                
                int i1 = (i + offset + GraphLength - 1) % GraphLength;
                int i2 = (i + offset + GraphLength    ) % GraphLength;
                
                if (i2 >= this._fill) {
                    break;
                }
                float x = i * dx;

                for (int j = 0; j < 3; j++) {
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
                for (int j = 0; j < 3; j++) {
                    String s = String.format("%6.2f", last_values[j]);
                    canvas.drawText(s, 10, 5 + 20 * (j+1), paint);
                }
            }
        }
    }
}
