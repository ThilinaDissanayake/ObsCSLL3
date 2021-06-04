package com.sroom.cslablogger3.devices;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Bundle;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityApi;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.Channel;
import com.google.android.gms.wearable.ChannelApi;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.sroom.cslablogger3.AbstractLogger;
import com.sroom.cslablogger3.AbstractLoggerPainter;
import com.sroom.cslablogger3.ArgValue;
import com.sroom.cslablogger3.DeviceProfile;
import com.sroom.cslablogger3.IAsyncCallback;
import com.sroom.cslablogger3.ILoggingDeviceBuilder;
import com.sroom.cslablogger3.Profile;
import com.sroom.cslablogger3.R;
import com.sroom.cslablogger3.SingleChoiceSelector;
import com.sroom.cslablogger3.TextDataWriter;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AndroidWatchAccelerometer implements ILoggingDeviceBuilder {

    @Override
    public DeviceProfile initDeviceProfile() {
        return new DeviceProfile(
                "android watch accelerometer",
                R.drawable.ic_watchaccelerometer,
                true,
                new ArgValue[] {

                        new ArgValue("samplerate", "8000",
                                new SingleChoiceSelector("15.625Hz=64000,31.25Hz=32000,62.5Hz=16000,125Hz=8000,250Hz=4000")),
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
    class Logger extends AbstractLogger implements NodeApi.NodeListener,
            ChannelApi.ChannelListener, GoogleApiClient.ConnectionCallbacks,
            GoogleApiClient.OnConnectionFailedListener {
        private final String TAG = "PhoneAccel";

        private static final String OPEN_DATA_CHANNEL_PATH = "/open_data_channel";
        private static final String SETUP_SENSOR_PATH = "/setup_sensor/"; //"/setup_sensor/{id}/{sample_rate}"
        private static final String START_COLLECTION_PATH = "/start_collection";
        private static final String STOP_COLLECTION_PATH = "/stop_collection";
        //private static final String RESET_SENSORS_PATH = "/reset_sensors";
        //private static final String RESET_DATA_CHANNEL_PATH = "/reset_data_channel";

        private GoogleApiClient mGoogleApiClient;
        private String mWearableNodeId;
        private String WEARABLE_CAPABILITY = "wearable_sensor_data";
        private BufferedReader mWearableInputStream;
        private ExecutorService mExecutorService;

        private TextDataWriter _csv = null;
        private int _samplerate = 10000;

        @Override
        public void init(Context context) {
            super.init(context);
            final Logger self = this;

            Profile profile = this._profile;
            this._samplerate = Integer.valueOf(profile.getArgValue("samplerate"));

            if (profile.getArgValue("save-data").equals("1")) {
                this._csv = new TextDataWriter("acc_w", "time,x,y,z");
                // this._csv.setVerbose(true);
                this._csv.setSplitSeconds(30);
                this._csv.setIntentConverter(new TextDataWriter.IIntentConverter() {
                    @Override
                    public String convert(Intent intent) {
                        float[] items = intent.getFloatArrayExtra("data");
                        return items[0] + "," + items[1] + "," + items[2];
                    }
                });
                this._csv.setFileRename(new TextDataWriter.IFileRename() {
                    @Override
                    public String rename(Date dt) {
                        return String.format("%s_acc_w.csv",
                                TextDataWriter.FilenameDateFormat.format(dt));
                    }
                });
                this._csv.setMultiDirSort(true);
                this._csv.setExceptionCatch(new TextDataWriter.IExceptionCatch() {
                    @Override
                    public void caught(Exception e) {
                        self.onException(new LoggerException("Android Watch Accelerometer CSV Error!"));
                        e.printStackTrace();
                    }
                });
            }
            this.setPackSize(4);
        }

        @Override
        public void start(final IAsyncCallback callback) {
            this.registerListener(this._csv);

            callback.done(true);

            mExecutorService = Executors.newCachedThreadPool();

            mGoogleApiClient = new GoogleApiClient.Builder(this._context)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();

            new Thread(new Runnable() {
                @Override
                public void run() {

                    while (mWearableNodeId == null || !mGoogleApiClient.isConnected())
                    {
                        try
                        {
                            Thread.sleep(100);
                        }
                        catch (InterruptedException e)
                        {
                            //Log.e(TAG, e.getMessage());
                        }
                    }

                    sendMessageToWearable(mWearableNodeId, OPEN_DATA_CHANNEL_PATH);
                    sendMessageToWearable(mWearableNodeId, SETUP_SENSOR_PATH + Sensor.TYPE_ACCELEROMETER + "/" + _samplerate);
                    sendMessageToWearable(mWearableNodeId, START_COLLECTION_PATH);

                }
            }).start();
        }


        @Override //ConnectionCallbacks
        public void onConnected(Bundle connectionHint) {
            //Log.d(TAG, "Google API Client was connected");
            Wearable.NodeApi.addListener(mGoogleApiClient, this);
            Wearable.ChannelApi.addListener(mGoogleApiClient, this);

            Wearable.CapabilityApi.getCapability(mGoogleApiClient, WEARABLE_CAPABILITY, CapabilityApi.FILTER_REACHABLE)
                    .setResultCallback(new ResultCallback<CapabilityApi.GetCapabilityResult>() {
                        @Override
                        public void onResult(CapabilityApi.GetCapabilityResult result) {
                            if (!result.getStatus().isSuccess()) {
                                //Log.e(TAG, "Failed to get capability");
                                return;
                            } else {
                                updateCapability(result.getCapability());
                            }

                        }
                    });

            CapabilityApi.CapabilityListener capabilityListener =
                    new CapabilityApi.CapabilityListener() {
                        @Override
                        public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
                            updateCapability(capabilityInfo);
                        }
                    };

            Wearable.CapabilityApi.addCapabilityListener(
                    mGoogleApiClient,
                    capabilityListener,
                    WEARABLE_CAPABILITY);

        }

        private void updateCapability(CapabilityInfo capabilityInfo) {
            //Log.d(TAG, "Updating capabilities");

            Set<Node> connectedNodes = capabilityInfo.getNodes();

            if (connectedNodes.size() > 0)
            {
                mWearableNodeId = pickBestNodeId(connectedNodes);
            }
            else
            {
                //Log.d(TAG, "No connected nodes were found");
            }
        }

        private String pickBestNodeId(Set<Node> nodes) {
            String bestNodeId = null;
            // Find a nearby node or pick one arbitrarily
            for (Node node : nodes) {
                //Log.d(TAG, "Next node: " + node.getId());
                if (node.isNearby()) {
                    return node.getId();
                }
                bestNodeId = node.getId();
            }
            return bestNodeId;
        }


        @Override //ConnectionCallbacks
        public void onConnectionSuspended(int cause) {
            //Log.d(TAG, "Connection to Google API client was suspended");
        }

        @Override //OnConnectionFailedListener
        public void onConnectionFailed(ConnectionResult result) {
            //Log.d(TAG, "On connection failed.");
            if (result.hasResolution()) {
                // There was an error with the resolution intent. Try again.
                mGoogleApiClient.connect();
            } else {
                //Log.e(TAG, "Connection to Google API client has failed");
                Wearable.NodeApi.removeListener(mGoogleApiClient, this);
                Wearable.ChannelApi.removeListener(mGoogleApiClient, this);

                if (mWearableInputStream != null)
                {
                    try
                    {
                        mWearableInputStream.close();
                    }
                    catch (IOException ex)
                    {
                        //Log.e(TAG, ex.getMessage());
                    }
                }
            }
        }

        @Override
        public void onOutputClosed(Channel channel, int closeReason, int appSpecificErrorCode)
        {
            //Log.d(TAG, "onOutputClosed: " + channel.getPath());
        }

        @Override
        public void onInputClosed(Channel channel, int closeReason, int appSpecificErrorCode)
        {
            //Log.d(TAG, "onInputClosed: " + channel.getPath());
        }

        @Override
        public void onChannelClosed(Channel channel, int closeReason, int appSpecificErrorCode)
        {
            //Log.d(TAG, "onChannelClosed: " + channel.getPath());
            if (mWearableInputStream != null)
            {
                try
                {
                    mWearableInputStream.close();
                }
                catch (IOException ex)
                {
                    //Log.e(TAG, ex.getMessage());
                }
            }
        }

        @Override
        public void onChannelOpened(final Channel channel) {
            //Log.d(TAG, "onChannelOpened: " + channel.getPath());

            channel.getInputStream(mGoogleApiClient).setResultCallback(
                    new ResultCallback<Channel.GetInputStreamResult>() {
                        @Override
                        public void onResult(Channel.GetInputStreamResult getInputStreamResult) {
                            if (getInputStreamResult.getStatus().isSuccess()) {
                                mWearableInputStream = new BufferedReader(new InputStreamReader(getInputStreamResult.getInputStream()));

                                mExecutorService.submit(new Runnable() {
                                    @Override
                                    public void run() {
                                        readInputStreamInBackground();
                                    }
                                });
                            }
                        }
                    }
            );
        }

        private void readInputStreamInBackground() {
            try {
                String dataLine;
                while (true) {
                    dataLine = mWearableInputStream.readLine();
                    String[] lineArray = dataLine.split(",");
                    //Log.d(TAG, dataLine);

                    int sensorType = Integer.parseInt(lineArray[0]);

                    if (sensorType == Sensor.TYPE_ACCELEROMETER) {
                        Date dt = new Date(Long.parseLong(lineArray[1]));
                        float x = Float.parseFloat(lineArray[2]);
                        float y = Float.parseFloat(lineArray[3]);
                        float z = Float.parseFloat(lineArray[4]);

                        Intent intent = new Intent();
                        intent.putExtra("data", new float[]{x, y, z});

                        sendData(dt, intent);
                    }
                }
            }
            catch (EOFException e)
            {
                //Log.d(TAG, "End of input stream reached.");
            }
            catch (IOException e)
            {
                //Log.e(TAG, e.getMessage());
            }
        }

        @Override //NodeListener
        public void onPeerConnected(final Node peer) {
            //Log.d(TAG, "onPeerConnected: " + peer);
        }

        @Override //NodeListener
        public void onPeerDisconnected(final Node peer) {
            //Log.d(TAG, "onPeerDisconnected: " + peer);
        }

        private void sendMessageToWearable(String node, String messagePath) {
            //Log.d(TAG, "Sending message to wearable: " + messagePath);
            Wearable.MessageApi.sendMessage(
                    mGoogleApiClient, node, messagePath, new byte[0]).setResultCallback(
                    new ResultCallback<MessageApi.SendMessageResult>() {
                        @Override
                        public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                            if (!sendMessageResult.getStatus().isSuccess()) {
                                //Log.e(TAG, "Failed to send message with status code: "
                                //        + sendMessageResult.getStatus().getStatusCode());
                            }
                        }
                    }
            );
        }


        @Override
        public void stop(final IAsyncCallback callback) {
            this.unregisterListener(this._csv);
            callback.done(true);

            sendMessageToWearable(mWearableNodeId, STOP_COLLECTION_PATH);

            Wearable.NodeApi.removeListener(mGoogleApiClient, this);
            Wearable.ChannelApi.removeListener(mGoogleApiClient, this);

            if (mWearableInputStream != null)
            {
                try
                {
                    mWearableInputStream.close();
                }
                catch (IOException ex)
                {
                    //Log.e(TAG, ex.getMessage());
                }
            }

            mGoogleApiClient.disconnect();
        }

        @Override
        public void finish() {
            if (this._csv != null) {
                this._csv.close();
            }
        }

        final private SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {

            }
            @Override
            public void onSensorChanged(SensorEvent event) {
                /*
                if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

                    Date dt = new Date();
                    float x = event.values[0];
                    float y = event.values[1];
                    float z = event.values[2];

                    Intent intent = new Intent();
                    intent.putExtra("data", new float[]{x, y, z});

                    sendData(dt, intent);
                }
                */
            }
        };
    }

    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {
        private final float MaxValue = 15.0f;
        private final int CounterMax  =  1;
        private final int GraphLength = 40;
        private final int[] COLORS = new int[] {0xFFFF0000, 0xFF00FF00, 0xFF6666FF};

        private int _counter = 0;
        private int _head = 0;
        private int _fill = 0;

        private float[][] _values = new float[GraphLength][3];

        @Override
        protected boolean setData_impl(Intent intent) {
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