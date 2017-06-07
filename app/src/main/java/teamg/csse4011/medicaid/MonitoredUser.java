package teamg.csse4011.medicaid;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;

import android.location.Location;
import android.os.BatteryManager;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collection;
import java.util.Enumeration;

import teamg.csse4011.medicaid.FallDetection.FallDetectionService;

public class MonitoredUser extends AppCompatActivity implements BeaconConsumer {
    private final String TAG = "MonitoredUser";

    private static boolean servicedStarted = false;

    public static MonitoredUser ThisInstance;

    private TextView portText, ipAddrText, beaconText;
    private BluetoothAdapter mBluetoothAdapter;

    private double beaconDist = 0.0;
    private String beaconName = "";
    private BeaconManager beaconManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitored_user);

        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
        beaconManager.bind(this);

        ThisInstance = this;

        /* TODO: Blake - Replace me with a user friendly solution, like a radio button */
        /* Add a background data collection service for (now at least) debugging purposes. */

        /* Start background data-acquiring services */
        /* Location - GPS outdoors */
        Intent intentGPS = new Intent(this, GPSService.class);
        startService(intentGPS);

        /* Fall-detection - acceleretomer */
        Intent intent = new Intent(this, FallDetectionService.class);
        startService(intent);

        /* Associate relevant update text fields */
        portText = (TextView)findViewById(R.id.portTextView);
        ipAddrText = (TextView)findViewById(R.id.ipAddrTextView);
        beaconText = (TextView)findViewById(R.id.beaconTextView);

        /* Display this device's connect details on the same network */
        portText.setText(String.format("%d", SocketServerThread.SocketServerPORT));
        ipAddrText.setText(getIpAddress());

        /* Open new socket to allow connection */
        Thread socketServerThread = new Thread(new SocketServerThread());
        socketServerThread.start();
    }



    private int counter0 = 0;

    private double[] beaconDistArray = new double[8];
    private boolean[] beaconPresent = new boolean[8];
    private int beaconIndex = 0;
    @Override
    public void onBeaconServiceConnect() {
        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                for (Beacon beacon: beacons) {
                    if (beacon.getDistance() > 0.0) {
                        counter0++;
                        if (counter0 >= 15) {
                            counter0 = 0;
                            for (int x = 0; x < 8; x++) {
                                beaconDistArray[x] = -1.0;
                            }
                        }
                        Log.d(TAG, "I see a beacon that is" + beacon.getDistance() + "m away.");
                        if (beacon.getBluetoothAddress().equals("E0:F0:AD:BC:86:B0")) {
                            beaconIndex = 0;
                        } else if (beacon.getBluetoothAddress().equals("F9:7C:B5:04:F3:58")) {
                            beaconIndex = 1;
                        } else if (beacon.getBluetoothAddress().equals("E5:DD:9B:2C:CB:DF")) {
                            beaconIndex = 2;
                        } else if (beacon.getBluetoothAddress().equals("F8:C8:32:0D:4D:AD")) {
                            beaconIndex = 3;
                        } else if (beacon.getBluetoothAddress().equals("D5:93:66:41:50:3E")) {
                            beaconIndex = 4;
                        } else if (beacon.getBluetoothAddress().equals("F9:44:C1:A0:7E:D5")) {
                            beaconIndex = 5;
                        } else if (beacon.getBluetoothAddress().equals("D3:4D:8E:07:3E:1F")) {
                            beaconIndex = 6;
                        } else if (beacon.getBluetoothAddress().equals("CC:FA:79:3A:74:D1")) {
                            beaconIndex = 7;
                        }
                        Log.d(TAG, Integer.toString(beaconIndex) + ": " + beacon.getDistance());
                        beaconDistArray[beaconIndex] = beacon.getDistance();
                        beaconDist = 999;
                        for (int i = 0; i < 8; i++) {
                            if (beaconDistArray[i] < beaconDist && beaconDistArray[i] != -1.0) {
                                beaconDist = beaconDistArray[i];
                                beaconName = beacon.getBluetoothAddress();
                                beaconIndex = i;
                            }
                        }
                        Log.d(TAG, "The CLOSEST beacon ID is" + beaconName);
                        MonitoredUser.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                beaconText.setText(Integer.toString(beaconIndex));
                                ThisInstance.updateBleNodePosition(beaconIndex);
                            }
                        });

                        // Perform distance-specific action here
                    }
                }
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {    }
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
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
//        json += "\"battery\":";
//        json += String.format("%f", patientBattery);
//        json += ",";

        patientGPSFlag = false;
        for (int n = 0; n < 8; n++) {
            if (beaconDistArray[n] == -1.0) {
                patientGPSFlag = true;
            } else {
                patientGPSFlag = false;
            }
        }

        /* Using GPS flag */
        json += "\"usingGps\":";
        json += patientGPSFlag ? "true" : "false";
        json += ",";

        /* Location - GPS */
        json += "\"latitude\":";
        json += String.format("%f,", patientGPSLocation.getLatitude());
        json += "\"longitude\":";
        json += String.format("%f,", patientGPSLocation.getLongitude());

        /* Location - nearest node */
        json += "\"nearestBleNode\":";
        json += String.format("%d", BleNearestNodeId);
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

    /* I would use enum or keys but a las, time is of the essence! */
    public static void updateStatus(String status) {
        /* Assume they are OKAY if given gibberish */
        if (status != "OKAY" && status != "FALLEN") {
            patientStatus = "OKAY";
        } else {
            patientStatus = status;
        }
    }

    /* TODO: Implement reading of battery percentage. */
    public static void updateBattery() {
        patientBattery = 0;
    }

    /* Determined by whether we can see a iBeacon node or not. */
    public static void updateGpsFlag() {
        /* TODO: Check if we can detect any single BLE node or not */
    }

    /* We only need latitude and longitude from this */
    public static void updateGPSLatLng(Location location) {
        patientGPSLocation = location;
    }

    /*
     * The unique ID of the node the device is closest to determines their approximate
     * in-doors location.
     */
    public static void updateBleNodePosition(int id) {
        BleNearestNodeId = id;
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

        /* Sends the current status string to guardian users connected when requested */
        @Override
        public void run() {
            OutputStream outputStream;
            String msgReply = outMsg;

            try {
                outputStream = hostThreadSocket.getOutputStream();
                PrintStream printStream = new PrintStream(outputStream);
                printStream.print(msgReply);
                printStream.close();

            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /* Get IP address of the local device */
    private String getIpAddress() {
        String ip = "";
        try {
            Enumeration<NetworkInterface> enumNetworkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (enumNetworkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = enumNetworkInterfaces.nextElement();
                Enumeration<InetAddress> enumInetAddress = networkInterface.getInetAddresses();
                while (enumInetAddress.hasMoreElements()) {
                    InetAddress inetAddress = enumInetAddress.nextElement();

                    if (inetAddress.isSiteLocalAddress()) {
                        ip += "" + inetAddress.getHostAddress() + "\n";
                    }
                } /* while (enumInetAddress.hasMoreElements()) */
            } /* while (enumNetworkInterfaces.hasMoreElements()) */

        } catch (SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            ip += "Something Wrong! " + e.toString() + "\n";
        }

        return ip;
    }
}
