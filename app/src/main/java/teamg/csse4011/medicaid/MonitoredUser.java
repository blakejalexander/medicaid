package teamg.csse4011.medicaid;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.io.DataInputStream;
import java.io.DataOutputStream;
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
import java.util.Timer;
import java.util.TimerTask;

import teamg.csse4011.medicaid.FallDetection.FallDetectionService;

public class MonitoredUser extends AppCompatActivity implements BeaconConsumer {
    private final String TAG = "MonitoredUser";

    public static MonitoredUser ThisInstance;

    /* GUI objects */

    private TextView portText, ipAddrText, beaconText;

    /* Patient variables */
    private boolean hasBeenAsked = false;
    private Timer answerTimer;
    private long lastAskTime = -1;
    private boolean needsHelp = false;

    /* BLE room localisation */
    private double beaconDist = 0.0;
    private String beaconName = "";
    private BeaconManager beaconManager;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                        BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        Log.d("4011help", "turned on");
                        beaconManager = BeaconManager.getInstanceForApplication(MonitoredUser.ThisInstance);
                        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
                        beaconManager.bind(MonitoredUser.ThisInstance);
                        break;
                    case BluetoothAdapter.STATE_OFF:
                        Log.d("4011help", "turned BLE off");
                        resetBluetoothDistances();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        Log.d("4011help", "turning BLE off");
                        resetBluetoothDistances();
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitored_user);
        ThisInstance = this;

        /* Beacon sensor */
        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24,d:25-25"));
        beaconManager.bind(this);
        /* TODO: Blake - Replace me with a user friendly solution, like a radio button */
        /* Add a background data collection service for (now at least) debugging purposes. */

        /* Start background data-acquiring services */
        /* Location - GPS outdoors */
        Intent intentGPS = new Intent(this, GPSService.class);
        startService(intentGPS);

        /* Fall-detection - accelerometer */
        Intent intent = new Intent(this, FallDetectionService.class);
        startService(intent);

        /* Associate relevant update text fields */
        portText = (TextView) findViewById(R.id.portTextView);
        ipAddrText = (TextView) findViewById(R.id.ipAddrTextView);
        beaconText = (TextView) findViewById(R.id.beaconTextView);

        /* Display this device's connect details on the same network */
        portText.setText(String.format("%d", SocketServerThread.SocketServerPORT));
        ipAddrText.setText(getIpAddress());

        /* Open new socket to allow connection */
        Thread socketServerThread = new Thread(new SocketServerThread());
        socketServerThread.start();

        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);

        handler.post(runnableCode);
    }

    private int counter0 = 0;

    private String[] beaconMacAddr =
            {
                    "E0:F0:AD:BC:86:B0", // 0
                    "F9:7C:B5:04:F3:58",
                    "E5:DD:9B:2C:CB:DF", // 2
                    "F8:C8:32:0D:4D:AD",
                    "D5:93:66:41:50:3E", // 4
                    "F9:44:C1:A0:7E:D5",
                    "D3:4D:8E:07:3E:1F", // 6
                    "CC:FA:79:3A:74:D1"
            };
    private double[] beaconDistArray = new double[8];
    private int beaconIndex = 0;

    @Override
    public void onBeaconServiceConnect() {
        beaconManager.setRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                for (Beacon beacon : beacons) {
                    if (beacon.getDistance() > 0.0) {

                    /* Reset distances every 15 reads to prevent non-present beacons from
                     * affecting closest beacon detection. */
                        counter0++;
                        if (counter0 >= 15) {
                            counter0 = 0;
                            resetBluetoothDistances();
                        }

                        Log.d(TAG, "I see a beacon that is " + beacon.getDistance() + "m away.");

                    /* Look through known beacons to determine which beacon triggered the event */
                        String macAddr = beacon.getBluetoothAddress();
                        for (int n = 0; n < 8; n++) {
                            if (beaconMacAddr[n].equals(macAddr)) {
                                beaconIndex = n;
                                break;
                            }
                        }

                        Log.d(TAG, Integer.toString(beaconIndex) + ": " + beacon.getDistance());
                        beaconDistArray[beaconIndex] = beacon.getDistance();

                    /* Find closest beacon from current stored distances for each beacon */
                        beaconDist = 999;
                        for (int i = 0; i < 8; i++) {
                            if (beaconDistArray[i] < beaconDist && beaconDistArray[i] != -1.0) {
                                beaconDist = beaconDistArray[i];
                                beaconName = macAddr;
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
                    }
                }
            }
        });

        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRangingUniqueId", null, null, null));
        } catch (RemoteException e) {

        }
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

        /* Determine whether to use GPS or not based on whether all stored distances are -1 or not */
        patientGPSFlag = false;
        for (int n = 0; n < 8; n++) {
            Log.d("4011help", "Val: " + String.valueOf(n) + " : " + String.valueOf(beaconDistArray[n]));
            patientGPSFlag = beaconDistArray[n] == -1.0;
            if (patientGPSFlag == false) {
                break;
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
        Log.d("4011server", "Updated to: " + json);
    }

    /*
     * Update methods.
     */
    static String patientStatus = "OKAY";
    static Boolean patientGPSFlag = true;
    static Location patientGPSLocation = null;
    static int BleNearestNodeId = 1;

    private void askPatientForSafety() {
        // custom dialog
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog);
        dialog.setTitle("Medicaid");

        // set the custom dialog components - text, image and button
        TextView text = (TextView) dialog.findViewById(R.id.text);
        text.setText("A fall has been detected. Are you OKAY?");
        text.setTextColor(Color.argb(255, 255, 255, 255));
        ImageView image = (ImageView) dialog.findViewById(R.id.image);
        image.setImageResource(android.R.drawable.ic_dialog_alert);

        Button dialogButton = (Button) dialog.findViewById(R.id.dialogButtonOK);

        // if button is clicked, close the custom dialog
        dialogButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                answerTimer.cancel();
                needsHelp = false;
                hasBeenAsked = false;
                MonitoredUser.updateStatus("OKAY");
                Log.d("4011help", "I DUN NEED HELP SIR!");
            }
        });
//        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        dialog.show();
    }

    /**
     * Update status of patient.
     * <p>
     * If they receive a PENDING status, due to a fall detected, open a dialog prompt with a
     * timeout. Timer succesfully expiring will set the status to NEEDS HELP and nullify any
     * future PENDING status updates -- until the person closes the dialog, which will reset
     * the status.
     *
     * @param status "OKAY", "PENDING" or "NEEDS HELP" -- everything else sets "OKAY"
     */
    public static void updateStatus(String status) {
        /* Assume they are OKAY if given gibberish */
        Log.d("4011help", "received status " + status);
        if (status != "OKAY" && status != "PENDING" && status != "NEEDS HELP") {
            patientStatus = "OKAY";
        } else if (status == "PENDING" && ThisInstance.needsHelp == false) {

            /* Ask patient for confirmation they are OKAY and track time asked */
            if (ThisInstance.hasBeenAsked == false) {
                ThisInstance.hasBeenAsked = true;
                TimerTask answerTimerTask = new TimerTask() {
                    @Override
                    public void run() {
                        Log.d("4011help", "I NEED HELP SIR!");

                        ThisInstance.needsHelp = true;
                        ThisInstance.answerTimer.cancel();
                        MonitoredUser.updateStatus("NEEDS HELP");

                    }
                };
                ThisInstance.answerTimer = new Timer();
                ThisInstance.answerTimer.schedule(answerTimerTask, 5000);
                ThisInstance.lastAskTime = android.os.SystemClock.uptimeMillis();
                ThisInstance.askPatientForSafety();
            } else {
                patientStatus = status;
            }

        } else if (status == "NEEDS HELP") {
            patientStatus = status;
            Log.d("4011help", "set status to " + status);
        } else {
            patientStatus = "OKAY";
        }
        MonitoredUser.ThisInstance.updateStatusString();
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

    private void resetBluetoothDistances() {
        Context context = getApplicationContext();
        CharSequence text = "Resetting BLE distances!";
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
        for (int x = 0; x < 8; x++) {
            beaconDistArray[x] = -1.0;
        }
    }

    // Create the Handler object (on the main thread by default)
    Handler handler = new Handler();

    // Define the code block to be executed
    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
        resetBluetoothDistances();

        /* Periodically repeat action */
        handler.postDelayed(runnableCode, 20000);
        }
    };

    Socket guardianSocket;
    /*
     * SERVER-SIDE CONNECTION CODE
     */
    ServerSocket serverSocket;

    private class SocketServerThread extends Thread {

        static final int SocketServerPORT = 8080;
        DataInputStream dataInputStream = null;
        DataOutputStream dataOutputStream = null;
        String message = "";
        @Override
        public void run() {
            Socket socket = null;

            try {
                serverSocket = new ServerSocket(SocketServerPORT);

                while (true) {
//                    socket = serverSocket.accept();
//                    Log.d("4011server", "got new connection from " + socket.getInetAddress().getHostAddress());
//                    dataInputStream = new DataInputStream(
//                            socket.getInputStream());
//
//
//                    /* Reply to guardian user if they requested */
//
//
//
//                    String messageFromClient = "";
//
//                    //Check available() before readUTF(),
//                    //to prevent program blocked if dataInputStream is empty
//                    if (dataInputStream.available() > 0) {
//                        messageFromClient = dataInputStream.readUTF();
//                    }
//
//                    message += "From " + socket.getInetAddress()
//                            + ":" + socket.getPort() + "\n"
//                            + "Msg from client: " + messageFromClient + "\n";
//
//                    MonitoredUser.this.runOnUiThread(new Runnable() {
//
//                        @Override
//                        public void run() {
//                            Log.d("4011server", message);
//                        }
//                    });
//
//                    if (socket.getInetAddress().getHostAddress().equals("172.20.10.7")) {
//                        Log.d("4011server", "Sending to guardian");
//                        SocketServerReplyThread socketServerReplyThread = new SocketServerReplyThread(
//                                socket, statusString);
//                        socketServerReplyThread.run();
//                    } else {
//                        dataOutputStream = new DataOutputStream(
//                                socket.getOutputStream());
//                    }
//=======
                    socket = serverSocket.accept();
                    updateStatusString();
                    SocketServerReplyThread socketServerReplyThread = new SocketServerReplyThread(
                            socket, statusString);
                    socketServerReplyThread.run();
                    guardianSocket = socket;
                    Log.d("4011help", "Connected from " + socket.getInetAddress() + " sent " + statusString);
//
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                Log.d("4011server", e.toString());
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                if (dataInputStream != null) {
                    try {
                        dataInputStream.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }

                if (dataOutputStream != null) {
                    try {
                        dataOutputStream.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
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

