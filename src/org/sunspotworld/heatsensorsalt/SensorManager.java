/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sunspotworld.heatsensorsalt;

import com.sun.spot.resources.Resources;
import com.sun.spot.resources.transducers.Condition;
import com.sun.spot.resources.transducers.IConditionListener;
import com.sun.spot.resources.transducers.ITemperatureInput;
import com.sun.spot.resources.transducers.SensorEvent;
import com.sun.spot.service.Task;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Timer;
import org.sunspotworld.heatsensorsalt.util.PacketTransmitter;

/**
 * This class is used to manage the temperature sensor and alert an event when the value
 * exceeds a given threshold.
 * 
 * @author Alexandre
 */
public class SensorManager implements PacketTypes {
    
    
    public static long SAMPLE_SCHEDULE_TIME = 10000;    // 10s
    public static long SAMPLE_DELAY = 20000;            // 20s
    
    ITemperatureInput sensor = (ITemperatureInput) 
                    Resources.lookup(ITemperatureInput.class);

    TopologyManager topology;                   // Used for the fault tolerance
    PacketTransmitter transmitter;              // Transmitter to send data to other SPOTs
    double threshold;                           // The threshold (in Celsius) used to trigger the timer
    Hashtable temperatures;                     // List of direct sons in the tree with temperature informations
    Condition thresholdExceeded;                // Monitors if the temperature sensor value exceeded the threshold
    Timer aggTimer = null;                      // Timer used to aggregate data
    Task allMsgRcv = null;                      // Task that monitors if all the sons have sent their data
    long firstRcv;                              // Date of the first received data before scheduling the timer
    int received;                               // Received messages from sons
    boolean started = false;                    // Indicates if the timer is scheduled to send temperatures
    boolean recovering = false;                 // Boolean indicating if the SPOT is trying to recover
    static boolean monitorLaunched = false;     // Indicates whether the monitor is launched or not
    
    /**
     * Constructor.
     */
    public SensorManager(TopologyManager manager, PacketTransmitter transmitter){
        this.topology = manager;
        this.transmitter = transmitter;
        temperatures = new Hashtable();
    }
    
    /**
     * Constructor.
     * With the threshold value to set.
     */
    public SensorManager(TopologyManager manager, PacketTransmitter transmitter, double threshold){
        this.topology = manager;
        this.transmitter = transmitter;
        this.threshold = threshold;
        temperatures = new Hashtable();
    }
    
    /**
     * Delayed start for sensor monitoring.
     * Calls {@link SensorManager#startTemperatureMonitor()} after 10s.
     * @param hops number of hops used to calculate how long will be the delay
     */
    public void delayedStart(int hops){
        long time = SAMPLE_DELAY / hops;
        System.out.println(
                "Starts the sensor monitoring in " 
                + time + 
                " seconds.."
        );
        Timer delay = new Timer();
        delay.schedule(
            new java.util.TimerTask()
            {
                public void run(){
                    startTemperatureMonitor();
                }
            }
            ,time
        );
    }
    
    /**
     * Monitors the temperature sensor and alerts a listener if the value exceeds the threshold.
     * Calls the {@link SensorManager#schedule()} method if the condition is met.
     */
    public void startTemperatureMonitor(){
        if(!monitorLaunched){
            monitorLaunched = true;
            System.out.println("Sensor monitoring started..");
            System.out.println("Value of threshold : " + this.threshold);
            IConditionListener thresholdListener = new IConditionListener(){
                public void conditionMet(SensorEvent evt, Condition condition) {
                    schedule();
                }
            };
            thresholdExceeded = new Condition(sensor, thresholdListener, 5 * 1000){
                private double last = -100.0;
                double actual;
                public boolean isMet(SensorEvent evt) {
                    try {
                        actual = ((ITemperatureInput) sensor).getCelsius();
                        if(last == -100.0)
                            last = actual;
                    } catch (IOException ex) {
                        ex.printStackTrace(); // debug
                        return false;
                    }
                    if(Math.abs(actual - last) >= threshold){
                        last = actual;
                        return true;
                    } else {
                        return false;
                    }
                }
            };
            thresholdExceeded.start();
        }
    }
    
    /**
     * Stop monitoring the temperature on the SPOT.
     */
    public void stopTemperatureMonitor(){
        if(monitorLaunched = true){
            monitorLaunched = false;
            thresholdExceeded.stop();
        }
    }
    
    /**
     * Schedules a specified task for execution after the specified delay.
     * Used for aggregation purposes. At the end of this timer (10s) calls 
     * {@link SensorManager#sendAggregatedTemperatures()} to send the temperatures to the father.
     * If there are no sons in the list, only calls {@link SensorManager#sendTemperature()}.
     */
    public void schedule(){
        if(!temperatures.isEmpty()){
            if(aggTimer == null)
                aggTimer = new Timer();
            if(!started){
                started = true;
                aggTimer.schedule(
                    new java.util.TimerTask()
                    {
                        public void run(){
                            reset();
                            sendAggregatedTemperatures();
                            started = false;
                        }
                    }
                    ,SAMPLE_SCHEDULE_TIME // 10s
                );
            }
        } else {
            sendTemperature();
        }
    }
    
    /**
     * Aggregates the temperature value of the sons including the temperature of the SPOT.
     * Calls {@link SensorManager#recoverFather()} if there is an error when sending data.
     */
    public void sendAggregatedTemperatures(){
        try {
            double avgTemp = 0.0;
            double intermediate;
            int coeff = 0;
            int i = 1;
            if(!temperatures.isEmpty()){
                Enumeration e = temperatures.keys();
                while (e.hasMoreElements()) {
                    Temperature temp = (Temperature)temperatures.get(e.nextElement());
                    temp.sent = true;
                    intermediate = temp.value.doubleValue() * temp.coeff;
                    System.out.println(
                            "[AGGR] "
                            + "Temperature " + 1
                            + " [Temp = " + intermediate + "]" 
                            + " [Coeff = " + temp.coeff + "]"
                    );
                    avgTemp += intermediate;
                    coeff += temp.coeff;
                    i++;
                }
                avgTemp += ((ITemperatureInput) sensor).getCelsius();
                coeff += 1;
                System.out.println(
                            "[AGGR] "
                            + "Final "
                            + " [Avg = " + avgTemp + "]" 
                            + " [Coeff = " + coeff + "]"
                );
                /* Calculates the weighted average */
                avgTemp /= coeff;
                /* Send aggegated temperatures to the father */
                transmitter.sendTemperature(
                        topology.info.father,
                        avgTemp,
                        coeff
                );
            }
        }
        /* We can't contact the father, try linking through another SPOT */
        catch(IOException e) {
            System.out.println(
                    "[ERROR]"
                    + "Cannot send data to father with address : " 
                    + topology.info.father
            );
            e.printStackTrace(); // debug
            stopTemperatureMonitor();
            recover();
        }
    }
    
    /**
     * Send the temperature of the SPOT to the father.
     */
    public void sendTemperature(){
        try {
            double temp = ((ITemperatureInput) sensor).getCelsius();
            transmitter.sendTemperature(
                    topology.info.father,
                    temp,
                    1
            );
        } 
        /* We can't contact the father, try linking through another SPOT */
        catch(IOException e) {
            System.out.println(
                    "[ERROR]"
                    + "Cannot send data to father with address : " 
                    + topology.info.father);
            e.printStackTrace();
            stopTemperatureMonitor();   
            recover();
        }
    }
    
    /**
     * Calls {@link TopologyManager#link()} to try linking through another SPOT.
     */
    public void recover(){
        if(!recovering){
            recovering = true;
            System.out.println(
                    "Trying to contact a SPOT in radio area"
            );
            topology.link();
        }
    }
    
    /**
     * Add an entry to {@link SensorManager#temperatures}
     * @param host The new son to add for the aggregation process
     * @param temperature Temperature information of this SPOT
     */
    public synchronized void putTemperature(String host, Temperature temperature){
        if(!monitorLaunched){
            startTemperatureMonitor();
        }
        temperatures.put(host, temperature);
        checkReceivedTemperatures();
        schedule();
    }
    
    /**
     * Sets the threshold value used to trigger an aggregation event.
     * @param threshold The new threshold value
     */
    public void setThreshold(double threshold){
        this.threshold = threshold;
    }
    
    /**
     * Check the number of temperature messages received.
     * If all sons have sent their data then don't waits for the end of the timer and calls
     * {@link Timer#cancel()} then {@link SensorManager#sendAggregatedTemperatures()}
     */
    public void checkReceivedTemperatures(){
        if(started && !temperatures.isEmpty()){
            Temperature temp;
            Enumeration e = temperatures.keys();
            while (e.hasMoreElements()) {
                temp = (Temperature)temperatures.get(e.nextElement());
                if(temp.sent)
                    continue;
                else
                    return;
            }
            aggTimer.cancel();
            sendAggregatedTemperatures();
        }
    }
    
    /**
     * Places the {@link Temperature#sent} at 'false' for all sons entries.
     */
    public void reset(){
        Temperature temp;
        Enumeration e = temperatures.keys();
        while (e.hasMoreElements()) {
            temp = (Temperature)temperatures.get(e.nextElement());
            temp.sent = false;
        }
    }
}
