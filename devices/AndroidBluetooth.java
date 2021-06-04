package com.sroom.cslablogger3.devices;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Looper;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.widget.Toast;

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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import utils.StringUtils;

import com.sroom.cslablogger3.R;

public final class AndroidBluetooth implements ILoggingDeviceBuilder {

	@Override
	public DeviceProfile initDeviceProfile() {
		return new DeviceProfile(
            "android bluetooth",
            R.drawable.ic_androidbluetooth,
            DeviceProfile.TYPE_BUILTIN,
            new ArgValue[] {
                new ArgValue("scan-interval", "60",
                             new SingleChoiceSelector("0.1sec=0.1,0.25sec=0.25,0.5sec=0.5,1sec=1,2sec=2,5sec=5,10sec=10,20sec=20,30sec=30,1min=60,2min=120,5min=300")),
                new ArgValue("scan-timeout", "10",
                             new SingleChoiceSelector("0.25sec=0.25,0.5sec=0.5,1sec=1,1.5sec=1.5,2sec=2,5sec=5,10sec=10,20sec=20,30sec=30")),
                new ArgValue("save-each", "0",
                             new SingleChoiceSelector("YES=1,NO=0")),
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
        private double _scan_interval;
        private double _scan_timeout;
        private boolean _save_each=false;

        @Override
        public void init(Context context) {
            super.init(context);
            final Logger self = this;

            Profile profile = this._profile;
            if (profile.getArgValue("save-data").equals("1")) {
                this._csv = new TextDataWriter("bluetooth", "time,");
                // this._csv.setVerbose(true);
                this._csv.setIntentConverter(new TextDataWriter.IIntentConverter() {
                        @Override
                        public String convert(Intent intent) {
                            return StringUtils.join(intent.getStringArrayExtra("data"), ",");
                        }
                    });
                this._csv.setMultiDirSort(true);
                this._csv.setExceptionCatch(new TextDataWriter.IExceptionCatch() {
                        @Override
                        public void caught(Exception e) {
                            self.onException(new LoggerException("AndroidBluetooth CSV Error!"));
                            e.printStackTrace();
                        }
                    });
            }
            this._scan_interval = Double.valueOf(profile.getArgValue("scan-interval"));
            this._scan_timeout  = Double.valueOf(profile.getArgValue("scan-timeout"));
            if (profile.getArgValue("save-each").equals("1")) {
            	_save_each=true;
            }
        }

        @Override
        public void connect(final IAsyncCallback callback) {
            final Logger self = this;
            ContentResolver cr = this._context.getContentResolver();
            try {
				int bluetooth_enabled = Settings.System.getInt(cr, Settings.Secure.BLUETOOTH_ON);
                if (bluetooth_enabled == 0) {
                    this.handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(self._context,
                                               "Check your bluetooth setting",
                                               Toast.LENGTH_LONG).show();
                            }
                        });
                    Log.w("CSLabLogger", "Check your bluetooth setting");
                }
			} catch (SettingNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            callback.done(true);
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
            callback.done(true);
        }

        @Override
        public void finish() {
            if (this._csv != null) {
                this._csv.close();
            }
        }

        ////////////////////////////////////////////////////////////////////////
        final AbstractLoggerThread thread = new AbstractLoggerThread() {
                private BluetoothAdapter _adapter;
                private double _count    = 0;
                private double _countMax = 0;
                private boolean _searching;
                private boolean _canceling;
                private double _scan_interval;
                private double _scan_timeout;
                private boolean _save_each;
                private List<String> _result = new ArrayList<String>();

                @Override
                public void begin() {
                    if (Looper.myLooper() == null) Looper.prepare();
                    this._adapter = BluetoothAdapter.getDefaultAdapter();

                    Logger owner = Logger.this;
                    IntentFilter filter = new IntentFilter();
                    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
                    filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                    filter.addAction(BluetoothDevice.ACTION_FOUND);
                    owner._context.registerReceiver(this._receiver, filter);

                    this._scan_interval = owner._scan_interval;
                    this._scan_timeout  = owner._scan_timeout;
                    this._save_each=owner._save_each;

                    setInterval(100, 100);
                    this._countMax = this._scan_interval;
                    this._count = this._countMax;
                    this._searching = false;
                    _canceling=false;
                }

                @Override
                public void exec() {
                    this._count += 0.1;
                    if (! this._searching) {
                        if (this._count >= this._countMax) {
                        	//Log.d("CSLabLogger", "BluetoothScan count=" + this._count+" countMax="+this._countMax);
                        	Log.d("CSLabLogger", "BluetoothScan " + this._adapter.getScanMode()+" "+this._adapter.getState()+" "+this._adapter.isEnabled());

                        	if(!this._adapter.isEnabled()){
                        		this._adapter.enable();
                        		this._count = 0;
                        		return;
                        	}

                            this._adapter.startDiscovery();
                            this._count = 0;
                            this._countMax = this._scan_timeout;
                        }
                    } else if(!_canceling) {
                        if (this._count >= this._countMax) {
                        	Log.d("CSLabLogger", "BluetoothScan timeout"+ this._adapter.getScanMode()+" "+this._adapter.getState()+" "+this._adapter.isEnabled());
                        	if(!this._adapter.isEnabled()){
                        		this._adapter.enable();
                        		this._count = 0;
                        		this._searching=false;
                        		return;
                        	}
                        	if(this._adapter.getScanMode()==20)
                        	{
                        		this._adapter = BluetoothAdapter.getDefaultAdapter();


					    		this._count = 0;
					    		this._searching=false;
					    		return;
                        	}
                        	this._adapter.cancelDiscovery();
                            this._canceling=true;
                        	this._countMax = this._scan_interval;
                        }
                    }else if (_canceling&&_searching)
                    {
                    	if(this._count >= this._countMax*2)
                    	{
                    		Log.d("CSLabLogger", "BluetoothScan cancel timeout"+ this._adapter.getScanMode()+" "+this._adapter.getState()+" "+this._adapter.isEnabled());
                    		this._adapter = BluetoothAdapter.getDefaultAdapter();
                    		this._count = 0;
                    		this._searching=false;
                            this._canceling=false;
                    	}
                    }
                }

                @Override
                public void end() {
                    Logger owner = Logger.this;
                    owner._context.unregisterReceiver(this._receiver);

                    if (this._searching) {
                        this._adapter.cancelDiscovery();
                    }
                }

                private final BroadcastReceiver _receiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            String action = intent.getAction();
                            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                                BluetoothDevice device
                                    = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                                short rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);


                                String deviceName = device.getName();
                                if (deviceName == null) deviceName = "UNKNOWN";

                                _result.add(deviceName.replaceAll(",", "-"));
                                _result.add(device.getAddress());
                                _result.add(String.valueOf(rssi));
                                //Log.d("CSLabLogger", "BT Scan Found:"+deviceName);
                                if(_save_each)
                                {
                                	if (! _result.isEmpty()) {
                                        Date dt = new Date();
                                        Intent senditem = new Intent();
                                        senditem.putExtra("data", _result.toArray(new String[0]));
                                        _result.clear();
                                        sendData(dt, senditem);
                                    }
                                }

                            } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                                _searching = true;
                                _result.clear();
                                //Log.d("CSLabLogger", "BT Scan Started");
                            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                                _searching = false;
                                _canceling=false;
                                //Log.d("CSLabLogger", "BT Scan Finished "+_result.size());
                                if (! _result.isEmpty()) {
                                    Date dt = new Date();
                                    Intent senditem = new Intent();
                                    senditem.putExtra("data", _result.toArray(new String[0]));
                                    sendData(dt, senditem);

                                }
                            }
                        }
                    };
            };
    }

    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {

        String[] _value;

        @Override
        protected boolean setData_impl(Intent intent) {
            this._value = intent.getStringArrayExtra("data");
            return true;
        }

        @Override
        public void draw(Canvas canvas) {
            Paint paint = new Paint();
            paint.setTextSize(16);
            paint.setAntiAlias(true);
            paint.setColor(0xFFFFFFFF);

            canvas.drawRGB(0x00, 0x00, 0x33);

            int w  = canvas.getWidth();
            int dx = w / 2;
            if ((this._value.length % 3) == 0) {
                for (int i = 0; i < this._value.length; i += 3) {
                    canvas.drawText(this._value[i  ], 10     , (i/3) * 20 + 20, paint);
                    canvas.drawText(this._value[i+1], 10 + dx, (i/3) * 20 + 20, paint);
                    canvas.drawText(this._value[i+2], w - 40 , (i/3) * 20 + 20, paint);
                }
            } else {
                for (int i = 0; i < this._value.length; i += 2) {
                    canvas.drawText(this._value[i  ], 10     , (i/2) * 20 + 20, paint);
                    canvas.drawText(this._value[i+1], 10 + dx, (i/2) * 20 + 20, paint);
                }
            }
        }
    }
}
