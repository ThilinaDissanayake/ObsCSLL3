package com.sroom.cslablogger3.devices;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;

import com.sroom.cslablogger3.AbstractLogger;
import com.sroom.cslablogger3.AbstractLoggerPainter;
import com.sroom.cslablogger3.AbstractLoggerThread;
import com.sroom.cslablogger3.ArgValue;
import com.sroom.cslablogger3.DeviceProfile;
import com.sroom.cslablogger3.IAsyncCallback;
import com.sroom.cslablogger3.ILoggingDeviceBuilder;
import com.sroom.cslablogger3.TextDataWriter;

import com.sroom.cslablogger3.R;

/**
 *  デバイス用のテンプレートです
 *  あたらしいデバイスを追加するときに参考にしてください。
 */
public final class AATemplate implements ILoggingDeviceBuilder {

    /**
     *  デバイスの基本設定を返す
     */
	@Override
	public DeviceProfile initDeviceProfile() {
		return new DeviceProfile(
            "template",                  // デバイスの種類
            R.drawable.icon,             // デバイスのアイコンリソース
            DeviceProfile.TYPE_BUILTIN,  // TYPE_BUILTIN or TYPE_EXTERNAL
            new ArgValue[] {         // 設定項目
                
            }
        );
	}

    /**
     *  ロガーのインスタンスを返す (Seorviceから呼ばれる)
     */
	@Override
	public AbstractLogger getLogger() {
		return new Logger();
	}

    /**
     *  データ描画のインスタンスを返す (Activityから呼ばれる)
     *  注意：ロガーと描画インスタンスは異なるプロセスで生成されるため、
     *        たがいの状態を直接参照することは出来ません。
     */
	@Override
	public AbstractLoggerPainter getPainter() {
		return new Painter();
	}
    
    ////////////////////////////////////////////////////////////////////////////
    /**
     *  ロガー
     */
    class Logger extends AbstractLogger {
        private TextDataWriter _csv;
        
        @Override
        public void init(Context context) {
            super.init(context);
            final Logger self = this;
            
            this._csv = new TextDataWriter("Template", "time,");
            this._csv.setVerbose(true);
            this._csv.setIntentConverter(new TextDataWriter.IIntentConverter() {
                    @Override
                    public String convert(Intent intent) {
                        // intent.getStringExtra("data");
                        return "not implemented";
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
            this._csv.close();
        }
        
        ////////////////////////////////////////////////////////////////////////
        final AbstractLoggerThread thread = new AbstractLoggerThread() {
                @Override
                public void begin() {
                    
                }

                @Override
                public void exec() {
                    
                }

                @Override
                public void end() {
                    
                }
            };
    }

    ////////////////////////////////////////////////////////////////////////////
    class Painter extends AbstractLoggerPainter {
        
        @Override
        protected boolean setData_impl(Intent intent) {
            return false;
        }

        @Override
        public void draw(Canvas canvas) {
            
        }        
    }    
}
