/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sunspotworld.heatsensorsalt;

import com.sun.spot.io.j2me.radiogram.Radiogram;
import com.sun.spot.io.j2me.radiogram.RadiogramConnection;
import com.sun.spot.resources.Resource;
import com.sun.spot.service.IService;
import java.io.IOException;
import java.io.InterruptedIOException;
import javax.microedition.io.Connector;

/**
 *
 * @author Alexandre
 */
class UnicastListener extends Resource implements IService, PacketTypes {
    
    /** Receive Unicast Connection. */
    private RadiogramConnection rcvConn = null;
    
    /** The tree topology management instance. */
    private TopologyManager manager = null;
    
    /** Radiogram used for sending data. */
    Radiogram sudg;
    
    /** Status of the service. */
    private int status = STOPPED;
    
    /** Our Address. */
    String ourAddress = System.getProperty("IEEE_ADDRESS");
    
    /** Thread used to run our service for listening to incoming Broadcasts. */
    private Thread thread = null;
    
    /** Name of the service running. */
    private String name = "Unicast Listener <" + CONNECTED_PORT + ">";
    
    /**
     * Constructor.
     * Initializes connections.
     */
    public UnicastListener(TopologyManager manager){
        this.manager = manager;
        try {
            rcvConn = (RadiogramConnection)Connector.open("radiogram://:" + CONNECTED_PORT);
            System.out.println("Listening with " + ourAddress + " on port " + CONNECTED_PORT);
        } catch(Exception e) {
            System.err.println("Caught " + e + " in server initialization.");
        }
    }

    /**
     * Main loop of the packet receiver.
     * Receive unicast packets and responds to these requests.
     */
    private void receiverLoop() {
        try {
            sudg = (Radiogram)rcvConn.newDatagram(rcvConn.getMaximumLength());
            
            /** Continually receive the next packet. */
            status = RUNNING;
            
            while (status == RUNNING && thread == Thread.currentThread()) {
                try {
                    sudg.reset();
                    rcvConn.receive(sudg);
                    /** Handle the packet. */
                    manager.handlePacket(UNICAST, sudg);
                } catch (InterruptedIOException ie) {
                    System.out.println("Packet receiver " + name + ": " + ie);
                    break;
                } catch (IOException e) {
                    e.printStackTrace(); // debug
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
