package com.sroom.cslablogger3.devices;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;
import android.widget.Toast;

import com.skyhookwireless.wps.RegistrationCallback;
import com.skyhookwireless.wps.WPS;
import com.skyhookwireless.wps.WPSAuthentication;
import com.skyhookwireless.wps.WPSContinuation;
import com.skyhookwireless.wps.WPSLocation;
import com.skyhookwireless.wps.WPSLocationCallback;
import com.skyhookwireless.wps.WPSReturnCode;
import com.skyhookwireless.wps.WPSStreetAddressLookup;
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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import com.sroom.cslablogger3.R;

public final class SkyhookLocation implements ILoggingDeviceBuilder {
	@Override
	public DeviceProfile initDeviceProfile() {
		return new DeviceProfile(
            "skyhook location",
            R.drawable.ic_skyhook,
            true,
            new ArgValue[] {
                new ArgValue("interval", "5000",
                             new SingleChoiceSelector("1sec=1000,2sec=2000,5sec=5000,10sec=10000,20sec=20000,30sec=30000,60sec=60000,120sec=120000,600sec=600000")),
	             new ArgValue("min-sample", "20000",
	                     new SingleChoiceSelector("2sec=2000,5sec=5000,10sec=10000,20sec=20000,30sec=30000,60sec=60000,120sec=120000,600sec=600000")),
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

        @Override
        public void init(Context context) {
            super.init(context);
            final Logger self = this;

            Profile profile = this._profile;
            if (profile.getArgValue("save-data").equals("1")) {
                this._csv = new TextDataWriter("skyhook", "time,latitude,longitude,accracy,altitude,time,speed,bearing");
                // this._csv.setVerbose(true);
                this._csv.setMultiDirSort(true);
                this._csv.setExceptionCatch(new TextDataWriter.IExceptionCatch() {
                        @Override
                        public void caught(Exception e) {
                            self.onException(new LoggerException("Skyhook CSV Error!"));
                            e.printStackTrace();
                        }
                    });
            }
            this._interval = Integer.valueOf(profile.getArgValue("interval"));
        }

        @Override
        public void connect(final IAsyncCallback callback) {
            final Logger self = this;
            ContentResolver cr = this._context.getContentResolver();
            try {
				int wifi_sleep_policy = Settings.System.getInt(cr, Settings.System.WIFI_SLEEP_POLICY);
                if (wifi_sleep_policy != Settings.System.WIFI_SLEEP_POLICY_NEVER) {
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
                private WPS wps;
                private WPSAuthentication auth;
                private Date _lastRecord;
                private double _minsample;

                @Override
                public void begin() {
                    Logger owner = Logger.this;

                    wps=new WPS(owner._context);
                    auth = new WPSAuthentication("takuya.maekawa", "osaka.university");

                    RegistrationCallback _regcallback=new RegistrationCallback()
                    {
                        public void done()
                        {
                        	Log.d("CSLabLogger", "skyhook registration finished");
                        	_scanning = false;
                        }

                        public void handleSuccess()
                        {
                            // send a message to display registration success
                        	Log.d("CSLabLogger", "skyhook registration success");
                        	_scanning = false;
                        }

                        public WPSContinuation handleError(final WPSReturnCode error)
                        {
                            // send a message to display the error
                        	Log.w("CSLabLogger", "skyhook registration error: "+error.toString());
                            return WPSContinuation.WPS_CONTINUE;
                        }
                    };
                    _scanning = true;
                    wps.registerUser(auth, null, _regcallback);

                    //this._manager =
                    //    (WifiManager)owner._context.getSystemService(Context.WIFI_SERVICE);
                    //IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
                    //owner._context.registerReceiver(receiver, filter);



                    this._count = this._countMax = owner._interval / 100;
                    this.setInterval(100);

                    _minsample=Double.valueOf(_profile.getArgValue("min-sample"));
                    _lastRecord=new Date(0);
                }

                @Override
                public void exec() {
                    this._count += 1;
                    if (!this._scanning && this._count >= this._countMax) {
                        //this._manager.startScan();
                    	// Callback object
                    	WPSLocationCallback callback = new WPSLocationCallback()
                    	{
                    		// What the application should do after it's done
                    		public void done()
                    		{
                    			// after done() returns, you can make more WPS calls.
                    		}

                    		// What the application should do if an error occurs
                    		public WPSContinuation handleError(WPSReturnCode error)
                    		{
                    			//handleWPSError(error); // you'll implement handleWPSError()

                    			// To retry the location call on error use WPS_CONTINUE,
                    			// otherwise return WPS_STOP
                    			Log.w("CSLabLogger", "skyhook error: "+error.toString());

                    			_scanning = false;
                    			return WPSContinuation.WPS_STOP;
                    		}

                    		// Implements the actions using the location object
                    		public void handleWPSLocation(WPSLocation location)
                    		{
                    			// you'll implement printLocation()
                    			//printLocation(location.getLatitude(), location.getLongitude());
                                StringBuffer sb = new StringBuffer();
                                sb.append(String.valueOf(location.getLatitude()));
                                sb.append(",");
                                sb.append(String.valueOf(location.getLongitude()));
                                sb.append(",");
                                sb.append(String.valueOf(0.0));
                                sb.append(",");
                                sb.append(String.valueOf(location.getAltitude()));
                                sb.append(",");
                                sb.append(String.valueOf(location.getTime()));
                                sb.append(",");
                                sb.append(String.valueOf(location.getSpeed()));
                                sb.append(",");
                                sb.append(String.valueOf(location.getBearing()));
                                sb.append(",");
                                sb.append("skyhook");


                    			Intent intent = new Intent();
                    			Date dt = new Date();
                                intent.putExtra("data", sb.toString());
                                Log.d("CSLabLogger", "skyhook data=" + sb.toString());
                                Log.d("CSLabLogger", "APs=" + location.getNAP()+" Cells="+location.getNCell());
                                sendData(dt, intent);
                                _lastRecord=dt;
                                _scanning = false;

                    		}
                    	};
                    	Date now = new Date();
                    	long l=now.getTime()-_lastRecord.getTime();
                    	if(l>=_minsample){
                            Log.d("CSLabLogger", "l=" + l +" minsample:"+_minsample);
	                    	wps.getLocation(auth, WPSStreetAddressLookup.WPS_LIMITED_STREET_ADDRESS_LOOKUP, callback);
	                        this._scanning = true;
	                        this._count = 0;
                    	}
                    }
                }

                @Override
                public void end() {
                    //Logger owner = Logger.this;
                   // owner._context.unregisterReceiver(receiver);

                }


            };
    }

    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {
        static final private int NONE       = 0;
        static final private int CALC_SIZE  = 1;
        static final private int DRAW_IMAGE = 2;

        private String [] _value = null;
        private Bitmap _bitmap = null;
        private int _mode = NONE;


        @Override
        protected boolean setData_impl(Intent intent) {
            this._value = intent.getStringExtra("data").split(",");
            this._mode = CALC_SIZE;
            return true;
        }

        @Override
        public void draw(Canvas canvas) {
            int width  = canvas.getWidth();
            int height = canvas.getHeight();
            Rect dst = new Rect(0, 0, width, height);

            if (this._mode == CALC_SIZE) {
                this.loadMapImage(dst);
            } else if (this._mode == DRAW_IMAGE) {
                this._mode = NONE;
            }

            if (this._bitmap != null) {
                Rect src = new Rect(0, 0, this._bitmap.getWidth(), this._bitmap.getHeight());
                Paint paint = new Paint();
                canvas.drawBitmap(this._bitmap, src, dst, paint);

                paint.setColor(0x66000000);
                paint.setStyle(Paint.Style.FILL);
                canvas.drawRect(new Rect(width-200, height-105, width, height), paint);

                paint.setTextSize(20);
                paint.setAntiAlias(true);
                paint.setColor(0xFFFFFFFF);
                paint.setStyle(Paint.Style.STROKE);

                //Location l = this._value;
                int x = width - 190;

                String strTime = _value[4];
                canvas.drawText("latitude: "+_value[0],
                                x, height-85, paint);
                canvas.drawText("longitude: "+_value[1],
                                x, height-65, paint);
                canvas.drawText("accuracy: "+_value[3]+" not provided",
                                x, height-45, paint);
                canvas.drawText("speed: "+_value[2],
                                x, height-25, paint);
                canvas.drawText(String.format("time: %s", strTime),
                                x, height-5, paint);
            }
        }

        private void loadMapImage(Rect dst) {
            final Painter self = this;

            String pos = _value[0] + "," + _value[1];
            String color = _value[7].equals(LocationManager.NETWORK_PROVIDER) ? "blue" : "red";
            String apikey = "0_yRnj66B9Ud2xW909AHWjHJdE4fR21oe0slVMA";

            StringBuffer sb = new StringBuffer();
            sb.append("http://maps.google.co.jp/staticmap?");
            sb.append(String.format("center=%s", pos));
            sb.append(String.format("&markers=%s,%s", pos, color));
            sb.append(String.format("&zoom=%d", 15));
            sb.append(String.format("&size=%dx%d", dst.width(), dst.height()));
            sb.append(String.format("&key=%s", apikey));

            final String mapurl = sb.toString();

            new Thread() {
                @Override
                public void run() {
                    DefaultHttpClient dhc = new DefaultHttpClient();

					try {
						HttpResponse response = dhc.execute(new HttpGet(mapurl));
                        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
                            HttpEntity entity = response.getEntity();
                            InputStream in = entity.getContent();

                            self._bitmap = BitmapFactory.decodeStream(in);

                            self._mode = DRAW_IMAGE;
                            self.callDraw();
                        }
					} catch (ClientProtocolException e) {
						e.printStackTrace();
                    } catch (IllegalStateException e) {
                        e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
                }
            }.start();
        }
    }
}

