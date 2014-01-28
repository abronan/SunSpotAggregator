/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sunspotworld.heatsensorsalt;

import com.sun.spot.io.j2me.radiogram.Radiogram;
import com.sun.spot.peripheral.ISleepManager;
import com.sun.spot.peripheral.Spot;
import com.sun.spot.service.Task;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;
import org.sunspotworld.heatsensorsalt.util.PacketTransmitter;
import org.sunspotworld.heatsensorsalt.util.RadioUtilities;

/**
 * This class constructs and maintains a logical tree topology over a physical network
 * of SunSPOTs. Uses the {@link SensorManager} class to aggregate and send temperature values.
 * 
 * @author Alexandre
 */
public class TopologyManager implements PacketTypes {
    
    /** Limit of LOST Broadcast before going on Shallow Sleep mode. */
    static final int LOST_LIMIT = 5;
    
    /** The Sleep Manager. */
    ISleepManager sleepManager = Spot.getInstance().getSleepManager();
    
    /** Our Address. */
    String ourAddress = System.getProperty("IEEE_ADDRESS");
    
    SPOTInfo info;                      // Our Information
    BroadcastListener rcvbroad;         // Broadcast receiver service
    UnicastListener rcvuni;             // Unicast receiver service
    Task ping;                          // Background task for pinging sons
    Task linkMonitor = null;            // Task used to monitor the link state
    PacketTransmitter transmitter;      // Transmitter to send data to other SPOTs
    SensorManager sensorManager;        // Used to aggregate data and monitor the temperature sensor
    Hashtable neighbors;                // The neighbors at radio distance. <String, SPOTInfo>
    Vector sons;                        // List of sons in the tree
    boolean checkdone = false;          // If the SPOT has done the CHECK Broadcast or not
    boolean attached = false;           // Indicates if the SPOT is linked to the tree
    boolean first = true;               // First attach attempt
    int lostCount = 0;                  // LOST diffuse counter when the connection is lost
    
    /**
     * Constructor.
     */
    public TopologyManager(SPOTInfo info){
        this.info = info;
        neighbors = new Hashtable();
        sons = new Vector();
        transmitter = new PacketTransmitter();
        /* Starts the SensorManager with a threshold of 0.2 (Celsius). */
        sensorManager = new SensorManager(this, transmitter);
    }
    
    /**
     * Handle a packet received on a Unicast or Broadcast connection.
     * 
     * @param connectionType The type of the connection. May be BROADCAST or UNICAST.
     * @param dg The received packet
     */
    public void handlePacket(byte connectionType, Radiogram dg){
        try{
            byte messageType = dg.readByte();
            switch(messageType){
                /** HELLO Request Handler. */
                case HELLO :
                    handleHELLO(connectionType, dg);
                    break;
                /** REPLY Response Handler. */
                case REPLY :
                    handleREPLY(connectionType, dg);
                    break;
                /** LOST Packet Handler. */
                case LOST :
                    handleLOST(connectionType, dg);
                    break;
                /** TIED Packet Handler. */
                case TIED :
                    handleTIED(dg);
                    break;
                /** TEMP Packet Handler. */
                case TEMP :
                    handleTEMP(dg);
                    break;  
                /** PING Packet Handler. */
                case PING:
                    // TODO send reply with temperature
                    break;
                default :
                    break;
            }
        } catch (IOException e) {
            System.out.println("[ERROR] Error in handling packet from : " + dg.getAddress());
            e.printStackTrace();
        }
    }
    
    /**
     * Handle a packet marked as HELLO.
     * These packets are used to build the tree and to discover neighbors.
     * 
     * @param connectionType The type of the connection. May be BROADCAST or UNICAST.
     * @param dg The received packet
     */
    public synchronized void handleHELLO(byte connectionType, Radiogram dg) throws IOException {
        String host = dg.getAddress();
        addOrUpdateHost(dg, host);
        if((!sons.contains(host) && info.father == null) 
                || (!sons.contains(host) && info.father != null && !info.father.equals(host))){
            if(!attached){
                attachToHost(dg, host);
                /* Response to the CHECK request */
                if(connectionType == BROADCAST){
                    transmitter.send(UNICAST, REPLY, info, host);
                }
                /* Diffuse CHECK request to neighbors in radio area */
                if(!checkdone){
                    transmitter.send(BROADCAST, HELLO, info, null);
                    checkdone = true;
                }
            } else { // attached
                transmitter.send(UNICAST, TIED, info, host);
            }
        }
    }
    
    /**
     * Handle a packet marked as REPLY.
     * These packets are used to signal that a SPOT has no father assigned.
     * 
     * @param connectionType The type of the connection. May be BROADCAST or UNICAST.
     * @param dg The received packet
     */
    public void handleREPLY(byte connectionType, Radiogram dg) throws IOException {
        String host = dg.getAddress();
        addOrUpdateHost(dg, host);
        if(!sons.contains(host) && info.father != null && !info.father.equals(host)){
            addSon(dg, host);
            /* Reply with a HELLO packet if the REPLY was Broadcasted */
            if(connectionType == BROADCAST){
                transmitter.send(UNICAST, HELLO, info, host);
            }
        }
    }
    
    /**
     * Handle a packet marked as LOST.
     * These packets are used to signal that a SPOT is in research of a new father.
     * 
     * @param connectionType The type of the connection. May be BROADCAST or UNICAST.
     * @param dg The received packet
     */
    public void handleLOST(byte connectionType, Radiogram dg) throws IOException {
        handleREPLY(connectionType, dg);
    }
    
    /**
     * Handle a packet marked as TIED.
     * These packets are used to signal that a SPOT has already a father.
     * 
     * @param dg The received packet
     */
    public void handleTIED(Radiogram dg) throws IOException {
        String host = dg.getAddress();
        addOrUpdateHost(dg, host);
        if(sons.contains(host))
            removeSon(host);
    }
    
    /**
     * Handle a packet marked as TEMP.
     * These packets are used to send a temperature value.
     * 
     * @param dg The received packet
     */
    public void handleTEMP(Radiogram dg) throws IOException {
        String host = dg.getAddress();
        if(sons.contains(host)){
            /* Reads TEMP packet informations */
            long date = dg.readLong();
            double value = dg.readDouble(); // ERROR
            int coeff = dg.readInt();
            /* Prints information about received data */
            System.out.println(
                    "[DATA] Data received from host : "
                    + dg.getAddress() 
                    + " [Value = " + value + "]" 
                    + " [Coefficient = " + coeff + "]"
            );
            /* Add or Update entry for the considered son */
            sensorManager.putTemperature(host, new Temperature(date, value, coeff));
        }
    }
    
    /**
     * Creates a new entry for a host in our neighbors list. 
     * Update this entry if already added.
     * 
     * @param dg The Radiogram with the SPOTInfo fields to extract
     * @param host The host IEEE address
     */
    public void addOrUpdateHost(Radiogram dg, String host) throws IOException{
        /* Creates a new SPOTInfo entry in case of a new host */
        if(!neighbors.containsKey(host)){
            System.out.println("Added entry in neighbors for : " + host);
            createInfo(dg, host);
        }
        /* Updates the entry with new informations */
        else {
            System.out.println("Updated entry in neighbors for : " + host);
            updateInfo(dg, host);
        }
    }
    
    /**
     * Removes the father.
     */
    public synchronized void removeFather(){
        if(info.father != null){
            attached = false;
            info.father = null;
        }
    }
    
    /**
     * Attach to a SPOT. The host is assigned as the father in the tree.
     * 
     * @param dg The Radiogram with the SPOTInfo fields to extract
     * @param hostAddr The IEEE address of the host we want to attach to
     */
    public void attachToHost(Radiogram dg, String hostAddr){
        info.father = hostAddr;
        attached = true;
        SPOTInfo hostInfo = (SPOTInfo)neighbors.get(hostAddr);
        info.threshold = hostInfo.threshold;
        if(hostInfo.nodetype == BASESTATION){
            info.hops = 0;
        } else {
            info.hops = hostInfo.hops + 1;
        }
        System.out.println("Attached to SPOT/host with address : " + hostAddr);
        sensorManager.setThreshold(info.threshold);
        /* Start monitoring temperatures with a delay regarding the hops count */
        if(first){
            sensorManager.delayedStart(info.hops);
            first = false;
        } else {
            sensorManager.startTemperatureMonitor();
        }
    }
    
    /**
     * Add a host to sons list. Increment the number of son in SPOTInfo instance.
     * Calls {@link TopologyManager#startPingMonitor()} when the first son is added in the list.
     *
     * @param dg The Radiogram with the SPOTInfo fields to extract
     * @param sonAddr The IEEE address of the host we want to add to sons list
     */
    public synchronized void addSon(Radiogram dg, String sonAddr){
        sons.addElement(sonAddr);
        sensorManager.temperatures.put(sonAddr, new Temperature());
        System.out.println("Son added to sons list : " + sonAddr);
        info.sonNumber++;
        if(info.sonNumber == 1){
            if(ping != null && !ping.isActive()){
                startSonMonitor();
            }
        }
    }
    
    /**
     * Removes a host from sons list. Decrement the number of son in SPOTInfo instance.
     * Calls {@link TopologyManager#stopPingMonitor()} when there are no sons remaining in the list.
     *
     * @param dg The Radiogram with the SPOTInfo fields to extract
     * @param sonAddr The IEEE address of the son we want to remove from the sons list
     */
    public synchronized void removeSon(String sonAddr){
        if(sons.removeElement(sonAddr)){
            sensorManager.temperatures.remove(sonAddr);
            System.out.println("Son removed : " + sonAddr);
            if(info.sonNumber != 0){
                info.sonNumber--;
                if(info.sonNumber == 0){
                    if(ping != null && ping.isActive()){
                        stopSonMonitor();
                    }
                }
            }
        }
    }
    
    /**
     * Handles the timeout case of Broadcast receive loop where the SPOT is non attached in the tree.
     *
     * @param conn The Broadcast connection to send a LOST info packet to all neighbors
     */
    public void handleTimeout()  {
        if(!attached){
            try {
                System.out.println("Broadcasting LOST request...");
                transmitter.send(BROADCAST, LOST, info, null);
            } catch (IOException ex) {
                System.out.println("Error Broadcasting LOST");
                ex.printStackTrace(); // debug
            } finally {
                lostCount++;
            }
        }
        if(lostCount == LOST_LIMIT){
            lostCount = 0;
            sleepManager = Spot.getInstance().getSleepManager();    
            sleepManager.enableDeepSleep();
            try {
                Thread.sleep(sleepManager.getMaximumShallowSleepTime());
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    /**
     * Creates a SPOTInfo instance for a new discovered neighbor/SPOT.
     * Add the instance into {@link TopologyManager#neighbors}
     *
     * @param dg The Radiograms with SPOT information.
     * @param host The IEEE address of the device from wich the informations are coming.
     */
    public void createInfo(Radiogram dg, String host) throws IOException {
        SPOTInfo hostinfo = new SPOTInfo();
        synchronized(neighbors){
            neighbors.put(host, hostinfo);
            RadioUtilities.putInfo(dg, hostinfo);
        }
    }
    
    /**
     * Updates the info of a SPOTInfo class from a received packet.
     * Updates the instance into {@link TopologyManager#neighbors}
     * 
     * @param dg The Radiograms with SPOT information.
     * @param host The IEEE address of the device from wich the informations are coming.
     */
    public void updateInfo(Radiogram dg, String host) throws IOException {
        SPOTInfo hostinfo = (SPOTInfo)neighbors.get(host);
        synchronized(hostinfo){
            RadioUtilities.putInfo(dg, hostinfo);
        }
    }
    
    /**
     * Try to link through another SPOT.
     * Calls {@link TopologyManager#monitorLink()} which monitors when the SPOT 
     * is linked again or not and put the SPOT into Shallow Sleep mode if not.
     */
    public void link(){
        try {
            info.father = null;
            attached = false;
            transmitter.send(BROADCAST, LOST, info, null);
            monitorLink();
        } catch (IOException ex) {
            ex.printStackTrace(); // debug
        }
    }
    
    /**
     * Monitors the link process. 
     * Try to link to the tree and alert the sensor monitor.
     */
    private void monitorLink(){
        linkMonitor = new Task(5 * 1000){
            public void doTask() {
                /* Broadcast LOST until we are not attached */
                if(info.father == null){
                    try {
                        transmitter.send(BROADCAST, LOST, info, null);
                    } catch (IOException ex) {
                        System.out.println("[LINK] Problem Broadcasting LOST...");
                        ex.printStackTrace();
                    }
                } else {
                    sensorManager.recovering = false;
                    stop();
                }
            }
        };
        linkMonitor.start();
    }
    
    /**
     * Ping sons every 60s.
     * Elements are removed from all the lists if we cannot contact them.
     */
    public void doPing(){
        String addr;
        if(!sons.isEmpty()){
            synchronized(sons){
                Enumeration e = sons.elements();
                while (e.hasMoreElements()) {
                    addr = (String) e.nextElement();
                    try {
                        transmitter.send(UNICAST, PING, info, (String) e.nextElement());
                    } catch (IOException exc) {
                        System.out.println("Cannot contact son with address : " + addr);
                        removeSon(addr);
                        exc.printStackTrace(); // debug
                    }
                }
            }
        }
    }
    
    /**
     * Start monitoring sons through periodic PING requests (60s).
     */
    public void startSonMonitor(){
        ping = new Task(60 * 1000){
            public void doTask() {
                doPing();
            }
        };
        ping.start();
    }
    
    /**
     * Stop monitoring sons. 
     * Used when the sons list becomes empty.
     */
    public void stopSonMonitor(){
        if(ping != null && ping.isActive()){
            ping.stop();
        }
    }
    
    /**
     * Tells the SPOT to go into Shallow Sleep mode. 
     * Used when the SPOT is unlinked to the network to spare the battery.
     */
    public void shallowSleep(){
        sleepManager = Spot.getInstance().getSleepManager();    
        sleepManager.enableDeepSleep();
        try {
            System.out.println("[SLEEP] Going to Shallow Sleep mode...");
            Thread.sleep(sleepManager.getMaximumShallowSleepTime());
        } catch (InterruptedException ex) {
            System.out.println("[SLEEP] Back from Shallow Sleep mode.");
            ex.printStackTrace();
        }
    }
}
