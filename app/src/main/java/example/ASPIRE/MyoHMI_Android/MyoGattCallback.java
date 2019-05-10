package example.ASPIRE.MyoHMI_Android;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

/**
 * Created by naoki on 15/04/15.
 */

public class MyoGattCallback extends BluetoothGattCallback {
    final static long NEVER_SLEEP_SEND_TIME = 10000;  // Milli Second
    /**
     * Service ID
     */
    private static final String MYO_CONTROL_ID = "d5060001-a904-deb9-4748-2c7f4a124842";
    private static final String MYO_EMG_DATA_ID = "d5060005-a904-deb9-4748-2c7f4a124842";
    private static final String MYO_IMU_DATA_ID = "d5060002-a904-deb9-4748-2c7f4a124842";
    private static final String MYO_CLASSIFIER_DATA_ID = "d5060003-a904-deb9-4748-2c7f4a124842";
    /**
     * Characteristics ID
     */
    private static final String MYO_INFO_ID = "d5060101-a904-deb9-4748-2c7f4a124842";
    private static final String FIRMWARE_ID = "d5060201-a904-deb9-4748-2c7f4a124842";
    private static final String COMMAND_ID = "d5060401-a904-deb9-4748-2c7f4a124842";

    private static final String EMG_0_ID = "d5060105-a904-deb9-4748-2c7f4a124842";
    private static final String EMG_1_ID = "d5060205-a904-deb9-4748-2c7f4a124842";
    private static final String EMG_2_ID = "d5060305-a904-deb9-4748-2c7f4a124842";
    private static final String EMG_3_ID = "d5060405-a904-deb9-4748-2c7f4a124842";
    private static final String IMU_0_ID = "d5060402-a904-deb9-4748-2c7f4a124842";
    private static final String Classifier_0_ID = "d5060103-a904-deb9-4748-2c7f4a124842";
    /**
     * android Characteristic ID (from Android Samples/BluetoothLeGatt/SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG)
     */
    private static final String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    public static double superTimeInitial;
    public static Boolean myoConnected;
    private static Plotter imuPlotter;
    private static Handler imuHandler;
    public ImuFragment imuFragment;
    ServerCommunicationThread thread;
    ClientCommunicationThread clientThread;
    long last_send_never_sleep_time_ms = System.currentTimeMillis();
    private Queue<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
    private Queue<BluetoothGattCharacteristic> readCharacteristicQueue = new LinkedList<BluetoothGattCharacteristic>();
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattCharacteristic mCharacteristic_command;
    private BluetoothGattCharacteristic mCharacteristic_emg0;
    private BluetoothGattCharacteristic mCharacteristic_emg1;
    private BluetoothGattCharacteristic mCharacteristic_emg2;
    private BluetoothGattCharacteristic mCharacteristic_emg3;
    private BluetoothGattCharacteristic mCharacteristic_imu0;
    private BluetoothGattCharacteristic mCharacteristic_classifier0;

    private MyoCommandList commandList = new MyoCommandList();
    private String TAG = "MyoGatt";
    private TextView textView;
    private TextView connectingTextView;
    private String callback_msg;
    private Handler mHandler;
    private Plotter plotter;
    private ProgressBar progress;
    private FeatureCalculator fcalc;//maybe needs to be later in process

    public MyoGattCallback(Handler handler, TextView view, ProgressBar prog, TextView connectingText, Plotter plot, View v) {
        mHandler = handler;
        connectingTextView = connectingText;
        textView = view;
        plotter = plot;
        progress = prog;
        fcalc = new FeatureCalculator(plotter);

//        thread = new ServerCommunicationThread();
//        thread.start();
//
//        clientThread = new ClientCommunicationThread();
//        clientThread.start();

//        fcalc.connect();
    }

    public MyoGattCallback(Handler handler, Plotter plot) {
        imuHandler = handler;
        imuPlotter = plot;
    }

    public static byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        Log.d(TAG, "onConnectionStateChange: " + status + " -> " + newState);
        if (newState == BluetoothProfile.STATE_CONNECTED) {

            gatt.discoverServices();

        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            // GATT Disconnected
            stopCallback();
            Log.d(TAG, "Bluetooth Disconnected");
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {

        super.onServicesDiscovered(gatt, status);
        Log.d(TAG, "onServicesDiscovered received: " + status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            // Find GATT Service
            BluetoothGattService service_emg = gatt.getService(UUID.fromString(MYO_EMG_DATA_ID));
            BluetoothGattService service_imu = gatt.getService(UUID.fromString(MYO_IMU_DATA_ID));
            BluetoothGattService service_classifier = gatt.getService(UUID.fromString(MYO_CLASSIFIER_DATA_ID));
            if (service_emg == null || service_imu == null || service_classifier == null) {//should probably break this into another separate checker for IMU service
                Log.d(TAG, "No Myo Service !!");
            } else {
                Log.d(TAG, "Find Myo Data Service !!");
                // Getting CommandCharacteristic
                mCharacteristic_emg0 = service_emg.getCharacteristic(UUID.fromString(EMG_0_ID));
                mCharacteristic_emg1 = service_emg.getCharacteristic(UUID.fromString(EMG_1_ID));
                mCharacteristic_emg2 = service_emg.getCharacteristic(UUID.fromString(EMG_2_ID));
                mCharacteristic_emg3 = service_emg.getCharacteristic(UUID.fromString(EMG_3_ID));
                mCharacteristic_imu0 = service_imu.getCharacteristic(UUID.fromString(IMU_0_ID));
                mCharacteristic_classifier0 = service_classifier.getCharacteristic(UUID.fromString(Classifier_0_ID));
                if (mCharacteristic_emg0 == null || mCharacteristic_imu0 == null || mCharacteristic_classifier0 == null) {
                    callback_msg = "Not Found Data Characteristics";
                } else {
                    // Setting the notification
                    boolean registered_0 = gatt.setCharacteristicNotification(mCharacteristic_emg0, true);
                    boolean registered_1 = gatt.setCharacteristicNotification(mCharacteristic_emg1, true);
                    boolean registered_2 = gatt.setCharacteristicNotification(mCharacteristic_emg2, true);
                    boolean registered_3 = gatt.setCharacteristicNotification(mCharacteristic_emg3, true);
                    boolean iregistered_0 = gatt.setCharacteristicNotification(mCharacteristic_imu0, true);
                    boolean cregistered_0 = gatt.setCharacteristicNotification(mCharacteristic_classifier0, true);
                if (!registered_0 || !iregistered_0 || !cregistered_0) {
                        Log.d(TAG, "EMG-Data Notification FALSE !!");
                    } else {
                        Log.d(TAG, "EMG-Data Notification TRUE !!");
                        // Turn ON the Characteristic Notification
                        BluetoothGattDescriptor descriptor_0 = mCharacteristic_emg0.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        BluetoothGattDescriptor descriptor_1 = mCharacteristic_emg1.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        BluetoothGattDescriptor descriptor_2 = mCharacteristic_emg2.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        BluetoothGattDescriptor descriptor_3 = mCharacteristic_emg3.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        BluetoothGattDescriptor idescriptor_0 = mCharacteristic_imu0.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        BluetoothGattDescriptor cdescriptor_0 = mCharacteristic_classifier0.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                        if (descriptor_0 != null || idescriptor_0 != null || cdescriptor_0 != null) {
                            cdescriptor_0.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                            idescriptor_0.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptor_0.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptor_1.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptor_2.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptor_3.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            descriptorWriteQueue.add(cdescriptor_0);
                            descriptorWriteQueue.add(idescriptor_0);
                            descriptorWriteQueue.add(descriptor_0);
                            descriptorWriteQueue.add(descriptor_1);
                            descriptorWriteQueue.add(descriptor_2);
                            descriptorWriteQueue.add(descriptor_3);
                            consumeAllGattDescriptors();
                            Log.d(TAG, "Set descriptor");
                        } else {
                            Log.d(TAG, "No descriptor");
                        }
                    }
                }
            }

            BluetoothGattService service = gatt.getService(UUID.fromString(MYO_CONTROL_ID));
            if (service == null) {
                Log.d(TAG, "No Myo Control Service !!");
            } else {
                Log.d(TAG, "Find Myo Control Service !!");
                // Get the MyoInfoCharacteristic
                BluetoothGattCharacteristic characteristic =
                        service.getCharacteristic(UUID.fromString(MYO_INFO_ID));
                if (characteristic == null) {
                } else {
                    Log.d(TAG, "Find read Characteristic !!");
                    //put the characteristic into the read queue
                    readCharacteristicQueue.add(characteristic);
                    //if there is only 1 item in the queue, then read it.  If more than 1, we handle asynchronously in the callback above
                    //GIVE PRECEDENCE to descriptor writes.  They must all finish first.
                    if ((readCharacteristicQueue.size() == 1) && (descriptorWriteQueue.size() == 0)) {
                        mBluetoothGatt.readCharacteristic(characteristic);
                    }
                }

                // Get CommandCharacteristic
                mCharacteristic_command = service.getCharacteristic(UUID.fromString(COMMAND_ID));
                if (mCharacteristic_command == null) {
                } else {
                    Log.d(TAG, "Find command Characteristic !!");
                }
            }
        }
    }

    public void writeGattDescriptor(BluetoothGattDescriptor d) {
        //put the descriptor into the write queue
        descriptorWriteQueue.add(d);
        //if there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
        if (descriptorWriteQueue.size() == 1) {
            mBluetoothGatt.writeDescriptor(d);
        }
    }

    public void consumeAllGattDescriptors() {
        mBluetoothGatt.writeDescriptor(descriptorWriteQueue.element());//the rest will happen in callback
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "Callback: Wrote GATT Descriptor successfully.");
        } else {
            Log.d(TAG, "Callback: Error writing GATT Descriptor: " + status);
        }
        descriptorWriteQueue.remove();  //pop the item that we just finishing writing
        //if there is more to write, do it!
        if (descriptorWriteQueue.size() > 0)
            mBluetoothGatt.writeDescriptor(descriptorWriteQueue.element());
        else if (readCharacteristicQueue.size() > 0)
            mBluetoothGatt.readCharacteristic(readCharacteristicQueue.element());
    }

    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

        if (status == BluetoothGatt.GATT_SUCCESS) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    //textView.setText("" + ListActivity.myoName + " Connected");
                    myoConnected = true;
                    progress.setVisibility(View.INVISIBLE);
                    connectingTextView.setVisibility(View.INVISIBLE);
                }
            });
        } else {
            myoConnected = false;
            Log.d(TAG, "onCharacteristicRead error: " + status);
        }
/*        if (setMyoControlCommand(commandList.sendImuAndEmg())) {
            Log.d(TAG, "Successfully started EMG stream");
        } else {
            Log.d(TAG, "Unable to start EMG stream");
        }*/

        if (setMyoControlCommand(commandList.sendImuEmgAndClassifier())) {
            Log.d(TAG, "Successfully started EMG IMU Classifier stream");
        } else {
            Log.d(TAG, "Unable to start EMG stream");
        }

    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.d(TAG, "onCharacteristicWrite success");
        } else {
            Log.d(TAG, "onCharacteristicWrite error: " + status);
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (EMG_0_ID.equals(characteristic.getUuid().toString()) || EMG_1_ID.equals(characteristic.getUuid().toString()) || EMG_2_ID.equals(characteristic.getUuid().toString()) || EMG_3_ID.equals(characteristic.getUuid().toString())) {

            long systemTime_ms = System.currentTimeMillis();
            superTimeInitial = systemTime_ms;
            byte[] emg_data = characteristic.getValue();

            byte[] emg_data1 = Arrays.copyOfRange(emg_data, 0, 8);
            byte[] emg_data2 = Arrays.copyOfRange(emg_data, 8, 16);

//            fcalc.pushFeatureBuffer(emg_data1);
            fcalc.pushFeatureBuffer(emg_data2);

//            byte cloudControl = 0;
//            if (fcalc.getClassify()) {
//                cloudControl = 1;
//            } else if (fcalc.getTrain()) {
//                cloudControl = 2;
//            }
//            byte[] emg_data_controlled = ArrayUtils.add(emg_data, 0, cloudControl);

//            /*The following can test the tcp latency by sending a timestamp to the server DOES NOT WORK NEED TO IMPLEMENT LAMPORT TIMESTAMPS*/
//            long currentTime = System.currentTimeMillis();
//            byte[] bytetime = longToBytes(currentTime);
//            byte[] guy = new byte[]{0,0,0,0,0,0,0,0,0};
//            byte[] bytetime17 = ArrayUtils.addAll(bytetime, guy);
//            System.out.println(currentTime);
////            System.out.println(Arrays.toString(bytetime17));

//            thread.send(emg_data_controlled);

            plotter.pushPlotter(emg_data);

            if (systemTime_ms > last_send_never_sleep_time_ms + NEVER_SLEEP_SEND_TIME) {
                setMyoControlCommand(commandList.sendUnSleep());
                last_send_never_sleep_time_ms = systemTime_ms;
            }
        } else if (IMU_0_ID.equals(characteristic.getUuid().toString())) {
            long systemTime_ms = System.currentTimeMillis();
            byte[] imu_data = characteristic.getValue();


            float orient_w, orient_x, orient_y, orient_z;
            short int16 = (short)(((imu_data[1] & 0xFF) << 8) | (imu_data[0] & 0xFF));
            orient_w = (float) int16/16384;
            int16 = (short)(((imu_data[3] & 0xFF) << 8) | (imu_data[2] & 0xFF));
            orient_x = (float) int16/16384;
            int16 = (short)(((imu_data[5] & 0xFF) << 8) | (imu_data[4] & 0xFF));
            orient_y = (float) int16/16384;
            int16 = (short)(((imu_data[7] & 0xFF) << 8) | (imu_data[6] & 0xFF));
            orient_z = (float) int16/16384;

            float accel_x, accel_y, accel_z;
            int16 = (short)(((imu_data[9] & 0xFF) << 8) | (imu_data[8] & 0xFF));
            accel_x = (float) int16/2048;
            int16 = (short)(((imu_data[11] & 0xFF) << 8) | (imu_data[10] & 0xFF));
            accel_y = (float) int16/2048;
            int16 = (short)(((imu_data[13] & 0xFF) << 8) | (imu_data[12] & 0xFF));
            accel_z = (float) int16/2048;

            float gyro_x, gyro_y, gyro_z;
            int16 = (short)(((imu_data[15] & 0xFF) << 8) | (imu_data[14] & 0xFF));
            gyro_x = (float) int16/16;
            int16 = (short)(((imu_data[17] & 0xFF) << 8) | (imu_data[16] & 0xFF));
            gyro_y = (float) int16/16;
            int16 = (short)(((imu_data[19] & 0xFF) << 8) | (imu_data[18] & 0xFF));
            gyro_z = (float) int16/16;


            Number[] emg_dataObj = ArrayUtils.toObject(imu_data);
            //ArrayList<Number> imu_data_list1 = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(emg_dataObj, 0, 10)));
            //ArrayList<Number> imu_data_list2 = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(emg_dataObj, 9, 19)));
            ArrayList<Number> imu_data_list1 = new ArrayList<>();
            imu_data_list1.add(0,accel_x);
            imu_data_list1.add(1,accel_y);
            imu_data_list1.add(2,accel_z);
            imu_data_list1.add(3,gyro_x);
            imu_data_list1.add(4,gyro_y);
            imu_data_list1.add(5,gyro_z);

            ArrayList<Number> imu_data_list2 = new ArrayList<>();
            imu_data_list2.add(0,orient_w);
            imu_data_list2.add(1,orient_x);
            imu_data_list2.add(2,orient_y);
            imu_data_list2.add(3,orient_z);

            DataVector dvec1 = new DataVector(true, 1, imu_data_list1.size(), imu_data_list1, systemTime_ms);
            DataVector dvec2 = new DataVector(true, 2, imu_data_list2.size(), imu_data_list2, systemTime_ms);
            fcalc.pushIMUFeatureBuffer(dvec1);
            fcalc.pushIMUFeatureBuffer(dvec2);

            imuFragment = new ImuFragment();
            imuFragment.sendIMUValues(dvec2);



            //SendToUnity.setQuaternion((float) data.getValue(0).byteValue(), (float) data.getValue(1).byteValue(), (float) data.getValue(2).byteValue(), (float) data.getValue(3).byteValue());
            SendToUnity.setQuaternion(orient_w, orient_x, orient_y, orient_z);

/*            try{
            Log.d(TAG, String.valueOf((int)imu_data[0]));
            Log.d(TAG, String.valueOf((int)imu_data[1]));
            Log.d(TAG, String.valueOf((int)imu_data[2]));
            Log.d(TAG, String.valueOf((int)imu_data[3]));
            Log.d(TAG, String.valueOf((int)imu_data[4]));
            Log.d(TAG, String.valueOf((int)imu_data[5]));
            Log.d(TAG, String.valueOf((int)imu_data[6]));
            Log.d(TAG, String.valueOf((int)imu_data[7]));
            Log.d(TAG, String.valueOf((int)imu_data[8]));
            Log.d(TAG, String.valueOf((int)imu_data[9]));
            Log.d(TAG, String.valueOf((int)imu_data[10]));
            Log.d(TAG, String.valueOf((int)imu_data[11]));
            Log.d(TAG, String.valueOf((int)imu_data[12]));
            Log.d(TAG, String.valueOf((int)imu_data[13]));
            Log.d(TAG, String.valueOf((int)imu_data[14]));
            Log.d(TAG, String.valueOf((int)imu_data[15]));
            Log.d(TAG, String.valueOf((int)imu_data[16]));
            Log.d(TAG, String.valueOf((int)imu_data[17]));
            Log.d(TAG, String.valueOf((int)imu_data[18]));
            Log.d(TAG, String.valueOf((int)imu_data[19]));
            } catch(Exception ex){

            }*/

        } else if (Classifier_0_ID.equals(characteristic.getUuid().toString())) {
            Log.d(TAG, "Classifier DATA RECEIVED");
            long systemTime_ms = System.currentTimeMillis();
            byte[] classifier_data = characteristic.getValue();

            short type, arm, x_axis_direction, pose, sync_result;
            type = (short) classifier_data[0];



            if (type == 1){
                Log.d(TAG, "Type: Arm Synced");
            } else if (type == 2){
                Log.d(TAG, "Type: Arm Unsynced");
            } else if (type == 3){
                Log.d(TAG, "Type: Pose");
            } else if (type == 4){
                Log.d(TAG, "Type: Unlocked");
            } else if (type == 5){
                Log.d(TAG, "Type: Locked");
            } else if (type == 6){
                Log.d(TAG, "Type: Failed");
            } else {
                Log.d(TAG, "Type: Invalid Result");
            }



            if (type == 1){
                arm = (short) classifier_data[1];
                x_axis_direction = (short) classifier_data[2];

                if(arm == 1){
                    Log.d(TAG, "ARM: Right");
                } else if (arm == 2){
                    Log.d(TAG, "ARM: Left");
                } else if(arm == -1){
                    Log.d(TAG, "ARM: Unknown");
                } else{
                    Log.d(TAG, "ARM: Invalid Result");
                }

                if(x_axis_direction == 1){
                    Log.d(TAG, "x_axis_direction: Towards Wrist");
                } else if (x_axis_direction == 2){
                    Log.d(TAG, "x_axis_direction: Towards Elbow");
                } else if (arm == -1){
                    Log.d(TAG, "x_axis_direction: Unknown");
                } else{
                    Log.d(TAG, "X_axis direction: Invalid Result");
                }

            } else if(type == 3){
                pose  = (short)(((classifier_data[2] & 0xFF) << 8) | (classifier_data[1] & 0xFF));


                if(pose == 0){
                    Log.d(TAG, "Pose: Rest");
                } else if (pose == 1){
                    Log.d(TAG, "Pose: Fist");
                } else if (pose == 2){
                    Log.d(TAG, "Pose: Wave In");
                } else if (pose == 3){
                    Log.d(TAG, "Pose: Wave Out");
                } else if (pose == 4){
                    Log.d(TAG, "Pose: Finger Spread");;
                } else if (pose == 5){
                    Log.d(TAG, "Pose: Double Tap");
                } else if (pose == -1){
                    Log.d(TAG, "Pose: Unknown");
                } else {
                    Log.d(TAG, "Pose: Invalid Result");
                }

                Log.d(TAG, "POSE: " + String.valueOf(pose));


            }  else if (type ==6){
                sync_result = (short) classifier_data[1];
                if(sync_result == 1){
                    Log.d(TAG, "Sync Result: Sync Gesture Performed too hard.");
                }
            }





            //SendToUnity.setQuaternion((float) data.getValue(0).byteValue(), (float) data.getValue(1).byteValue(), (float) data.getValue(2).byteValue(), (float) data.getValue(3).byteValue());
            //SendToUnity.setQuaternion(orient_w, orient_x, orient_y, orient_z);


        }
    }

    public void setBluetoothGatt(BluetoothGatt gatt) {
        mBluetoothGatt = gatt;
    }

    public boolean setMyoControlCommand(byte[] command) {
        if (mCharacteristic_command != null) {
            mCharacteristic_command.setValue(command);
            int i_prop = mCharacteristic_command.getProperties();
            if (i_prop == BluetoothGattCharacteristic.PROPERTY_WRITE) {
                if (mBluetoothGatt.writeCharacteristic(mCharacteristic_command)) {
                    return true;
                }
            }
        }
        return false;
    }

    public void stopCallback() {
        // Before the closing GATT, set Myo [Normal Sleep Mode].
        setMyoControlCommand(commandList.sendNormalSleep());
        descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
        readCharacteristicQueue = new LinkedList<BluetoothGattCharacteristic>();
        if (mCharacteristic_command != null) {
            mCharacteristic_command = null;
        }
        if (mCharacteristic_emg0 != null) {
            mCharacteristic_emg0 = null;
        }
        if (mBluetoothGatt != null) {
            mBluetoothGatt = null;
        }
    }

}
