package com.sroom.cslablogger3.devices;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
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

import com.sroom.cslablogger3.R;

public final class AndroidWifi implements ILoggingDeviceBuilder {

	@Override
	public DeviceProfile initDeviceProfile() {
		return new DeviceProfile(
            "android wifi",
            R.drawable.ic_androidwifi,
            true,
            new ArgValue[] {
                new ArgValue("interval", "100",
                             new SingleChoiceSelector("0.1sec=100, 1sec=1000,2sec=2000,5sec=5000,10sec=10000,20sec=20000,30sec=30000,60sec=60000,90sec=90000,120sec=120000")),
               new ArgValue("timeout", "30000",
            		   		new SingleChoiceSelector("2sec=2000,5sec=5000,10sec=10000,20sec=20000,30sec=30000,60sec=60000,90sec=90000,120sec=120000")),
                new ArgValue("sleep", "0",
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
        private int _interval;
        private TextDataWriter _csv = null;
        public int sleep=0;
        private int timeout;

        @Override
        public void init(Context context) {
            super.init(context);
            final Logger self = this;

            Profile profile = this._profile;
            if (profile.getArgValue("save-data").equals("1")) {
                this._csv = new TextDataWriter("wifi", "time,");
                // this._csv.setVerbose(true);
                this._csv.setMultiDirSort(true);
                this._csv.setExceptionCatch(new TextDataWriter.IExceptionCatch() {
                        @Override
                        public void caught(Exception e) {
                            self.onException(new LoggerException("Android Wifi CSV Error!"));
                            e.printStackTrace();
                        }
                    });
            }
            this._interval = Integer.valueOf(profile.getArgValue("interval"));
            sleep=Integer.valueOf(profile.getArgValue("sleep"));
            timeout=Integer.valueOf(profile.getArgValue("timeout"));
        }

        @Override
        public void connect(final IAsyncCallback callback) {
            final Logger self = this;
            ContentResolver cr = this._context.getContentResolver();
            try {
				int wifi_sleep_policy = Settings.System.getInt(cr, Settings.Global.WIFI_SLEEP_POLICY);//Settings.System.getInt(cr, Settings.System.WIFI_SLEEP_POLICY); //Thilina
                if (wifi_sleep_policy != Settings.Global.WIFI_SLEEP_POLICY_NEVER) { //Settings.System.WIFI_SLEEP_POLICY_NEVER
                    this.handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(self._context,
                                               "check your WiFi sleep policy",
                                               Toast.LENGTH_LONG).show();
                            }
                        });
                    Log.w("CSLabLogger", "check your WiFi sleep policy");
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
        public void finish() {
            if (this._csv != null) {
                this._csv.close();
            }
        }

        ////////////////////////////////////////////////////////////////////////

        final AbstractLoggerThread thread = new AbstractLoggerThread() {
                private WifiManager _manager = null;
                private int _count    = 0;
                private int _countMax = 0;
                private boolean _scanning = false;
                private int sleep=0;
                private int _countTimeoutMax=0;
                //private PowerManager.WakeLock _wlock=null;

                @Override
                public void begin() {
                    Logger owner = Logger.this;
                    sleep=owner.sleep;
                    this._manager =
                        (WifiManager)owner._context.getSystemService(Context.WIFI_SERVICE);
                    if(sleep==1){
	                    if(!this._manager.isWifiEnabled())
	                    {
	                    	this._manager.setWifiEnabled((true));
	                    }
                    }

                    //PowerManager powerman=(PowerManager)owner._context.getSystemService(Context.POWER_SERVICE);
                    //_wlock = powerman.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WiFicslablogger3");
                    //_wlock.acquire();

                    IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                    owner._context.registerReceiver(receiver, filter);

                    this._count = this._countMax = owner._interval / 100;
                    this._countTimeoutMax=owner.timeout/100;
                    //this.setInterval(100); //Thilina
                }

                @Override
                public void exec() {
                    this._count += 1;

                    /*
                    if( this._count%100==0){
                    	//Log.d("CSLabLogger", "WifiScan increment scan="+this._scanning+" count=" + this._count+" countMax="+this._countMax);

                        if (_wlock.isHeld()) {
                        	Log.d("CSLabLogger", "PowerManger WakeLockHeld");
                        }
                        else
                        {
                        	Log.d("CSLabLogger", "PowerManger WakeLock Not Held!!!");
                        }
                    }
                    */

                    if (!this._scanning && this._count >= this._countMax) {
                    	//Log.d("CSLabLogger", "WifiScan count=" + this._count+" countMax="+this._countMax);

                        if(sleep==1){
    	                    if(!this._manager.isWifiEnabled())
    	                    {
    	                    	this._manager.setWifiEnabled((true));
    	                    }
                        }

                        this._manager.startScan();
                        this._scanning = true;
                        this._count = 0;

                    }
                    if(this._scanning && this._count >= this._countTimeoutMax) {
                    	//Log.d("CSLabLogger", "WifiScan timeout count=" + this._count+" countTimeoutMax="+this._countTimeoutMax);
                        if(sleep==1){
    	                    if(_manager.isWifiEnabled())
    	                    {
    	                    	_manager.setWifiEnabled((false));
    	                    }
                        }
                        _scanning = false;
                    }


                }

                @Override
                public void end() {
                    Logger owner = Logger.this;
                    if(sleep==1){
	                    if(!this._manager.isWifiEnabled())
	                    {
	                    	this._manager.setWifiEnabled((true));
	                    }
                    }
                    try{
                    owner._context.unregisterReceiver(receiver);
                    /*
                    if(_wlock!=null){
                    	//_wlock.release();
                    }
                    */
                    }catch(Exception e)
                    {}
                }

                final private BroadcastReceiver receiver = new BroadcastReceiver() {
                        final String FORMAT = "SSID: %s, BSSID: %s, capabilities: %s, level: %d, frequency: %d";

                        @Override
                        public void onReceive(Context context, Intent notUsed) { //replaced '_' with 'notUsed'
                            List<ScanResult> results = _manager.getScanResults();
                            if (!results.isEmpty()) {
                                Date dt = new Date();
                                StringBuffer buffer = new StringBuffer();
                                for (ScanResult result : results) {
                                    String line = String.format(FORMAT,
                                                                result.SSID.replaceAll(",", "-"),
                                                                result.BSSID,
                                                                result.capabilities,
                                                                result.level,  result.frequency);
                                    buffer.append("\t");
                                    buffer.append(line);
                                }
                                Intent intent = new Intent();
                                intent.putExtra("data", buffer.toString());
                                //Log.d("CSLabLogger", "data=" + buffer.toString());
                                sendData(dt, intent);
                            }

                            if(sleep==1){
        	                    if(_manager.isWifiEnabled())
        	                    {
        	                    	_manager.setWifiEnabled((false));
        	                    }
                            }

                            _scanning = false;


                        }
                    };
            };
    }

    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {

        private String _value = "";

        @Override
        public boolean setData_impl(Intent intent) {
            this._value = intent.getStringExtra("data");
            return true;
        }

        @Override
        public void draw(Canvas canvas) {
            Paint paint = new Paint();
            paint.setTextSize(16);
            paint.setAntiAlias(true);
            paint.setColor(0xFFFFFFFF);

            canvas.drawRGB(0x00, 0x00, 0x33);

            String value = this._value;
            value = value.replaceAll("\t", "\n");
            value = value.replaceAll(", ", "\n");

            List<String> list = new ArrayList<String>();
            for (String s : value.split("\n")) {
                if (s.startsWith("SSID")) {
                    list.add(s);
                } else if (s.startsWith("level")) {
                    list.add(s);
                }
            }

            int dx = canvas.getWidth() / 2;
            for (int i = 0; i < list.size(); i += 2) {
                canvas.drawText(list.get(i  ), 10     , (i/2) * 20 + 20, paint);
                canvas.drawText(list.get(i+1), 10 + dx, (i/2) * 20 + 20, paint);
            }


        }
    }
}
