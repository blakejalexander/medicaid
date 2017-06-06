package teamg.csse4011.medicaid;

import android.content.Intent;

import android.location.Location;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Enumeration;

import teamg.csse4011.medicaid.FallDetection.FallDetectionService;

public class MonitoredUser extends AppCompatActivity {

    private final String TAG = "MonitoredUser";

    private static boolean servicedStarted = false;

    public static MonitoredUser ThisInstance;

    private TextView portText, ipAddrText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitored_user);
        ThisInstance = this;
        Log.d("onCreate", "onCreate called");
        /* TODO: Blake - Replace me with a user friendly solution, like a radio button */
        /* Add a background data collection service for (now at least) debugging purposes. */

//        if (servicedStarted == false) {
//            servicedStarted = true;


        Intent intentGPS = new Intent(this, GPSService.class);
        //Start Service
        startService(intentGPS);

        Intent intent = new Intent(this, FallDetectionService.class);
        startService(intent);
//        }

        portText = (TextView)findViewById(R.id.portTextView);
        ipAddrText = (TextView)findViewById(R.id.ipAddrTextView);

        Log.d("4011server", getIpAddress());

        portText.setText(String.format("%d", SocketServerThread.SocketServerPORT));
        ipAddrText.setText(getIpAddress());

        Thread socketServerThread = new Thread(new SocketServerThread());
        socketServerThread.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("main", "destroy called");
    }

    /**
     * Assembles the JSON string to send to guardian user device when requested by it.
     */
    private static String statusString = "";
    public void updateStatusString() {
        String json = "{";

        /* Status */
        json += "\"status\":";
        json += "\"" + patientStatus + "\"";
        json += ",";

        /* Battery */
        json += "\"battery\":";
        json += String.format("%f", patientBattery);
        json += ",";

        /* Using GPS flag */
        json += "\"usingGps\":";
        json += patientGPSFlag ? "true" : "false";
        json += ",";

        /* Location - GPS */
        json += "\"location_gps\": {";
        json += "\"latitude\":";
        json += String.format("%f,", patientGPSLocation.getLatitude());
        json += "\"longitude\":";
        json += String.format("%f},", patientGPSLocation.getLongitude());

        /* Location - nearest node */
        json += "\"nearestBleNode\":";
        json += String.format("%d", 5);
        json += "}";

        this.statusString = json;
        Log.d("4011server", json);
    }


    /*
     * Update methods.
     */
    static String patientStatus = "OKAY";
    static float patientBattery = 99.0f;
    static Boolean patientGPSFlag = true;
    static Location patientGPSLocation = null;
    static int BleNearestNodeId = 1;
    public static void updateStatus() {

        patientStatus = "OKAY";
    }

    public static void updateBattery() {

        patientBattery = 50;
    }

    public static void updateGpsFlag() {
        /* TODO: Check if we can detect any single BLE node or not */
    }

    public static void updateGPSLatLng(Location location) {
        patientGPSLocation = location;
    }

    public static void updateBleNodePosition() {

    }

    Socket guardianSocket;
    /*
     * SERVER-SIDE CONNECTION CODE
     */
    ServerSocket serverSocket;
    private class SocketServerThread extends Thread {

        static final int SocketServerPORT = 8080;
        int count = 0;

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(SocketServerPORT);

                while (true) {
                    Socket socket = serverSocket.accept();
                    SocketServerReplyThread socketServerReplyThread = new SocketServerReplyThread(
                            socket, statusString);
                    socketServerReplyThread.run();
                    guardianSocket = socket;
                    Log.d("server", "Connected from " + socket.getInetAddress());

                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

    }

    private class SocketServerReplyThread extends Thread {

        private Socket hostThreadSocket;
        String outMsg;

        SocketServerReplyThread(Socket socket, String msg) {
            this.hostThreadSocket = socket;
            this.outMsg = msg;
        }
        PrintStream gPrintStream;
        @Override
        public void run() {
            OutputStream outputStream;
            String msgReply = outMsg;

            try {
                outputStream = hostThreadSocket.getOutputStream();
                PrintStream printStream = new PrintStream(outputStream);
                printStream.print(msgReply);
                gPrintStream = printStream;
                printStream.close();

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }

    }

    private String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface
                    .getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces
                        .nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface
                        .getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip += ""
                                + inetAddress.getHostAddress() + "\n";
                    }

                }

            }

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ip += "Something Wrong! " + e.toString() + "\n";
        }

        return ip;
    }
}
