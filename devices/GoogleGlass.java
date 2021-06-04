package com.sroom.cslablogger3.devices;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;
import android.widget.Toast;

import com.sroom.cslablogger3.*;


public final class GoogleGlass implements ILoggingDeviceBuilder {

    @Override
    public DeviceProfile initDeviceProfile() {
        return new DeviceProfile(
                "GoogleGlass",
                R.drawable.ic_googleglass,
                DeviceProfile.TYPE_EXTERNAL,
                new ArgValue[] {
                        new ArgValue("bluetooth", "NONE",
                                new BluetoothDeviceSelector()),
                        new ArgValue("visualizeSensor","0",
                                new SingleChoiceSelector("acceleration=0,gyro=1,rotation=2,magnetic=3,light=4")),
                        new ArgValue("acceleration","1",
                                new SingleChoiceSelector("YES=1,NO=0")),
                        new ArgValue("gyro","1",
                                new SingleChoiceSelector("YES=1,NO=0")),
                        new ArgValue("rotation","1",
                                new SingleChoiceSelector("YES=1,NO=0")),
                        new ArgValue("magnetic","1",
                                new SingleChoiceSelector("YES=1,NO=0")),
                        new ArgValue("light","1",
                                new SingleChoiceSelector("YES=1,NO=0")),
                        new ArgValue("image","1",
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

    class Logger extends AbstractLogger {

        //////////////////////////////////////////////////
        ///_csvはvisualizeするもの,その他はsaveするもの///
        //////////////////////////////////////////////////
        private TextDataWriter _csv = null;
        private TextDataWriter _csvAcc = null;
        private TextDataWriter _csvGyro = null;
        private TextDataWriter _csvRotation = null;
        private TextDataWriter _csvMagnetic = null;
        private TextDataWriter _csvLight = null;

        private String _visualizeSensor;
        private BluetoothClient _client;

        private SerializedObject _SOSensor = new SerializedObject();

        @Override
        public void init(Context context) {
            super.init(context);
            final Logger self = this;
            Profile profile = this._profile;

            _SOSensor.sensor = profile.getArgValue("acceleration") + profile.getArgValue("gyro") + profile.getArgValue("rotation") + profile.getArgValue("magnetic") + profile.getArgValue("light");

            Log.i("GoogleGlass_Init", "gyro : " + profile.getArgValue("gyro"));
            Log.i("GoogleGlass_Init", "save-data : " + profile.getArgValue("save-data"));

            if (profile.getArgValue("save-data").equals("1")) {
                if(profile.getArgValue("acceleration").equals("1")){
                    this._csvAcc = new TextDataWriter("GoogleGlass/acc/" + profile.getTag(), "time,x,y,z");
                    this._csvAcc.setSplitSeconds(30);
                    this._csvAcc.setIntentConverter(new TextDataWriter.IIntentConverter() {
                        @Override
                        public String convert(Intent intent) {
                            float[] items = intent.getFloatArrayExtra("data");
                            return items[0] + "," + items[1] + "," + items[2];
                        }
                    });
                    this._csvAcc.setMultiDirSort(true);
                    this._csvAcc.setExceptionCatch(new TextDataWriter.IExceptionCatch() {
                        @Override
                        public void caught(Exception e) {
                            self.onException(new LoggerException("GoogleGlass CSV Error!"));
                            e.printStackTrace();
                        }

                    });
                }
                if(profile.getArgValue("gyro").equals("1")){
                    this._csvGyro = new TextDataWriter("GoogleGlass/gyro/" + profile.getTag(), "time,x,y,z");
                    this._csvGyro.setSplitSeconds(30);
                    this._csvGyro.setIntentConverter(new TextDataWriter.IIntentConverter() {
                        @Override
                        public String convert(Intent intent) {
                            float[] items = intent.getFloatArrayExtra("data");
                            return items[0] + "," + items[1] + "," + items[2];
                        }
                    });
                    this._csvGyro.setMultiDirSort(true);
                    this._csvGyro.setExceptionCatch(new TextDataWriter.IExceptionCatch() {
                        @Override
                        public void caught(Exception e) {
                            self.onException(new LoggerException("GoogleGlass CSV Error!"));
                            e.printStackTrace();
                        }

                    });
                }
                if(profile.getArgValue("rotation").equals("1")){
                    this._csvRotation = new TextDataWriter("GoogleGlass/rot/" + profile.getTag(), "time,x,y,z");
                    this._csvRotation.setSplitSeconds(30);
                    this._csvRotation.setIntentConverter(new TextDataWriter.IIntentConverter() {
                        @Override
                        public String convert(Intent intent) {
                            float[] items = intent.getFloatArrayExtra("data");
                            return items[0] + "," + items[1] + "," + items[2];
                        }
                    });
                    this._csvRotation.setMultiDirSort(true);
                    this._csvRotation.setExceptionCatch(new TextDataWriter.IExceptionCatch() {
                        @Override
                        public void caught(Exception e) {
                            self.onException(new LoggerException("GoogleGlass CSV Error!"));
                            e.printStackTrace();
                        }

                    });
                }
                if(profile.getArgValue("magnetic").equals("1")){
                    this._csvMagnetic = new TextDataWriter("GoogleGlass/mag/" + profile.getTag(), "time,x,y,z");
                    this._csvMagnetic.setSplitSeconds(30);
                    this._csvMagnetic.setIntentConverter(new TextDataWriter.IIntentConverter() {
                        @Override
                        public String convert(Intent intent) {
                            float[] items = intent.getFloatArrayExtra("data");
                            return items[0] + "," + items[1] + "," + items[2];
                        }
                    });
                    this._csvMagnetic.setMultiDirSort(true);
                    this._csvMagnetic.setExceptionCatch(new TextDataWriter.IExceptionCatch() {
                        @Override
                        public void caught(Exception e) {
                            self.onException(new LoggerException("GoogleGlass CSV Error!"));
                            e.printStackTrace();
                        }

                    });
                }
                if(profile.getArgValue("light").equals("1")){
                    this._csvLight = new TextDataWriter("GoogleGlass/lig/" + profile.getTag(), "time,light1,light2,light3");
                    this._csvLight.setSplitSeconds(30);
                    this._csvLight.setIntentConverter(new TextDataWriter.IIntentConverter() {
                        @Override
                        public String convert(Intent intent) {
                            float[] items = intent.getFloatArrayExtra("data");
                            return items[0] + "," + items[1] + "," + items[2];
                        }
                    });
                    this._csvLight.setMultiDirSort(true);
                    this._csvLight.setExceptionCatch(new TextDataWriter.IExceptionCatch() {
                        @Override
                        public void caught(Exception e) {
                            self.onException(new LoggerException("GoogleGlass CSV Error!"));
                            e.printStackTrace();
                        }

                    });
                }
            }

            if(profile.getArgValue("visualizeSensor").equals("0")){
                _visualizeSensor = "acc";
                _csv = _csvAcc;
                _csvAcc = null;
            }else if(profile.getArgValue("visualizeSensor").equals("1")){
                _visualizeSensor = "gyro";
                _csv = _csvGyro;
                _csvGyro = null;
            }else if(profile.getArgValue("visualizeSensor").equals("2")){
                _visualizeSensor = "rot";
                _csv = _csvRotation;
                _csvRotation = null;
            }else if(profile.getArgValue("visualizeSensor").equals("3")){
                _visualizeSensor = "mag";
                _csv = _csvMagnetic;
                _csvMagnetic = null;
            }else if(profile.getArgValue("visualizeSensor").equals("4")){
                _visualizeSensor = "lig";
                _csv = _csvLight;
                _csvLight = null;
            }

            this._client = new BluetoothClient(profile.getArgValue("bluetooth"), true);

            this.setPackSize(2);
        }

        @Override
        public void connect(final IAsyncCallback callback) {
            Log.e("GoogleGlass","GoogleGlass Connect Start.");
//            Toast.makeText(this._context.getApplicationContext(), "これを入れるとconnectが終わらない.", Toast.LENGTH_SHORT).show();
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
            if (this._csvAcc != null) {
                this._csvAcc.close();
            }
            if (this._csvGyro != null) {
                this._csvGyro.close();
            }
            if (this._csvRotation != null) {
                this._csvRotation.close();
            }
            if (this._csvMagnetic != null) {
                this._csvMagnetic.close();
            }
            if (this._csvLight != null) {
                this._csvLight.close();
            }
        }

        ////////////////////////////////////////////////////////////////////////
        final AbstractLoggerThread thread = new AbstractLoggerThread() {
            private final int BUF_SIZE = 1024;
            private BluetoothSocket _socket;
            private InputStream  _in;
            private OutputStream _out;

            private byte _buf[] = new byte[BUF_SIZE];
            private int _buf_index = 0;

            private int _trycount = 0;
            private int _trycount_max = 1000;

            private SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HH:mm:ss.SSS");
            //            private ObjectInputStream objReader;
            @Override
            public void begin() {
                Logger logger = Logger.this;
                this._socket = logger._client.getBluetoothSocket();
                try {
                    this._in  = this._socket.getInputStream();
                    this._out = this._socket.getOutputStream();
//                    this.objReader = new ObjectInputStream(_in);

                    /*sett();*/

                    ObjectOutputStream objWriter = new ObjectOutputStream(_out);

                    Log.i("GoogleGlass_begin",_SOSensor.sensor);

                    objWriter.writeObject(_SOSensor);

/*                    String cmd = String.format("senb +000000000 %d %d 0",
                            logger._interval, logger._count);*/


                    //ここをきる
//                    String cmd = String.format("acc %d img %d",
//                            _profile.getArgValue("acceleration"), _profile.getArgValue("image"));
//
//                    send(cmd);
                } catch (IOException e) {
                    Logger.this.onException(new LoggerException("GoogleGlass Error!"));
                    e.printStackTrace();
                }
//                this.setInterval(logger._interval, logger._interval);
            }

            ////////////////////////////////////////////////////////////////////////////
            ////////////////////////ObjectStreamでデータを送る//////////////////////////
            ////////////////////////////////////////////////////////////////////////////
            @Override
            public void exec() {
                try {
                    Log.i("GoogleGlass","Object Reading Start.");
                    List<Object> o_list = new ArrayList<Object>();

//                    boolean bStopReading = true;
//
//                    while(bStopReading)
//                    {
//                        ObjectInputStream objReader = new ObjectInputStream(_in);
//                        Object tempObject = objReader.readObject();
//
//                        o_list.add(tempObject);
//                        if(tempObject == null)
//                        {
//                            bStopReading = false;
//                        }
//
//                        Log.i("GoogleGlass", "Loop");
//                    }

                    int nLoopCount = 30;
                    for(int i = 0; i < nLoopCount; i++)
                    {
                        ObjectInputStream objReader = new ObjectInputStream(_in);
                        Object tempObject = objReader.readObject();

                        o_list.add(tempObject);
                    }

                    Log.i("GoogleGlass","Object Reading End. object count : " + o_list.size());

                    for(Object o : o_list)
                    {
                        if (o != null) {
                            if (o instanceof SerializedObject) {
                                _buf_index = 0;

                                Date dt = new Date();

                                try {
                                    dt = sdf.parse(((SerializedObject) o).time);
                                }catch(Exception e)
                                {
                                    e.printStackTrace();
                                }

//                            String[] tempvalue = ((SerializedObject) o).value.split(",");

//                            float x = Float.parseFloat(tempvalue[0]);
//                            float y = Float.parseFloat(tempvalue[1]);
//                            float z = Float.parseFloat(tempvalue[2]);


                                Intent intent = new Intent();
                                intent.putExtra("data", new float[]{((SerializedObject) o).valueX, ((SerializedObject) o).valueY, ((SerializedObject) o).valueZ});

                                Log.i("GoogleGlass", ((SerializedObject) o).sensor);
                                boolean bSensorLog = ((SerializedObject) o).sensor.equals(_visualizeSensor);
                                Log.i("GoogleGlass",String.valueOf(bSensorLog));

                                if(((SerializedObject) o).sensor.equals(_visualizeSensor))
                                {
                                    Log.i("GoogleGlass_Visualize",((SerializedObject) o).sensor);
                                    Log.i("GoogleGlass_Visualize",((SerializedObject) o).time);
                                    Logger.this.sendData(dt, intent);
                                }else if(((SerializedObject) o).sensor.equals("acc"))
                                {
                                    Log.i("GoogleGlass_acc",((SerializedObject) o).time);
                                    intent.putExtra("dt", dt.getTime());
                                    _csvAcc.onReceive(0, intent);

                                }else if(((SerializedObject) o).sensor.equals("gyro"))
                                {
                                    Log.i("GoogleGlass_gyro",((SerializedObject) o).time);
                                    intent.putExtra("dt",dt.getTime());
                                    _csvGyro.onReceive(0, intent);
                                }else if(((SerializedObject) o).sensor.equals("rot"))
                                {
                                    Log.i("GoogleGlass_rot",((SerializedObject) o).time);
                                    intent.putExtra("dt",dt.getTime());
                                    _csvRotation.onReceive(0, intent);
                                }else if(((SerializedObject) o).sensor.equals("mag"))
                                {
                                    Log.i("GoogleGlass_mag",((SerializedObject) o).time);
                                    intent.putExtra("dt",dt.getTime());
                                    _csvMagnetic.onReceive(0, intent);
                                }else if(((SerializedObject) o).sensor.equals("lig"))
                                {
                                    Log.i("GoogleGlass_lig",((SerializedObject) o).time);
                                    intent.putExtra("dt",dt.getTime());
                                    _csvLight.onReceive(0, intent);
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Logger.this.onException(new LoggerException("GoogleGlass Error!"));
                    e.printStackTrace();
                }
            }

            @Override
            public void end() {
                try {
                    this._out.close();
                } catch (IOException e) {
                    Logger.this.onException(new LoggerException("GoogleGlass Error!"));
                    e.printStackTrace();
                }
            }


            ////////////////////////////////////////////////////////////////////////////
            ////////////////////////Streamでデータを送る////////////////////////////////
            ////////////////////////////////////////////////////////////////////////////
//            @Override
//            public void exec() {
//                try {
//                    if (this._in.available() > 0) {
//
//
//                        _buf[_buf_index] = (byte)this._in.read();
//
//
//                        //0x0a = \n
//                        if (_buf[_buf_index] == 0x0a)
//                        {
//                            //accにする
//                            readAccData(_buf);
//                            this._trycount = 0;
//                        }
//                        else
//                        {
//                            _buf_index += 1;
//                        }
//
//                    }
//
//                    this._trycount += 1;
//                    if (this._trycount >= this._trycount_max) {
//                        Logger.this.onException(new LoggerException("GoogleGlass: no responses!"));
//                    }
//
//                } catch (IOException e) {
//                    Logger.this.onException(new LoggerException("GoogleGlass Error!"));
//                    e.printStackTrace();
//                }
//            }

            //glass用
            private void readAccData(byte[] bytes){
                String tempData = new String(bytes,0,_buf_index);

                //bufferの中身を全てnullにする　いらない？
//                for(int i = 0; i <= _buf_index; i++)
//                {
//                    //0x00 = null
//                    bytes[i] = 0x00;
//                }

                _buf_index = 0;

                //0-20までの21文字が時間 [21],
                String[] _data = tempData.split(",");

                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HH:mm:ss.SSS");

                Date dt = new Date();

                try {
                    dt = sdf.parse(_data[0]);
                }catch(Exception e)
                {
                    e.printStackTrace();
                }

                float x = Float.parseFloat(_data[1]);
                float y = Float.parseFloat(_data[2]);
                float z = Float.parseFloat(_data[3]);

                Intent intent = new Intent();
                intent.putExtra("data", new float[] {x, y, z});

                Logger.this.sendData(dt, intent);
            }

            private void write(byte[] bytes) {
                try {
                    this._out.write(bytes);
                } catch (IOException e) {
                    Logger.this.onException(new LoggerException("GoogleGlass Error!"));
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
        };
    }

    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {
        private final float MaxValue = 15.0f;
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
    public class SerializedObject implements Serializable {
        private static final long serialVersionUID = 1395162780187766824L;
        public String sensor = "";
        public String time = "";
        public float valueX = 0;
        public float valueY = 0;
        public float valueZ = 0;
//    public byte[] pic = null;

    }
}
