package com.sroom.cslablogger3.devices;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.BatteryManager;
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
import com.sroom.cslablogger3.TextDataWriter;

import java.util.Date;

import com.sroom.cslablogger3.R;

public final class AndroidBattery implements ILoggingDeviceBuilder {

	@Override
	public DeviceProfile initDeviceProfile() {
		return new DeviceProfile(
            "android battery",
            R.drawable.ic_androidbattery,
            DeviceProfile.TYPE_BUILTIN,
            new ArgValue[] {
                new ArgValue("interval", "60",
                             new SingleChoiceSelector("30sec=30,1min=60,2min=120,5min=300")),
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
        private int _interval = 0;

        @Override
        public void init(Context context) {
            super.init(context);
            final Logger self = this;

            Profile profile = this._profile;
            if (profile.getArgValue("save-data").equals("1")) {
                this._csv = new TextDataWriter("battery", "time,health,plugged,status,present,scale,level,voltage,temperature");
                // this._csv.setVerbose(true);
                this._csv.setIntentConverter(new TextDataWriter.IIntentConverter() {
                        @Override
                        public String convert(Intent intent) {
                            intent = (Intent)intent.getParcelableExtra("data");
                            StringBuffer buffer = new StringBuffer();
                            buffer.append(intent.getStringExtra("health"));
                            buffer.append(",");
                            buffer.append(intent.getStringExtra("plugged"));
                            buffer.append(",");
                            buffer.append(intent.getStringExtra("status"));
                            buffer.append(",");
                            buffer.append(String.valueOf(intent.getBooleanExtra("present", false)));
                            buffer.append(",");
                            buffer.append(String.valueOf(intent.getIntExtra("scale", 0)));
                            buffer.append(",");
                            buffer.append(String.valueOf(intent.getIntExtra("level", 0)));
                            buffer.append(",");
                            buffer.append(String.valueOf(intent.getIntExtra("voltage", 0)));
                            buffer.append(",");
                            buffer.append(String.valueOf(intent.getIntExtra("temperature", 0)));
                            return buffer.toString();
                        }
                    });
                this._csv.setMultiDirSort(true);
                this._csv.setExceptionCatch(new TextDataWriter.IExceptionCatch() {
                        @Override
                        public void caught(Exception e) {
                            self.onException(new LoggerException("Template CSV Error!"));
                            e.printStackTrace();
                        }
                    });
            }
            this._interval = Integer.valueOf(profile.getArgValue("interval"));
        }

        @Override
        public void connect(final IAsyncCallback callback) {
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

                private int _interval = 0;
                private long _saved_time = 0;
                private Intent _saved_value = null;

                @Override
                public void begin() {
                    Logger owner = Logger.this;
                    IntentFilter filter = new IntentFilter();
                    filter.addAction(Intent.ACTION_BATTERY_CHANGED);
                    owner._context.registerReceiver(this._receiver, filter);

                    this._interval = owner._interval;
                    this.setInterval(1000, 1000);
                }

                @Override
                public void exec() {
                    if (this._saved_value != null) {
                        long now = System.currentTimeMillis();
                        if ((now - this._saved_time) > this._interval * 1000) {
                            Date dt = new Date();
                            sendData(dt, this._saved_value);
                            this._saved_time = now;
                            Log.d("CSLabLogger", "save Battery");
                        }
                    }
                }

                @Override
                public void end() {
                    Logger owner = Logger.this;
                    owner._context.unregisterReceiver(this._receiver);
                }


                final private BroadcastReceiver _receiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            if (! intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) return;

                            String health;
                            switch (intent.getIntExtra("health", 0)) {
                            case BatteryManager.BATTERY_HEALTH_DEAD:
                                health = "DEAD";
                                break;
                            case BatteryManager.BATTERY_HEALTH_GOOD:
                                health = "GOOD";
                                break;
                            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                                health = "OVERHEAT";
                                break;
                            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                                health = "VOLTAGE";
                                break;
                            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                                health = "UNSPECIFIED_FAILURE";
                                break;
                            case BatteryManager.BATTERY_HEALTH_UNKNOWN:
                            default:
                                health = "UNKNOWN";
                                break;
                            }
                            intent.putExtra("health", health);

                            String plugged;
                            switch (intent.getIntExtra("plugged", 0)) {
                            case BatteryManager.BATTERY_PLUGGED_AC:
                                plugged = "AC";
                                break;
                            case BatteryManager.BATTERY_PLUGGED_USB:
                                plugged = "USB";
                                break;
                            default:
                                plugged = "NONE";
                                break;
                            }
                            intent.putExtra("plugged", plugged);

                            String status;
                            switch (intent.getIntExtra("status", 0)) {
                            case BatteryManager.BATTERY_STATUS_CHARGING:
                                status = "CHARGING";
                                break;
                            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                                status = "DISCHARGING";
                                break;
                            case BatteryManager.BATTERY_STATUS_FULL:
                                status = "FULL";
                                break;
                            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                                status = "NOT_CHARGING";
                                break;
                            case BatteryManager.BATTERY_STATUS_UNKNOWN:
                            default:
                                status = "UNKNOWN";
                                break;
                            }
                            intent.putExtra("status", status);

                            Intent result = new Intent();
                            result.putExtra("data", intent);
                            _saved_value = result;
                            _saved_time  = 0;

                        }
                    };
            };
    }

    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {

        private final float MaxValue = 1.2f;
        private final int GraphLength = 10;
        private int _head = 0;
        private int _fill = 0;
        private float[] _values = new float[GraphLength];
        private Intent _intent = null;

        @Override
        protected boolean setData_impl(Intent intent) {
            intent = (Intent)intent.getParcelableExtra("data");
            this._intent = intent;
            int scale = intent.getIntExtra("scale", 0);
            int level = intent.getIntExtra("level", 0);
            float value = 0.0f;
            if (scale != 0) {
                value = level / (float)scale;
            }
            this._values[this._head] = value;

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

            for (int i = 1; i < GraphLength; i++) {

                int i1 = (i + offset + GraphLength - 1) % GraphLength;
                int i2 = (i + offset + GraphLength    ) % GraphLength;

                if (i2 >= this._fill) {
                    break;
                }
                float x = i * dx;


                float y1 = dh - (this._values[i1] / MaxValue) * dh;
                float y2 = dh - (this._values[i2] / MaxValue) * dh;
                paint.setColor(0xFF00FF00);
                canvas.drawLine(x-dx, y1, x, y2, paint);
            }

            if (this._intent != null) {
                Intent i = this._intent;
                int width  = canvas.getWidth();
                int height = canvas.getHeight();

                paint.setTextSize(20);
                paint.setAntiAlias(true);
                paint.setColor(0xFFFFFFFF);
                paint.setStyle(Paint.Style.STROKE);

                int x = 5;
                canvas.drawText(String.format("health=%s",
                                              i.getStringExtra("health")),
                                x, height-145, paint);
                canvas.drawText(String.format("plugged=%s",
                                              i.getStringExtra("plugged")),
                                x, height-125, paint);
                canvas.drawText(String.format("status=%s",
                                              i.getStringExtra("status")),
                                x, height-105, paint);
                canvas.drawText(String.format("present=%s",
                                              String.valueOf(i.getBooleanExtra("present", false))),
                                x, height-85, paint);
                canvas.drawText(String.format("scale=%d",
                                              i.getIntExtra("scale", 0)),
                                x, height-65, paint);
                canvas.drawText(String.format("level=%d",
                                              i.getIntExtra("level", 0)),
                                x, height-45, paint);
                canvas.drawText(String.format("voltage=%d",
                                              i.getIntExtra("voltage", 0)),
                                x, height-25, paint);
                canvas.drawText(String.format("temperature=%.1f",
                                              i.getIntExtra("temperature", 0) / 10.0f),
                                x, height-5, paint);
            }
        }
    }
}
