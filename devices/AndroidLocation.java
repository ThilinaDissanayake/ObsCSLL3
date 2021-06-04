package com.sroom.cslablogger3.devices;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.sroom.cslablogger3.AbstractLogger;
import com.sroom.cslablogger3.AbstractLoggerPainter;
import com.sroom.cslablogger3.AbstractLoggerThread;
import com.sroom.cslablogger3.ArgValue;
import com.sroom.cslablogger3.DeviceProfile;
import com.sroom.cslablogger3.IAsyncCallback;
import com.sroom.cslablogger3.ILoggingDeviceBuilder;
import com.sroom.cslablogger3.Profile;
import com.sroom.cslablogger3.SingleChoiceSelector;
import com.sroom.cslablogger3.SqlQuery;
import com.sroom.cslablogger3.TextDataWriter;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import utils.BetterLocationManager;

import com.sroom.cslablogger3.R;

public final class AndroidLocation implements ILoggingDeviceBuilder {

	@Override
	public DeviceProfile initDeviceProfile() {
		return new DeviceProfile(
            "android location",
            R.drawable.ic_androidlocation,
            DeviceProfile.TYPE_BUILTIN,
            new ArgValue[] {
                new ArgValue("using", "3",
                             new SingleChoiceSelector("GPS+NETWORK=3,GPS=1,NETWORK=2")),
                new ArgValue("interval", "10000",
                             new SingleChoiceSelector("10sec=10000,20sec=20000,30sec=30000,60sec=60000,90sec=90000")),
                // new ArgValue("interval-fast", "10000",
                //              new SingleChoiceSelector("10sec=10000,20sec=20000,30sec=30000,60sec=60000,90sec=90000")),
                // new ArgValue("interval-slow", "90000",
                //              new SingleChoiceSelector("10sec=10000,20sec=20000,30sec=30000,60sec=60000,90sec=90000")),
                new ArgValue("timeout", "10000",
                             new SingleChoiceSelector("10sec=10000,20sec=20000,30sec=30000,60sec=60000,90sec=90000")),

                new ArgValue("min-time", "30000",
                             new SingleChoiceSelector("OFF=0,1sec=1000,10sec=10000,30sec=30000,60sec=60000,90sec=90000")),
                new ArgValue("min-distance", "25",
                             new SingleChoiceSelector("OFF=0,1meter=1,5meters=5,10meters=10,25meters=25,50meters=50,100meters=100,200meters=200,250meters=250")),
                new ArgValue("timeout-in-motion-mode", "120000",
                             new SingleChoiceSelector("1min=60000,2min=120000,3min=300000,5min=300000,10min=600000")),
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
        private int _using;
        private int _interval_fast;
        private int _interval_slow;
        private int _timeout;
        private int _timeout_in_motion_mode;
        private long _min_time;
        private float _min_distance;

        private Handler _handler;
        private TextDataWriter _csv = null;
        private LocationThread thread = new LocationThread();

        @Override
        public void init(Context context) {
            super.init(context);
            final Logger self = this;

            Profile profile = this._profile;
            if (profile.getArgValue("save-data").equals("1")) {
                this._csv = new TextDataWriter("location", "time,latitude,longitude,accracy,altitude,time,speed,bearing");
                // this._csv.setVerbose(true);
                this._csv.setIntentConverter(new TextDataWriter.IIntentConverter() {
                        @Override
                        public String convert(Intent intent) {
                            Location location = intent.getParcelableExtra("data");

                            StringBuffer sb = new StringBuffer();
                            sb.append(String.valueOf(location.getLatitude()));
                            sb.append(",");
                            sb.append(String.valueOf(location.getLongitude()));
                            sb.append(",");
                            sb.append(String.valueOf(location.getAccuracy()));
                            sb.append(",");
                            sb.append(String.valueOf(location.getAltitude()));
                            sb.append(",");
                            sb.append(String.valueOf(location.getTime()));
                            sb.append(",");
                            sb.append(String.valueOf(location.getSpeed()));
                            sb.append(",");
                            sb.append(String.valueOf(location.getBearing()));
                            sb.append(",");
                            sb.append(location.getProvider());

                            float response_time = intent.getFloatExtra("response time", -1.0f);
                            if (response_time != -1) {
                                sb.append(",");
                                sb.append(String.valueOf(response_time));
                            }

                            return sb.toString();
                        }
                    });
                this._csv.setMultiDirSort(true);
                this._csv.setExceptionCatch(new TextDataWriter.IExceptionCatch() {
                        @Override
                        public void caught(Exception e) {
                            self.onException(new LoggerException("Android Location CSV Error!"));
                            e.printStackTrace();
                        }
                    });
            }
            this._using = Integer.valueOf(profile.getArgValue("using"));
            // this._interval_fast = Integer.valueOf(profile.getArgValue("interval-fast"));
            // this._interval_slow = Integer.valueOf(profile.getArgValue("interval-slow"));
            this._interval_fast = Integer.valueOf(profile.getArgValue("interval"));
            this._interval_slow = Integer.valueOf(profile.getArgValue("interval"));
            this._timeout       = Integer.valueOf(profile.getArgValue("timeout"));
            this._timeout_in_motion_mode = Integer.valueOf(profile.getArgValue("timeout-in-motion-mode"));
            this._min_time     = Integer.valueOf(profile.getArgValue("min-time"));
            this._min_distance = Float.valueOf(profile.getArgValue("min-distance"));

            ///
            ConnectSQL(context,SqlQuery._AndroidLocation);
            ///
        }

        @Override
        public void start(final IAsyncCallback callback) {
            this._handler = new Handler(Looper.getMainLooper());
            this.registerListener(this._csv);
            this.thread.start();
            callback.done(true);

            ///
            TableName(SqlQuery._AndroidLocation);
            ///
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
            CloseSQL();
        }

        @Override
        protected void SqlCreateTable(){
            String[] columnname=new String[]{"timestamp","latitude","longitude"};
            String[] columntype=new String[]{SqlQuery._DATETIME,SqlQuery._FLOAT,SqlQuery._FLOAT};
            this.sqlQuery.CreateTable(tablename, columnname, columntype);
        }

        @Override
        public void receiveMessage(Intent intent) {
            boolean logging = intent.getBooleanExtra("logging", true);
            this.thread.setLogging(logging);
            this.setProfileStatus(logging ? "running.." : "pause");
        }

        ////////////////////////////////////////////////////////////////////////
        class LocationThread extends AbstractLoggerThread {
                private BetterLocationManager _manager = null;

                private Location _location;
                private Date _location_date;
                private boolean _location_scan;
                private long _trigger_time;
                private boolean _in_motion_mode;
                private long _last_scan_time;
                private long _scan_interval;
                private boolean _logging = true;

                public void setLogging(boolean logging) {
                    this._logging = logging;
                }

                @Override
                public void begin() {
                    final Logger owner = Logger.this;

                    LocationManager manager =
                        (LocationManager)owner._context.getSystemService(Context.LOCATION_SERVICE);

                    this._manager = new BetterLocationManager(manager) {
                            @Override protected void onLocationProviderNotAvailable() {}
                            @Override protected void onLocationProgress(long time) {}
                            @Override protected void onLocationTimeout() {}
                            @Override
                            protected void onUpdateLocation(Location location, int updateCount) {
                                long now = System.currentTimeMillis();

                                _location = location;
                                _location_date = new Date(now);

                                Log.d("CSLabLogger", "location update=" +
                                      location.getProvider() + "; " + location.getAccuracy());

                                if (location != null) {
                                    String GPS = LocationManager.GPS_PROVIDER;
                                    if (GPS.equals(location.getProvider())||!((owner._using & 0x01) == 0x01)) {//source is GPS or GPS is disabled
                                        saveLocation(_location);
                                        if (_in_motion_mode) {
                                            _trigger_time = new Date().getTime();
                                        }
                                    }
                                }
                            }
                        };

                    this._manager.setUserLastKnownLocation(false);
                    this._manager.setTimeout(0);
                    this._manager.setMinTime(owner._min_time);
                    this._manager.setMinDistance(owner._min_distance);

                    // 1=GPS, 2=NETWORK, 3=GPS+NETWORK
                    this._manager.setUsingGPS((owner._using & 0x01) == 0x01);
                    this._manager.setUsingNetwork((owner._using & 0x02) == 0x02);

                    this.setInterval(1000, 500);
                    this._location_scan  = false;
                    this._in_motion_mode = false;
                    this._last_scan_time = 0;
                    this._scan_interval  = owner._interval_fast;
                }

                @Override
                public void exec() {
                    final Logger owner = Logger.this;

                    if (! this._location_scan) {
                        long elapsed = System.currentTimeMillis() - this._last_scan_time;
                        if (elapsed >= this._scan_interval) {
                            if (this._logging) {
                                owner._handler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            _location = null;
                                            _location_date = null;
                                            _location_scan = true;
                                            _trigger_time = new Date().getTime();
                                            _manager.start();
                                        }
                                    });
                            } else {
                                Log.d("CSLabLogger", "location logging skip");
                            }
                        }
                    } else if (this._location_date != null) {
                        int elapsed = (int)((new Date().getTime()) - this._location_date.getTime());
                        if (_in_motion_mode) {
                            if (elapsed >= _timeout_in_motion_mode) {
                                this._in_motion_mode = false;
                                this._location_scan  = false;
                                this._manager.stop();
                                this._last_scan_time = System.currentTimeMillis();
                            }
                        } else {
                            if (elapsed >= _timeout) {
                            	if(((owner._using & 0x01) == 0x01)){//GPS is enabled
                            		if(this._location.getProvider().equals(LocationManager.NETWORK_PROVIDER))
                            		{
                            			saveLocation(this._location);
                            		}
                            	}
                                this._location_scan  = false;
                                this._manager.stop();
                                this._last_scan_time = System.currentTimeMillis();
                            }
                        }
                    } else {
                        int elapsed = (int)((new Date().getTime()) - this._trigger_time);
                        if (_in_motion_mode) {
                            if (elapsed >= _timeout_in_motion_mode) {
                                this._in_motion_mode = false;
                                this._location_scan  = false;
                                this._manager.stop();
                                this._last_scan_time = System.currentTimeMillis();
                            }
                        } else {
                            if (elapsed >= _timeout) {
                            	if(((owner._using & 0x01) == 0x01)){//GPS is enabled
                            		this._in_motion_mode = true;
                            	}
                            	this._location_scan  = false;
                                this._manager.stop();
                                this._last_scan_time = System.currentTimeMillis();
                            }
                        }
                    }
                }


                @Override
                public void end() {
                    this._manager.stop();
                }

                private void saveLocation(Location location) {
                    final Logger owner = Logger.this;

                    Date dt = this._location_date;
                    float response_time = (dt.getTime() - this._trigger_time) / 1000.0f;

                    float speed = location.getSpeed();
                    if (speed > 0.0f) {
                        this._in_motion_mode = true;
                        this._scan_interval = owner._interval_fast;
                    } else {
                        this._in_motion_mode = false;
                        this._scan_interval = owner._interval_slow;
                    }
                    Intent intent = new Intent();
                    intent.putExtra("data", location);
                    intent.putExtra("response time", response_time);

                    ///SQL操作
                    CreateTable();

                    String[] data=new String[3];
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
                    data[0] = sdf.format(dt);
                    data[1]=Float.toString((float)location.getLatitude());
                    data[2]=Float.toString((float)location.getLongitude());

                    AddData(data);
                    ///

                    sendData(dt, intent);
                }
            };
    }

    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {
        static final private int NONE       = 0;
        static final private int CALC_SIZE  = 1;
        static final private int DRAW_IMAGE = 2;

        private Location _value = null;
        private Bitmap _bitmap = null;
        private int _mode = NONE;


        @Override
        protected boolean setData_impl(Intent intent) {
            this._value = intent.getParcelableExtra("data");
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

                Location l = this._value;
                int x = width - 190;

                String strTime = new SimpleDateFormat("HH:mm:ss").format(new Date(l.getTime()));
                canvas.drawText(String.format("latitude: %.3f", l.getLatitude()),
                                x, height-85, paint);
                canvas.drawText(String.format("longitude: %.3f", l.getLongitude()),
                                x, height-65, paint);
                canvas.drawText(String.format("accuracy: %.3f", l.getAccuracy()),
                                x, height-45, paint);
                canvas.drawText(String.format("speed: %.3f", l.getSpeed()),
                                x, height-25, paint);
                canvas.drawText(String.format("time: %s", strTime),
                                x, height-5, paint);
            }
        }

        private void loadMapImage(Rect dst) {
            final Painter self = this;

            String pos = this._value.getLatitude() + "," + this._value.getLongitude();
            String color = this._value.getProvider().equals(LocationManager.NETWORK_PROVIDER) ? "blue" : "red";
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
