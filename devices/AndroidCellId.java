package com.sroom.cslablogger3.devices;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.telephony.NeighboringCellInfo;
import android.telephony.TelephonyManager;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;

import com.sroom.cslablogger3.AbstractLogger;
import com.sroom.cslablogger3.AbstractLoggerPainter;
import com.sroom.cslablogger3.AbstractLoggerThread;
import com.sroom.cslablogger3.ArgValue;
import com.sroom.cslablogger3.DeviceProfile;
import com.sroom.cslablogger3.IAsyncCallback;
import com.sroom.cslablogger3.ILoggingDeviceBuilder;
import com.sroom.cslablogger3.Profile;
import com.sroom.cslablogger3.R;
import com.sroom.cslablogger3.SingleChoiceSelector;
import com.sroom.cslablogger3.TextDataWriter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AndroidCellId implements ILoggingDeviceBuilder {

	@Override
	public DeviceProfile initDeviceProfile() {
		return new DeviceProfile(
	            "android cellid",
	            R.drawable.ic_androidcellid,
	            true,
	            new ArgValue[] {
	                new ArgValue("interval", "20000",
	                             new SingleChoiceSelector("2sec=2000,5sec=5000,10sec=10000,20sec=20000,30sec=30000,60sec=60000,120sec=120000")),
	                new ArgValue("save-data", "1",
	                             new SingleChoiceSelector("YES=1,NO=0")),
	            }
	        );
	}

	@Override
	public AbstractLogger getLogger() {
		// TODO 自動生成されたメソッド・スタブ
		return new Logger();
	}

	@Override
	public AbstractLoggerPainter getPainter() {
		// TODO 自動生成されたメソッド・スタブ
		return new Painter();
	}

    class Logger extends AbstractLogger {
        private int _interval;
        private TextDataWriter _csv = null;

        @Override
        public void init(Context context) {
            super.init(context);
            final Logger self = this;

            Profile profile = this._profile;
            if (profile.getArgValue("save-data").equals("1")) {
                this._csv = new TextDataWriter("cellid", "time,cellid,lac");
                // this._csv.setVerbose(true);
                this._csv.setMultiDirSort(true);
                this._csv.setExceptionCatch(new TextDataWriter.IExceptionCatch() {
                        @Override
                        public void caught(Exception e) {
                            self.onException(new LoggerException("Android CellID CSV Error!"));
                            e.printStackTrace();
                        }
                    });
            }
            this._interval = Integer.valueOf(profile.getArgValue("interval"));
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
                private TelephonyManager _manager = null;
                private int _count    = 0;
                private int _countMax = 0;
                private boolean _scanning = false;

                @Override
                public void begin() {
                    Logger owner = Logger.this;
                    this._manager =
                        (TelephonyManager)owner._context.getSystemService(Context.TELEPHONY_SERVICE);


                    this._count = this._countMax = owner._interval / 100;
                    this.setInterval(100);
                }

                @Override
                public void exec() {
                    this._count += 1;
                    if (!this._scanning && this._count >= this._countMax) {



                        this._scanning = true;

                        GsmCellLocation cellLocation = (GsmCellLocation) _manager.getCellLocation();
                        if(cellLocation!=null){
	                        int cid = cellLocation.getCid();
	                        int lac = cellLocation.getLac();
	                        if(cid!=-1&&lac!=-1){
	                            StringBuffer sb = new StringBuffer();
	                            sb.append(String.valueOf(cid));
	                            sb.append(",");
	                            sb.append(String.valueOf(lac));


	                			Intent intent = new Intent();
	                			Date dt = new Date();
	                            intent.putExtra("data", sb.toString());
	                            Log.d("CSLabLogger", "cellid data=" + sb.toString());
	                            sendData(dt, intent);

	                            RqsLocation(cid,lac);
	                        }
	                        else
	                        {
	                        	Log.d("CSLabLogger", "Android CellId error; cid:"+cid+" lac:"+lac);
	                        }
                        }
                        else
                        {
                        	Log.d("CSLabLogger", "Android CellId error; cannot obtain GsmCellLocation");

                        	List<NeighboringCellInfo> _clist=_manager.getNeighboringCellInfo();
                        	if(_clist!=null){
                        		Log.d("CSLabLogger", "Neighbor cells:"+_clist.size());
	                        	for (NeighboringCellInfo neighboringCellInfo : _clist) {
	                        		Log.d("CSLabLogger", "Neighbor cell info:"+neighboringCellInfo.toString());
								}
                        	}
                        	else
                        	{
                        		Log.d("CSLabLogger", "Android CellId error; cannot obtain NeighboringCellInfo");
                        	}
                        }
                        this._scanning = false;
                        this._count = 0;
                    }
                }

                private Boolean RqsLocation(int cid, int lac){

                	   Boolean result = false;

                	   String urlmmap = "http://www.google.com/glm/mmap";

                	      try {
                	       URL url = new URL(urlmmap);
                	          URLConnection conn = url.openConnection();
                	          HttpURLConnection httpConn = (HttpURLConnection) conn;
                	          httpConn.setRequestMethod("POST");
                	          httpConn.setDoOutput(true);
                	          httpConn.setDoInput(true);
                	  httpConn.connect();

                	  OutputStream outputStream = httpConn.getOutputStream();
                	        WriteData(outputStream, cid, lac);

                	        InputStream inputStream = httpConn.getInputStream();
                	        DataInputStream dataInputStream = new DataInputStream(inputStream);

                	        dataInputStream.readShort();
                	        dataInputStream.readByte();
                	        int code = dataInputStream.readInt();
                	        if (code == 0) {
                	         int myLatitude = dataInputStream.readInt();
                	         int myLongitude = dataInputStream.readInt();
                	         Log.d("CSLabLogger", "cellid loc=" + String.valueOf((float)myLatitude/1000000)+","+String.valueOf((float)myLongitude/1000000));

                	            result = true;

                	        }
                	 } catch (IOException e) {
                	  // TODO Auto-generated catch block
                	  e.printStackTrace();
                	 }

                	 return result;

                	  }

                	  private void WriteData(OutputStream out, int cid, int lac)
                	  throws IOException
                	  {
                	      DataOutputStream dataOutputStream = new DataOutputStream(out);
                	      dataOutputStream.writeShort(21);
                	      dataOutputStream.writeLong(0);
                	      dataOutputStream.writeUTF("en");
                	      dataOutputStream.writeUTF("Android");
                	      dataOutputStream.writeUTF("1.0");
                	      dataOutputStream.writeUTF("Web");
                	      dataOutputStream.writeByte(27);
                	      dataOutputStream.writeInt(0);
                	      dataOutputStream.writeInt(0);
                	      dataOutputStream.writeInt(3);
                	      dataOutputStream.writeUTF("");

                	      dataOutputStream.writeInt(cid);
                	      dataOutputStream.writeInt(lac);

                	      dataOutputStream.writeInt(0);
                	      dataOutputStream.writeInt(0);
                	      dataOutputStream.writeInt(0);
                	      dataOutputStream.writeInt(0);
                	      dataOutputStream.flush();
                	  }

                @Override
                public void end() {
                }


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

            List<String> list = new ArrayList<String>();
            for (String s : value.split(",")) {
            	list.add(s);
            }

            int dx = canvas.getWidth() / 2;
            for (int i = 0; i < list.size(); i += 2) {
                canvas.drawText(list.get(i  ), 10     , (i/2) * 20 + 20, paint);
                canvas.drawText(list.get(i+1), 10 + dx, (i/2) * 20 + 20, paint);
            }
        }
    }

}
