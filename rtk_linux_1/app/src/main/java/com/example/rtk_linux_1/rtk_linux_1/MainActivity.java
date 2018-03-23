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
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import android.util.Log;
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
    private final static int REQUEST_SEND_FILE = 2;

    private static final String SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB";

    private Button send_file;
    private TextView cmdInput;
    private TextView ConnFlag;
    private String remote_ble_address = null; //用于存储已连接蓝牙的地址
    private Timer timer;
    private TimerTask timerTask;
    private boolean BleIsOKFlag = false;
    private boolean ServerSocketIsClose = false;
    private int Conn_Error_Num = 0;
    private int Error_Num = 0;

    private int Num = 30; //因为每1分钟检测一次，2次就是2分钟
    private int Interval = 8000; //重连时间间隔 60S

    private int conn_status = 0;

    private Timer CloseSockettimer;
    private TimerTask CloseSockettimerTask;

    //震动
    Vibrator vibrator;

    private boolean HasSendOver = false;

    //发送文件
    private Uri uri;
    private String real_path = null;
    private boolean canSendFile = false;
    private InputStream AutoSendFileinputStream;
    private boolean canSend = false;
    private InputStreamReader inputStreamReader;
    private BufferedReader bufferedReader;

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

        //震动
        vibrator = (Vibrator)this.getSystemService(this.VIBRATOR_SERVICE);

        //选择文件发送
        send_file = (Button)findViewById(R.id.send_file);
        send_file.setOnClickListener(new SendFile());

        new WatchServerSocketThread().start();
        new ErrorThread().start();
        new SendFileThread().start();

        //new AutoSendFileThread().start();
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


    private class CloseSocketTimerTask extends TimerTask {
        @Override
        public void run() {
            while(true) {
                if (socket.isConnected()) {
                    try {
                        inputStreamReader.close();
                        bufferedReader.close();
                        AutoSendFileinputStream.close();
                        canSend = false;
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    ServerSocketIsClose = true;
                }
            }
        }
    }

    //监听服务器socket线程——检测对方设备是否主动断开蓝牙连接
    private class WatchServerSocketThread extends Thread {
        @Override
        public void run() {
            while(true) {
                switch (conn_status) {
                    case 0: //检测
                        if ( BleIsOKFlag && ServerSocketIsClose) {
                            //已经确认是连接断开
                            ServerSocketIsClose = false;
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
                            conn_status = 1;
                        }
                        break;

                    case 1: //重连
                        //建立客户端的socket
                        try {
                            socket = device.createRfcommSocketToServiceRecord(UUID.fromString(SPP_UUID));
                            socket.connect();
                        } catch (IOException e) {
                            Error_Num++;
                            if(Error_Num > Num) {
                                Error_Num = 0;
                                Conn_Error_Num++;
                            }
                            e.printStackTrace();

                            //注意注意[既然没连接成功，没必要执行下面的代码了]
                            continue;
                        }

                        Error_Num = 0;
                        timer = new Timer();
                        timerTask = new MyTimerTask();
                        timer.schedule(timerTask, Interval);
                        //UI更新
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
                        //开启线程接受蓝牙数据
                        try {
                            inputStream = socket.getInputStream();
                            new BLEInput().start();

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        //再次检测
                        conn_status = 0;
                        break;

                    default: //默认
                        System.out.print("nothing to do...");
                        break;
                }
            }
        }
    }

    //socket未连接退出
    private class ErrorThread extends Thread {
        @Override
        public void run() {
            while(true) {
                if (Conn_Error_Num > 0) {
                    vibrator.vibrate(3000);

                    try {
                        if (inputStream != null) {
                            inputStream.close();
                            outputstream.close();
                        }
                        if (socket != null) {
                            socket.close();
                            Toast.makeText(MainActivity.this, "超过2分钟没连接上", Toast.LENGTH_LONG).show();
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    //socket未连接退出
    private class SendFileThread extends Thread {
        String content = "";
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void run() {
            while(true) {
                if ( canSendFile ) {
                    canSendFile = false;
                    try {
                        if ("content".equalsIgnoreCase(uri.getScheme())) {
                            String[] projection = { "_data" };
                            Cursor cursor;
                            try {
                                cursor = getContentResolver().query(uri, projection, null, null, null);
                                int column_index = cursor.getColumnIndexOrThrow("_data");
                                if (cursor.moveToFirst()) {
                                    real_path = cursor.getString(column_index);
                                }
                            } catch (Exception e) {
                                Log.d("TAG", "get real_path error");
                            }
                        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                            real_path =  uri.getPath();
                        }

                        Log.d("TAG", real_path);

                        File f = new File(real_path);
                        AutoSendFileinputStream = new FileInputStream(f);

                        InputStreamReader inputStreamReader = new InputStreamReader(AutoSendFileinputStream);
                        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                        String line;
                        outputstream = socket.getOutputStream();

                        while ((line = bufferedReader.readLine()) != null) {
                            content += line+"\n";
                            outputstream.write(content.getBytes("utf-8"));
                            outputstream.flush();
                            content = "";
                        }
                        AutoSendFileinputStream.close();

                        HasSendOver = true;

                        Looper.prepare();
                        Toast.makeText(MainActivity.this,"发送文件成功", Toast.LENGTH_LONG).show();
                        Looper.loop();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    private class AutoSendFileThread extends Thread {
        String content = "";
        @Override
        public void run() {
            while(true) {
                if ( HasSendOver ) {    //经过第一次手动发送文件后才开始自动化测试
                    try {
                        sleep(15);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    try {

                        Log.d("AutoSendFileThread TAG", real_path);

                        File f = new File(real_path);
                        AutoSendFileinputStream = new FileInputStream(f);
                        inputStreamReader = new InputStreamReader(AutoSendFileinputStream);
                        bufferedReader = new BufferedReader(inputStreamReader);
                        String line;
                        outputstream = socket.getOutputStream();

                        CloseSockettimer = new Timer();
                        CloseSockettimerTask = new CloseSocketTimerTask();
                        CloseSockettimer.schedule(CloseSockettimerTask, 5); //发送8秒就关闭inputStream.close();

                        canSend = true;

                        while ( canSend && ((line = bufferedReader.readLine()) != null)) {
                            content += line+"\n";
                            outputstream.write(content.getBytes("utf-8"));
                            outputstream.flush();
                            content = "";
                        }
                        //AutoSendFileinputStream.close();

                        Looper.prepare();
                        Toast.makeText(MainActivity.this,"发送文件成功", Toast.LENGTH_LONG).show();
                        Looper.loop();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
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

    //跳转去选择文件发送
    private class SendFile implements OnClickListener {
        @Override
        public void onClick(View v) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQUEST_SEND_FILE);
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
            case REQUEST_SEND_FILE:
                if (resultCode == Activity.RESULT_OK) {
                    uri = data.getData();

                    Log.d("TAG", uri.toString());

                    canSendFile = true;
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
                    Log.d("read data", str);


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
