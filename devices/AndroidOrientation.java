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
import com.sroom.cslablogger3.ArgValue;
import com.sroom.cslablogger3.DeviceProfile;
import com.sroom.cslablogger3.IAsyncCallback;
import com.sroom.cslablogger3.ILoggingDeviceBuilder;
import com.sroom.cslablogger3.Profile;
import com.sroom.cslablogger3.SingleChoiceSelector;
import com.sroom.cslablogger3.SqlQuery;
import com.sroom.cslablogger3.TextDataWriter;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.sroom.cslablogger3.R;

public final class AndroidOrientation implements ILoggingDeviceBuilder {

	@Override
	public DeviceProfile initDeviceProfile() {
		return new DeviceProfile(
            "android orientation",
            R.drawable.ic_androidorientation,
            DeviceProfile.TYPE_BUILTIN,
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
        private TextDataWriter _csv = null;
        private SensorManager _manager = null;
        float[] _accvals = null;
        float[] _magvals = null;
        boolean _ready = false;
        
        
        @Override
        public void init(Context context) {
            super.init(context);
            final Logger self = this;
            
            Profile profile = this._profile;
            if (profile.getArgValue("save-data").equals("1")) {
                this._csv = new TextDataWriter("orientation", "time,azimuth,pitch,roll");
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
                            self.onException(new LoggerException("Android Orientation CSV Error!"));
                            e.printStackTrace();
                        }
                    });
            }            
            this.setPackSize(4);

            ///
            ConnectSQL(context,SqlQuery._AndroidOrientation);
            ///
        }
        
        @Override
        public void start(final IAsyncCallback callback) {
            this.registerListener(this._csv);
            
            this._manager =
                (SensorManager)this._context.getSystemService(Context.SENSOR_SERVICE);
            Sensor magsensor = this._manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            Sensor accsensor = this._manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            
            this._manager.registerListener(this.listener, magsensor, SensorManager.SENSOR_DELAY_GAME);
            this._manager.registerListener(this.listener, accsensor, SensorManager.SENSOR_DELAY_GAME);
            
            callback.done(true);

            ///
            TableName(SqlQuery._AndroidOrientation);
            ///
        }

        @Override
        public void stop(final IAsyncCallback callback) {
            this.unregisterListener(this._csv);
            if (this._manager != null) {
                this._manager.unregisterListener(this.listener);
            }
            this._manager = null;
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
                    
                    switch  (event.sensor.getType()) {
                    case Sensor.TYPE_ACCELEROMETER:
                        _accvals = event.values.clone();
                        break;
                    case Sensor.TYPE_MAGNETIC_FIELD:
                        _magvals = event.values.clone();
                        _ready   = true;
                        break;
                    }
                            
                    if (_accvals != null && _magvals != null && _ready) {
                        _ready = false;
                                
                        Date dt = new Date();
                                
                        float[] R = new float[16];
                        float[] I = new float[16];
                        SensorManager.getRotationMatrix(R, I, _accvals, _magvals);
                                
                        float[] items = new float[3];
                        SensorManager.getOrientation(R, items);
                                
                        Intent intent = new Intent();
                        intent.putExtra("data", items);

                        ///SQL操作
                        CreateTable();

                        String[] data=new String[4];
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
                        data[0] = sdf.format(dt);
                        data[1]=Float.toString(items[0]);
                        data[2]=Float.toString(items[1]);
                        data[3]=Float.toString(items[2]);

                        AddData(data);
                        ///
                                
                        sendData(dt, intent);
                    }
                }
            };
    };
    
    
    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {
        private final float MaxValue = 3.141592f;
        private final int CounterMax  =  1;
        private final int GraphLength = 40;
        private final int[] COLORS = new int[] {0xFFFF0000, 0xFF00FF00, 0xFF6666FF};
        
        private int _counter = 0;
        private int _head = 0;
        private int _fill = 0;
        
        private float[][] _values = new float[GraphLength][3];
        
        @Override
        public boolean setData_impl(Intent intent) {
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
                    // canvas.drawCircle(x-dx, y1, 1, paint);
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
