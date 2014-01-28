/*
 * SunSpotApplication.java
 *
 * Created on 2 avr. 2012 13:22:17;
 */

package org.sunspotworld.heatsensorsalt;

import com.sun.spot.util.Utils;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

/**
 * The startApp method of this class is called by the VM to start the
 * application.
 * 
 * The manifest specifies this class as MIDlet-1, which means it will
 * be selected for execution.
 */
public class HeatSensorsNetworkApplication extends MIDlet implements PacketTypes {

    private static final int SAMPLE_PERIOD = 30 * 1000;  // in milliseconds
    
    BroadcastListener rcvbroad;             // Broadcast receiver service
    UnicastListener rcvuni;                 // Unicast receiver service
    
    /** Our SPOTInfo instance. */
    SPOTInfo ourInfo = new SPOTInfo();
    
    public HeatSensorsNetworkApplication(){
        init();
    }
    
    public static void init(){}

    protected void startApp() throws MIDletStateChangeException {
        // Our address in String format
        String ourAddress = System.getProperty("IEEE_ADDRESS");
        System.out.println("Starting sensor sampler application on " + ourAddress + " ...");
        
        // Manage the network topology
        TopologyManager manager = new TopologyManager(ourInfo);
        
        /* Starting broadcast receiver service. */
        rcvbroad = new BroadcastListener(manager);
        rcvbroad.start();
        
        /* Starting unicast receiver service. */
        rcvuni = new UnicastListener(manager);
        rcvuni.start();
        
        while (true) {
            Utils.sleep(SAMPLE_PERIOD);
        }
    }

    protected void pauseApp() {
        // This is not currently called by the Squawk VM
    }

    /**
     * Called if the MIDlet is terminated by the system.
     * It is not called if MIDlet.notifyDestroyed() was called.
     *
     * @param unconditional If true the MIDlet must cleanup and release all resources.
     */
    protected void destroyApp(boolean unconditional) throws MIDletStateChangeException {
    }
}
