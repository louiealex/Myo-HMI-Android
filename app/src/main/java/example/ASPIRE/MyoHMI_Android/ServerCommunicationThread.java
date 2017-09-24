package example.ASPIRE.MyoHMI_Android;
import android.util.Log;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;

public class ServerCommunicationThread extends Thread {

    public final static int TCP_SERVER_PORT = 9940;

    private ArrayList<byte[]> mMessages = new ArrayList<>();
    private String mServer;

    private boolean mRun = true;

    private final String ec2ip = "34.213.61.15";
    private final String alexHomeip = "2601:645:c100:b669:ad86:cf34:9b81:48e3";
    private final String icelabip = "34.213.61.15";
    private final String sfStateip = "10.143.132.221";

    int count = 0;

    public ServerCommunicationThread() {
        this.mServer = alexHomeip;
    }

    @Override
    public void run() {

        while (mRun) {
            Socket s = null;
            try {

                s = new Socket(mServer, TCP_SERVER_PORT);
                DataOutputStream output = new DataOutputStream(s.getOutputStream());

                while (mRun) {
                    byte[] message;
                    // Wait for message
                    synchronized (mMessages) {
                        while (mMessages.isEmpty()) {
                            try {
                                mMessages.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        // Get message and remove from the list
                        message = mMessages.get(0);
                        mMessages.remove(0);
                    }

//                    message = mMessages.get(0);
                    Log.d("sent$$$", Arrays.toString(message));
                    output.write(message);
//                    if (count == 100) {
//                        output.flush();
//                        count=0;
//                    }
                }


            } catch (UnknownHostException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                //close connection
                if (s != null) {
                    try {
                        s.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void send(byte[] message) {
        synchronized (mMessages) {
//            Log.d("Hello", Arrays.toString(message));
            mMessages.add(message);
            mMessages.notify();
        }
    }

    public void close() {
        mRun = false;
    }
}