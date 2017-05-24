package com.example.kohei.androidaccelerometerexample;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.graphics.drawable.Drawable;


import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import java.io.PrintWriter;
import java.io.BufferedWriter;
import java.io.FileWriter;

public class MyService extends Service implements SensorEventListener {

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private float mAccel; // acceleration apart from gravity
    private float mAccelCurrent; // current acceleration including gravity
    private float mAccelLast; // last acceleration including gravity

    private long start_time =  System.currentTimeMillis();
    private long time = System.currentTimeMillis();
    private float deltaX, deltaY, deltaZ, prevX = 0, prevY = 0, prevZ = 0;

    String filename = "myfile";
    String string = "Hello world!";
    FileOutputStream outputStream;


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mSensorManager.registerListener(this, mAccelerometer,
                SensorManager.SENSOR_DELAY_UI, new Handler());
        return START_STICKY;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        deltaX = x - prevX;
        deltaY = y - prevY;
        deltaZ = z - prevZ;
        prevX = x;
        prevY = y;
        prevZ = z;
        mAccelLast = mAccelCurrent;
        mAccelCurrent = (float) Math.sqrt((double) (x * x + y * y + z * z));
        float delta = mAccelCurrent - mAccelLast;
        mAccel = mAccel * 0.9f + delta; // perform low-cut filter
        time = System.currentTimeMillis() - start_time;
        String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
        String dirName = "yee1";
        String filePath = baseDir + File.separator + dirName;
        File f = new File(filePath);
        f.mkdirs();
        File csv = new File(f, "test.csv");
        BufferedWriter bw = null;
        FileWriter fw = null;
        try {
            /*String content = Float.toString(deltaX) +","+Float.toString(deltaY)+","+ Float.toString(deltaZk)+","+ Long.toString(time)+"\n";
            fw = new FileWriter(filePath , true);
            bw = new BufferedWriter(fw);
            bw.write(content);*/
            FileOutputStream out = new FileOutputStream(csv, true);
            PrintWriter pw = new PrintWriter(out, true);
            pw.println(Long.toString(System.currentTimeMillis()) + "," + Float.toString(deltaX) +","+Float.toString(deltaY)+","+ Float.toString(deltaZ));
            pw.close();
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mAccel > 11) {
           // showNotification();
        }
    }

    /**
     * show notification when Accel is more then the given int.
     */
    private void showNotification() {
        final NotificationManager mgr = (NotificationManager) this
                .getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder note = new NotificationCompat.Builder(this);
        note.setContentTitle("Device Accelerometer Notification");
        note.setTicker("New Message Alert!");
        note.setAutoCancel(true);
        // to set default sound/light/vibrate or all
        note.setDefaults(Notification.DEFAULT_ALL);
        // Icon to be set on Notification
        note.setSmallIcon(R.drawable.ic_launcher);
        // This pending intent will open after notification click
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this,
                MainActivity.class), 0);
        // set pending intent to notification builder
        note.setContentIntent(pi);
        mgr.notify(101, note.build());
    }

}
