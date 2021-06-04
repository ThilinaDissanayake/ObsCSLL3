package com.sroom.cslablogger3.devices;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.sroom.cslablogger3.AbstractLogger;
import com.sroom.cslablogger3.AbstractLoggerPainter;
import com.sroom.cslablogger3.AbstractLoggerThread;
import com.sroom.cslablogger3.ArgValue;
import com.sroom.cslablogger3.BluetoothClient;
import com.sroom.cslablogger3.BluetoothDeviceSelector;
import com.sroom.cslablogger3.DeviceProfile;
import com.sroom.cslablogger3.IAsyncCallback;
import com.sroom.cslablogger3.ILoggingDeviceBuilder;
import com.sroom.cslablogger3.Profile;
import com.sroom.cslablogger3.SingleChoiceSelector;
import com.sroom.cslablogger3.TextDataWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.sroom.cslablogger3.R;

public final class WirelessT implements ILoggingDeviceBuilder {

	@Override
	public DeviceProfile initDeviceProfile() {
		return new DeviceProfile(
            "WirelessT",
            R.drawable.ic_wirelesst,
            DeviceProfile.TYPE_EXTERNAL,
            new ArgValue[] {
                new ArgValue("bluetooth", "NONE",
                                 new BluetoothDeviceSelector()),
                new ArgValue("samplerate", "4-5",
                                 new SingleChoiceSelector("200Hz(1msec x5)=1-5,100Hz(2msec x5)=2-5,50Hz(4msec x5)=4-5,40Hz(5msec x5)=5-5")),
                new ArgValue("model", "WAA001",
                             new SingleChoiceSelector("WAA001,WAA004")),
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
    interface IDataConverter {
        void convert(float[] data);
    };
    
    class Logger extends AbstractLogger {
        
        private TextDataWriter _csv = null;
        private BluetoothClient _client;
        private int _interval;
        private int _count;
        private IDataConverter _converter;
        
        @Override
        public void init(Context context) {
            super.init(context);
            final Logger self = this;
            
            Profile profile = this._profile;
            if (profile.getArgValue("save-data").equals("1")) {
                this._csv = new TextDataWriter("acc/" + profile.getTag(), "time,x,y,z");
                // this._csv.setVerbose(true);
                this._csv.setSplitSeconds(30);
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
                            self.onException(new LoggerException("WirelessT CSV Error!"));
                            e.printStackTrace();
                        }
                        
                    });
            }
            this._client = new BluetoothClient(profile.getArgValue("bluetooth"), true);
            
            String samplerate = profile.getArgValue("samplerate");
            if (samplerate == null) samplerate = "4-5";
            String[] items = samplerate.split("-");
            
            this._interval = Integer.valueOf(items[0]);
            this._count    = Integer.valueOf(items[1]);

            if (profile.getArgValue("model").equalsIgnoreCase("waa004")) {
                this._converter = null;
            } else {
                this._converter = new IDataConverter() {
                        @Override
                        public void convert(float[] data)  {
                            float tmp = data[0];
                            data[0] = data[1] * -1; // x = -y
                            data[1] = tmp;          // y =  x
                            data[2] *= -1.0;        // z = -z
                        }
                    };
            }

            this.setPackSize(8);
        }
        
        @Override
        public void connect(final IAsyncCallback callback) {
            this._client.connect(new BluetoothClient.IAsyncCallback() {
                    @Override
                    public void done(boolean result) {
                        callback.done(result);
                    }
                });
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
        public void disconnect(final IAsyncCallback callback) {
            this._client.disconnect(new BluetoothClient.IAsyncCallback() {
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
        
        ////////////////////////////////////////////////////////////////////////
        final AbstractLoggerThread thread = new AbstractLoggerThread() {
                private final int BUF_SIZE = 1024;
                private BluetoothSocket _socket;
                private InputStream  _in;
                private OutputStream _out;

                private int _index = 0;
                private int _prev_index = -1;
                private final int _buffer[] = new int[15];
                
                private int _trycount = 0;
                private int _trycount_max = 1000;
                
                @Override
                public void begin() {
                    Logger logger = Logger.this;
                    this._socket = logger._client.getBluetoothSocket();
                    try {
                        this._in  = this._socket.getInputStream();
                        this._out = this._socket.getOutputStream();
                        
                        sett();

                        String cmd = String.format("senb +000000000 %d %d 0",
                                                   logger._interval, logger._count);
                        send(cmd);
                    } catch (IOException e) {
                        Logger.this.onException(new LoggerException("WirelessT Error!"));
                        e.printStackTrace();
                    }
                    this.setInterval(logger._interval, logger._interval);
                }
                
                @Override
                public void exec() {
                    byte[] bytes = new byte[BUF_SIZE];
                    int len;
                    
                    try {
                        if (this._in.available() > 0) {
                            len = this._in.read(bytes);
                            for (int i = 0; i < len; i++) {
                                if (readbyte(bytes[i])) this._trycount = 0;
                            }
                        }
                        
                        this._trycount += 1;
                        if (this._trycount >= this._trycount_max) {
                            Logger.this.onException(new LoggerException("WirelessT: no responses!"));
                        }
                        
                    } catch (IOException e) {
                        Logger.this.onException(new LoggerException("WirelessT Error!"));
                        e.printStackTrace();
                    }
                }
                
                @Override
                public void end() {
                    try {
                        this._out.close();
                    } catch (IOException e) {
                        Logger.this.onException(new LoggerException("WirelessT Error!"));
                        e.printStackTrace();
                    }
                }

                private void write(byte[] bytes) {
                    try {
                        this._out.write(bytes);
                    } catch (IOException e) {
                        Logger.this.onException(new LoggerException("WirelessT Error!"));
                        e.printStackTrace();
                    }
                }
                
                private void sett() {
                    send("sett " + (new SimpleDateFormat("HHmmssSSS")).format(new Date()));
                }

                private void send(String cmd) {
                    int len = cmd.length();
                    byte[] bytes = new byte[len + 2];
                    System.arraycopy(cmd.getBytes(), 0, bytes, 0, len);
                    bytes[len  ] = 0x0D;
                    bytes[len+1] = 0x0A;
                    write(bytes);
                }

                private boolean readbyte(byte b) {
                    boolean result = false;
                    this._buffer[this._index] = b & 0x00ff;
                    this._prev_index = this._index;
                    
                    if (this._index == 0) {
                        if (b == (byte)0x73) this._index += 1; // 's'
                    } else if (this._index == 1) {
                        if (b == (byte)0x65) this._index += 1; // 'e'
                    } else if (this._index == 2) {
                        if (b == (byte)0x6E) this._index += 1; // 'n'
                    } else if (this._index == 3) {
                        if (b == (byte)0x62) this._index += 1; // 'b'
                    } else if (this._index == 14) {
                        if (b == (byte)0xC1) {
                            makeAccData(this._buffer);
                            result = true;
                        }
                    } else {
                        this._index += 1;
                    }

                    if (this._index == this._prev_index) {
                        this._index = 0;
                    }
                    return result;
                }
                
                private void makeAccData(int[] bytes) {
                    Date dt = new Date();
                    
                    int dint = (bytes[4]<<24) + (bytes[5]<<16) + (bytes[6]<<8) + (bytes[7]);

                    int millisecond = (int)(dint % 1000);
                    dint /= 1000;
                    int second  = (int)(dint % 60);
                    dint /= 60;
                    int minute  = (int)(dint % 60);
                    dint /= 60;
                    int hour = (int)(dint % 24);
                    
                    dt.setHours(hour);
                    dt.setMinutes(minute);
                    dt.setSeconds(second);
                    
                    long l = dt.getTime();
                    l /= 1000;
                    l *= 1000;
                    l += millisecond;
                    dt = new Date(l);
                    
                    float x = ((short)((bytes[ 8]<<8) + bytes[ 9])) / 1000.0f;
                    float y = ((short)((bytes[10]<<8) + bytes[11])) / 1000.0f;
                    float z = ((short)((bytes[12]<<8) + bytes[13])) / 1000.0f;

                    if (_converter != null) {
                        float[] data = {x, y, z};
                        _converter.convert(data);
                        x = data[0];
                        y = data[1];
                        z = data[2];
                    }
                    
                    Intent intent = new Intent();
                    intent.putExtra("data", new float[] {x, y, z});
                    
                    Logger.this.sendData(dt, intent);
                }
            };
    }
    
    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {
        private final float MaxValue = 3.0f;
        private final int CounterMax  =  2;
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
                }
                
                last_values = this._values[i2];
            }

            if (last_values != null) {
                paint.setTextSize(12);
                paint.setAntiAlias(true);
                paint.setColor(0xFFFFFFFF);
                paint.setStyle(Paint.Style.STROKE);
                for (int j = 0; j < 3; j++) {
                    String s = String.format("%6.2f", last_values[j]);
                    canvas.drawText(s, 5, 5 + 12 * (j+1), paint);
                }
            }
        }
    }
}
