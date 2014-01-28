/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sunspotworld.heatsensorsalt;

import com.sun.spot.io.j2me.radiogram.Radiogram;
import com.sun.spot.io.j2me.radiogram.RadiogramConnection;
import com.sun.spot.peripheral.TimeoutException;
import com.sun.spot.resources.Resource;
import com.sun.spot.service.IService;
import java.io.IOException;
import java.io.InterruptedIOException;
import javax.microedition.io.Connector;

/**
 *
 * @author Alexandre
 */
class BroadcastListener extends Resource implements IService, PacketTypes {
    
    public static final long BROADCAST_WAIT = 9999999; // 1000000
    
    /** Receive Broadcast Connection. */
    private RadiogramConnection rcvbc = null;
    
    /** The tree topology management instance. */
    private TopologyManager manager = null;
    
    /** Our Address. */
    String ourAddress = System.getProperty("IEEE_ADDRESS");
    
    /** Radiogram used for receiving Broadcasted data. */
    Radiogram rbdg;
    
    /** Status of the service. */
    private int status = STOPPED;
    
    /** Thread used to run our service for listening to incoming Broadcasts. */
    private Thread thread = null;
    
    /** Name of the service running. */
    private String name = "Broadcast Listener <" + BROADCAST_PORT + ">";
    
    /** Indicates that we are listening for the first time at a Broadcast packet. */
    private boolean firstListen = true;
    
    /**
     * Constructor.
     * Initializes connections.
     */
    public BroadcastListener(TopologyManager manager){
        this.manager = manager;
        try {
            rcvbc = (RadiogramConnection) Connector.open("radiogram://:" + BROADCAST_PORT);
            System.out.println("Listening with " + ourAddress + " on port " + BROADCAST_PORT);
        } catch(Exception e) {
            System.err.println("Caught " + e + " in server initialization.");
            e.printStackTrace();
        }
    }

    /**
     * Main loop of the packet receiver.
     * Receive broadcast packets and respond to these requests.
     */
    private void receiverLoop() {
        try {
            rbdg = (Radiogram)rcvbc.newDatagram(rcvbc.getMaximumLength());
            
            /** Continually receive the next packet. */
            status = RUNNING;
            
            while (status == RUNNING && thread == Thread.currentThread()) {
                try {
                    rbdg.reset();
                    /** Wait 10sec for a broadcast request. */
                    if(firstListen){
                        rcvbc.setTimeout(BROADCAST_WAIT);
                        firstListen = false;
                    }
                    rcvbc.receive(rbdg);
                    /** Handle the packet. */
                    manager.handlePacket(BROADCAST, rbdg);
                } catch (TimeoutException te) {
                    if(!manager.attached)
                        manager.handleTimeout();
                } catch (InterruptedIOException ie) {
                    System.out.println("Packet receiver " + name + ": " + ie);
                    break;
                } catch (IOException e) {
                    e.printStackTrace();
                    System.out.println("Error in packet receiver " + name + ": " + e);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Fatal error in packet receiver " + name + ": " + e);
        }
        if (thread == Thread.currentThread()) {
            status = STOPPED;
        }
    }
    
    ////////////////////////////////
    //
    // IService defined methods
    //
    ////////////////////////////////
    
    /**
     * Stop packet receiver service if running.
     *
     * @return true if will stop service
     */
    public boolean stop() {
        if (status != STOPPED) {
            status = STOPPING;
        }
        System.out.println("Stopping packet receiver: " + name);
        return true;
    }
    
    /**
     * Start packet receiver service running.
     *
     * @return true if will start service
     */
    public boolean start() {
        if (status == STOPPED || status == STOPPING) {
            status = STARTING;
            thread = new Thread() {
                public void run() {
                    receiverLoop();
                }
            };
            thread.setPriority(Thread.MAX_PRIORITY - 3);
            thread.start();
            System.out.println("Starting packet receiver: " + name);
        }
        return true;
    }
    
    /**
     * Pause the service, and return whether successful.
     *
     * Since there is no particular state associated with this service
     * then pause() can be implemented by calling stop().
     *
     * @return true if the service was successfully paused
     */
    public boolean pause() {
        return stop();
    }

    /**
     * Resume the service, and return whether successful.
     *
     * Since there was no particular state associated with this service
     * then resume() can be implemented by calling start().
     *
     * @return true if the service was successfully resumed
     */
    public boolean resume() {
        return start();
    }

    /**
     * Return service name
     *
     * @return the name of this service
     */
    public String getServiceName() {
        return name;
    }
    
    /**
     * Assign a name to this service.
     *
     * @param who the new name for this service
     */
    public void setServiceName(String who) {
        if (who != null) {
            name = who;
        }
    }
    
    /**
     * Return if service is currently running.
     *
     * @return true if currently running
     */
    public boolean isRunning() {
        return status == RUNNING;
    }
    
    /**
     * Return current service status.
     *
     * @return current service status: STOPPED, STARTING, RUNNING, or STOPPING.
     */
    public int getStatus() {
        return status;
    }
    
    /**
     * Return whether service is started automatically on reboot.
     *
     * @return false as this service is never started automatically on reboot
     */
    public boolean getEnabled() {
        return false;
    }
    
    /**
     * Enable/disable whether service is started automatically. Noop for us.
     *
     * @param enable ignored
     */
    public void setEnabled(boolean enable) {
        // ignore
    }
    
}
