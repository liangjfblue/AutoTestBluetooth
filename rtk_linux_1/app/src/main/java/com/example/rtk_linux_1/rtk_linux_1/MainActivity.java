/*
*
*
* app name: Bluetooth Auto test
* author: 梁杰帆 Liangjf
* Create time： 2018.3.8
*
*
* */
package com.example.rtk_linux_1.rtk_linux_1;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Looper;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.InputStream;
import java.io.OutputStream;

import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import android.os.Handler;
import android.os.Message;
import android.content.Intent;
import android.view.View.OnClickListener;

public class MainActivity extends AppCompatActivity {

    boolean enable = false;
    boolean blecon = true;
    private InputStream inputStream;
    private OutputStream outputstream;
    BluetoothSocket socket = null;
    BluetoothDevice device = null;
    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
    private final static int REQUEST_CONNECT_DEVICE = 1;

    private static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";

    private TextView Connect_Error_Num;
    private TextView cmdInput;
    private TextView ConnFlag;
    private String remote_ble_address = null; //用于存储已连接蓝牙的地址
    private Timer timer;
    private TimerTask timerTask;
    private boolean SocketautoConn = false;
    private boolean BleIsOKFlag = false;
    private boolean ServerSocketIsClose = false;
    private boolean Notic2AutoConn = false;
    private int Conn_Error_Num = 0;
    private int Error_Num = 0;

    private int Num = 20; //因为每1分钟检测一次，2次就是2分钟
    private int Interval = 60000; //重连时间间隔 60S

    //震动
    Vibrator vibrator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        SysApplication.getInstance().addActivity(this);


        Button btn_exit1 = (Button)findViewById(R.id.exit_new);
        btn_exit1.setOnClickListener(new ExitAPP());

        ImageView img_ble =  (ImageView)findViewById(R.id.setble_new);
        img_ble.setOnClickListener(new setclick());

        Button bleSend = (Button)findViewById(R.id.bluetoothsend_new);
        bleSend.setOnClickListener(new BluetoothSend());

        cmdInput = (TextView)findViewById(R.id.cmd_input);
        cmdInput.setOnClickListener(new CMDInput());

        Button CloseBle = (Button)findViewById(R.id.close_ble);
        CloseBle.setOnClickListener(new CloseBLE());

        ConnFlag = (TextView)findViewById(R.id.connect_flag);

        Connect_Error_Num = (TextView)findViewById(R.id.connect_error_num);

        Button clean_num = (Button)findViewById(R.id.clean_num);
        clean_num.setOnClickListener(new CleanNum());

        //震动
        vibrator = (Vibrator)this.getSystemService(this.VIBRATOR_SERVICE);

        new SocketAutoConnThread().start();
        new WatchServerSocketThread().start();

        new ExitErrorThread().start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //修改退出的时候断开蓝牙连接，释放资源
        try {
            if (inputStream != null) {
                inputStream.close();
                outputstream.close();
            }
            if (socket != null) {
                socket.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //命令定时发送任务
    private class MyTimerTask extends TimerTask {
        @Override
        public void run() {
            if(socket.isConnected())
            {
                try {
                    String cmd_tmp = cmdInput.getText().toString();
                    outputstream = socket.getOutputStream();
                    outputstream.write((cmd_tmp.trim()+"\r\n").getBytes("utf-8"));
                    outputstream.flush();
                    Looper.prepare();
                    Toast.makeText(MainActivity.this,"发送成功 cmd: "+cmd_tmp, Toast.LENGTH_SHORT).show();
                    Looper.loop();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            else {
                Looper.prepare();
                Toast.makeText(MainActivity.this,"发送失败"	, Toast.LENGTH_SHORT).show();
                Looper.loop();
            }
        }
    }

    //监听服务器socket线程——检测对方设备是否主动断开蓝牙连接
    private class WatchServerSocketThread extends Thread {
        @Override
        public void run() {
            while(true) {
                if ( BleIsOKFlag && ServerSocketIsClose) {
                    //已经确认是连接断开
                    //BleIsOKFlag = false;
                    ServerSocketIsClose = false;
                    Notic2AutoConn = true;

                    //UI更新
                    new Thread() {
                        @Override
                        public void run() {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    ConnFlag.setText("未连接");
                                }
                            });
                        }
                    }.start();
                }
            }
        }
    }

    //一直检测，如果出现2分钟内蓝牙连接不上就报错，直接退出
    private class ExitErrorThread extends Thread {
        @Override
        public void run() {
            while(true) { //一直检测，如果出现2分钟内蓝牙连接不上就报错，直接退出
                if ( Conn_Error_Num > 0) {
                    vibrator.vibrate(3000);//先震动再退出

                    // TODO Auto-generated method stub
                    SysApplication.getInstance().exit();

                    //退出APP时先关闭资料和断开蓝牙-----?
                    try {
                        if ( (inputStream != null) || (outputstream != null)) {
                            inputStream.close();
                            outputstream.close();
                        }
                        if(socket != null)
                            socket.close();

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    //socket尝试自动重连线程
    private class SocketAutoConnThread extends Thread {
        @Override
        public void run() {
            while(true) {
                if( SocketautoConn && Notic2AutoConn) {
                    //Notic2AutoConn = false;
                    //ServerSocketIsClose = false;
                    //建立客户端的socket
                    try {
                        socket = device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID));
                        socket.connect();
                    } catch (IOException e) {
                        Error_Num++;
                        if(Error_Num > Num) {
                            Error_Num = 0;
                            Conn_Error_Num++;
                            vibrator.vibrate(1000);
                        }
                        e.printStackTrace();
                    }

                    if (socket.isConnected()) {
                        Notic2AutoConn = false;
                        BleIsOKFlag = true;

                        timer = new Timer();
                        timerTask = new MyTimerTask();
                        timer.schedule(timerTask, Interval);//定时10S后自动发送输入框的RTK控制命令.10S内输入命令,10S后发送

                        //UI更新
                        new Thread() {
                            @Override
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ConnFlag.setText("已连接");
                                        //Connect_Error_Num.setText(String.valueOf(Conn_Error_Num));
                                    }
                                });
                            }
                        }.start();
                    }

                    if (Conn_Error_Num > 0) {
                        //UI更新
                        new Thread() {
                            @Override
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        //ConnFlag.setText("已连接");
                                        Connect_Error_Num.setText(String.valueOf(Conn_Error_Num));
                                    }
                                });
                            }
                        }.start();
                    }

                    //开启线程接受蓝牙数据
                    try {
                        inputStream = socket.getInputStream();
                        new BLEInput().start();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    }

    //清除失败次数计数按键响应
    private class CleanNum implements OnClickListener {
        @Override
        public void onClick(View v) {
            Conn_Error_Num = 0;
            new Thread() {
                @Override
                public void run() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Connect_Error_Num.setText(String.valueOf(Conn_Error_Num));
                        }
                    });
                }
            }.start();
        }
    }

    //app主动关闭蓝牙按键响应
    private class CloseBLE implements OnClickListener {
        @Override
        public void onClick(View v) {
            try {
                if (inputStream != null) {
                    inputStream.close();
                    outputstream.close();
                }
                if (socket != null) {
                    socket.close();
                    Toast.makeText(MainActivity.this, "关闭蓝牙", Toast.LENGTH_SHORT).show();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //输入框响应——暂未使用
    private class CMDInput implements OnClickListener {
        @Override
        public void onClick(View v) {

        }
    }

    //蓝牙搜索响应
    private class setclick implements OnClickListener {
        @Override
        public void onClick(View arg0) {
            // TODO Auto-generated method stub
            if (adapter.isEnabled())
                enable = true;

            if (adapter != null) {
                if (!adapter.isEnabled()) {
                    Toast.makeText(MainActivity.this, "开启蓝牙", Toast.LENGTH_SHORT).show();
                    adapter.enable();
                }
            }
            else {
                Toast.makeText(MainActivity.this, "蓝牙不存在", Toast.LENGTH_SHORT).show();
            }
            if (adapter.isEnabled())
                blecon = true;
            new BLEThread().start();
        }
    }

    //蓝牙选择并初始化线程
    private class BLEThread extends Thread {
        public void run() {
            while (blecon) {
                if (adapter.isEnabled()) {
                    enable = true;
                    blecon = false;
                }
                if (enable) {
                    //蓝牙选择界面跳转，以返回结果方式
                    Intent serverIntent = new Intent(MainActivity.this, DeviceListActivity.class);
                    startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);
                    enable = false;
                }
            }
        }
    }

    //页面跳转返回处理
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {//判断请求码，识别是哪个Activity返回的结果
            case REQUEST_CONNECT_DEVICE:
                if (resultCode == Activity.RESULT_OK) {
                    remote_ble_address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                    device = adapter.getRemoteDevice(remote_ble_address);  //获得远程蓝牙设备

                    //建立客户端的socket
                    try {

                        socket = device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID));
                        socket.connect();
                    } catch (IOException e) {
                        Error_Num++;
                        if(Error_Num > Num) {
                            Error_Num = 0;
                            Conn_Error_Num++;
                            vibrator.vibrate(1000);
                        }
                        e.printStackTrace();
                    }

                    if (socket.isConnected()) {
                        new Thread() {
                            @Override
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        ConnFlag.setText("已连接");
                                    }
                                });
                            }
                        }.start();
                    }
                    if (Conn_Error_Num > 0) {
                        new Thread() {
                            @Override
                            public void run() {
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Connect_Error_Num.setText(String.valueOf(Conn_Error_Num));
                                    }
                                });
                            }
                        }.start();
                    }

                    SocketautoConn = true;

                    //用于判断建立socket连接后判断输入来监听对方设备是否断开
                    BleIsOKFlag = true;

                    //开启线程接受蓝牙数据
                    try {
                        inputStream = socket.getInputStream();
                        new BLEInput().start();

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                break;

            default:
                break;
        }
    }

    //退出app按键响应
    private class ExitAPP implements OnClickListener {
        @Override
        public void onClick(View view) {
            // TODO Auto-generated method stub
            SysApplication.getInstance().exit();

            //退出APP时先关闭资料和断开蓝牙-----?
            try {
                if ( (inputStream != null) || (outputstream != null)) {
                    inputStream.close();
                    outputstream.close();
                }
                if(socket != null)
                    socket.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //中断机制接收消息处理
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 2:
                    //修改，增加异常捕捉处理，避免因为原有逻辑截取字符串造成问题
                    try {
                        //在控制台输出接收到的数据，方便调试查看
                        //System.out.println("msg.getData: "+msg.getData().get("msg"));

                        /*String result = msg.getData().get("msg").toString();
                        String showstr = "";
                        showstr = showstr + result;

                        cmdInput.setText(showstr);*/

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;

                default:
                    break;
            }
        }
    };

    //蓝牙接收线程
    private class BLEInput extends Thread {
        String str;
        int num;

        public void run() {
            while (true) {
                byte buffer[] = new byte[1024];
                try {
                    num = inputStream.read(buffer);   //读取蓝牙数据
                    str = new String(buffer, 0, num);

                    //发送给中断处理数据
                    Message msg = new Message();
                    msg.what = 2;
                    Bundle data = new Bundle();
                    data.putString("msg", str);
                    msg.setData(data);
                    mHandler.sendMessage(msg);
                } catch (IOException e) {
                    e.printStackTrace();

                    //对方设备断开肯定是发送数据过来，这边接收是失败的
                    ServerSocketIsClose = true;

                    //修改断开连接时跳出死循环，避免线程不退出
                    break;
                }
            }
        }
    }

    //蓝牙发送线程
    private class BluetoothSend implements OnClickListener {
        public void onClick(View arg0) {

            // TODO Auto-generated method stub
            if(socket!=null)
            {
                try {
                    String cmd_tmp = cmdInput.getText().toString();
                    outputstream = socket.getOutputStream();
                    outputstream.write((cmd_tmp.trim()+"\r\n").getBytes("utf-8"));
                    outputstream.flush();
                    Toast.makeText(MainActivity.this,"发送成功 cmd: "+cmd_tmp, Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            else
                Toast.makeText(MainActivity.this,"发送失败"	, Toast.LENGTH_SHORT).show();
        }
    }
}
