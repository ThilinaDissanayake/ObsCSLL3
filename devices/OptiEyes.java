package com.sroom.cslablogger3.devices;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.sroom.cslablogger3.AbstractLogger;
import com.sroom.cslablogger3.AbstractLoggerPainter;
import com.sroom.cslablogger3.AbstractLoggerThread;
import com.sroom.cslablogger3.ArgValue;
import com.sroom.cslablogger3.BluetoothClient;
import com.sroom.cslablogger3.BluetoothDeviceSelector;
import com.sroom.cslablogger3.DeviceProfile;
import com.sroom.cslablogger3.IAsyncCallback;
import com.sroom.cslablogger3.ILoggingDeviceBuilder;
import com.sroom.cslablogger3.JPEGDataWriter;
import com.sroom.cslablogger3.Profile;
import com.sroom.cslablogger3.SingleChoiceSelector;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import com.sroom.cslablogger3.R;

public final class OptiEyes implements ILoggingDeviceBuilder {
    
	@Override
	public DeviceProfile initDeviceProfile() {
		return new DeviceProfile(
            "OptiEyes",
            R.drawable.ic_optieyes,
            DeviceProfile.TYPE_EXTERNAL,
            new ArgValue[] {
                new ArgValue("bluetooth", "NONE",
                             new BluetoothDeviceSelector()),
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
    interface IProcess {
        boolean apply(byte[] bytes, int size);
    }
    
    class Logger extends AbstractLogger {
        private BluetoothClient _client;
        private JPEGDataWriter _jpg = null;
        
        @Override
        public void init(Context context) {
            super.init(context);
            
            Profile profile = this._profile;
            if (profile.getArgValue("save-data").equals("1")) {
                this._jpg = new JPEGDataWriter("cam/" + profile.getTag());
            }
            this._client = new BluetoothClient(profile.getArgValue("bluetooth"));
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
            this.registerListener(this._jpg);
            this.thread.start();
            callback.done(true);
        }
        
        @Override
        public void stop(final IAsyncCallback callback) {
            this.unregisterListener(this._jpg);
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
            // this._csv.close();
        }
        
        ////////////////////////////////////////////////////////////////////////
        final AbstractLoggerThread thread = new AbstractLoggerThread() {
                private final int READ_SIZE = 2048;
                final private byte[] recvs = new byte[READ_SIZE];
                
                private BluetoothSocket _socket;
                private IProcess _process;
                
                private InputStream  _in  = null;
                private OutputStream _out = null;

                private int _trycount = 0;
                private int _trycount_max = 200;
                
                @Override
                public void begin() {
                    Logger logger = Logger.this;
                    this._socket = logger._client.getBluetoothSocket();
                    
                    try {
                        this._in  = this._socket.getInputStream();
                        this._out = this._socket.getOutputStream();
                        this._process = ackProcess;
                        
                    } catch (IOException e) {
                        Logger.this.onException(new LoggerException("OptiEyes Error!"));
                        e.printStackTrace();
                    }
                    this.setInterval(10, 10);
                }
                
                @Override
                public void exec() {
                    try {
                        if (_in.available() > 0) {
                            
                            int size = _in.read(recvs, 0, recvs.length);
                            boolean result = this._process.apply(recvs, size);
                            
                            if (result) {
                                this._trycount = 0;
                            }
                        }
                        
                        this._trycount += 1;
                        if (this._trycount >= this._trycount_max) {
                            Logger.this.onException(new LoggerException("OptiEyes: no responses!"));
                        }
                    } catch (IOException e) {
                        Logger.this.onException(new LoggerException("OptiEyes Error!"));
                        e.printStackTrace();
                    }
                }
                
                @Override
                public void end() {
                    try {
                        _out.write("$GENIESYS0001\r\n".getBytes());
                        Thread.sleep(200);
                    } catch (IOException e) {
                        Logger.this.onException(new LoggerException("OptiEyes Error!"));
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        Logger.this.onException(new LoggerException("OptiEyes Error!"));
                        e.printStackTrace();
                    }
                }
                
                final IProcess ackProcess = new IProcess() {
                        private final byte[] VGAMode = "$GENIESYS0006\r\n".getBytes();  // 160x120
                        // private final byte[] VGAMode = "$GENIESYS0005\r\n".getBytes(); // 320x240
                        private final byte[] Command = "$GENIESYS0002\r\n".getBytes();

                        private byte[] ackbytedata = new byte[0];
                        
                        @Override
                        public boolean apply(byte[] bytes, int size) {
                            if (! checkAck(bytes, size)) {
                                return false;
                            }
                            boolean result = true;
                            try {
                                _out.write("$GENIESYS0001\r\n".getBytes());
                                Thread.sleep(200);
                                _out.write(VGAMode);
                                Thread.sleep(200);
                                _out.write(Command);
                                Thread.sleep(200);
                                
                                setInterval(45, 15);
                                _trycount_max = 50;
                                _process = previewProcess;
                            } catch (IOException e) {
                                result = false;
                                e.printStackTrace();
                            } catch (InterruptedException e) {
                                result = false;
                                e.printStackTrace();
                            }
                            return result;
                        }
                        
                        boolean checkAck(byte[] bytetext, int bytesread) {
                            int size = ackbytedata.length + bytesread;
                            byte[] bytes = new byte[size];
                            
                            System.arraycopy(ackbytedata, 0, bytes, 0, ackbytedata.length);
                            System.arraycopy(bytetext, 0, bytes, ackbytedata.length, bytesread);
                            
                            ackbytedata = bytes;
                            
                            return ((new String(ackbytedata)).indexOf("ACK0000") != -1);
                        }
                    };
                
                final IProcess previewProcess = new IProcess() {
                        private final byte SOI1 = (byte)0xff;
                        private final byte SOI2 = (byte)0xd8;
                        private final byte EOI1 = (byte)0xff;
                        private final byte EOI2 = (byte)0xd9;
                        private final int OFFSET = 1500;
                        
                        private int pos1 = -1;
                        private int pos2 = -1;
                        private byte[] allbytedata = new byte[0];
                        private Date startDate;
                        
                        @Override
                        public boolean apply(byte[] bytes, int size) {
                            Date dt = new Date();
                            return saveImage(bytes, size, dt);
                        }
                        
                        boolean saveImage(byte[] bytetext, int bytesread, Date dt) {
                            boolean result = false;
                            int size = allbytedata.length + bytesread;
                            byte[] bytes = new byte[size];
                            
                            System.arraycopy(allbytedata, 0, bytes, 0, allbytedata.length);
                            System.arraycopy(bytetext, 0, bytes, allbytedata.length, bytesread);
                            
                            allbytedata = bytes;
                            
                            int i, len, j;
                            byte b1, b2;
                            
                            if (pos1 < 0) {
                                len = allbytedata.length - 1;
                                for (i = 0; i < len; i++) {
                                    b1 = allbytedata[i];
                                    b2 = allbytedata[i+1];
                                    if (b1 == SOI1 && b2 == SOI2) {
                                        pos1 = i;
                                        startDate = dt;
                                        break;
                                    }
                                }
                            } else {
                                len = allbytedata.length - 1;
                                for (i = pos1 + OFFSET; i < len; i++) {
                                    b1 = allbytedata[i];
                                    b2 = allbytedata[i+1];
                                    if (b1 == EOI1 && b2 == EOI2) {
                                        pos2 = i;
                                        break;
                                    }
                                }
                                if (pos2 > 0) {
                                    byte[] data = new byte[pos2 - pos1 + 2];
                                    for (i = pos1, j = 0; i < pos2 + 2; i++, j++) {
                                        data[j] = allbytedata[i];
                                    }

                                    Intent intent = new Intent();
                                    intent.putExtra("data", data);
                                    
                                    Logger.this.sendData(startDate, intent);
                                    
                                    //
                                    size = allbytedata.length - pos2;
                                    byte[] bytes2 = new byte[size];
                                    
                                    System.arraycopy(allbytedata, pos2,
                                                     bytes2, 0, allbytedata.length - pos2);
                                    allbytedata = bytes2;
                                    
                                    pos1 = -1;
                                    pos2 = -1;
                                    
                                    result =  true;
                                }
                            }
                            return result;
                        }
                    };
                
            };
    }
    
    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {
        private Bitmap _bitmap = null;
        
        @Override
        public boolean setData_impl(Intent intent) {
            boolean onDraw = false;
            
            byte[] data = intent.getByteArrayExtra("data");
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
