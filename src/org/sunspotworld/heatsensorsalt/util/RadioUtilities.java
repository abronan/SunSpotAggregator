/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sunspotworld.heatsensorsalt.util;

import com.sun.spot.io.j2me.radiogram.Radiogram;
import com.sun.spot.resources.Resources;
import com.sun.spot.resources.transducers.ITriColorLED;
import com.sun.spot.util.Utils;
import java.io.EOFException;
import java.io.IOException;
import org.sunspotworld.heatsensorsalt.PacketTypes;
import org.sunspotworld.heatsensorsalt.SPOTInfo;

/**
 *
 * @author Alexandre
 */
public class RadioUtilities implements PacketTypes {
    
    /** Initialize led for info related event. */
    private static ITriColorLED info_led = 
            (ITriColorLED)Resources.lookup(ITriColorLED.class, "LED1");
    
    /** Initialize led for temperature related event. */
    private static ITriColorLED data_led = 
            (ITriColorLED)Resources.lookup(ITriColorLED.class, "LED2");
    
    /** Initialize led for sampling event. */
    private static ITriColorLED error_led = 
            (ITriColorLED)Resources.lookup(ITriColorLED.class, "LED8");
    
    /**
     * Write the common header into a Radiogram.
     *
     * @param rdg the Radiogram to write the header info into
     * @param type the type of data packet to send
     */
    public static void writeHeader(Radiogram rdg, byte type) {
        try {
            rdg.reset();
            rdg.writeByte(type);
        } catch (IOException ex) {
            System.out.println("Error writing header: " + ex);
        }
    }
    
    /**
     * Construct a Radiogram with the SPOTInfo fields for the topology discovery.
     *
     * @param dg The Radiogram to construct.
     * @param type The type of the packet to send.
     * @param ourInfo The SPOT info fields used to fill the Datagram
     */
    public static void fillInfoRadiogram(Radiogram dg, byte type, SPOTInfo ourInfo){
        try {
            RadioUtilities.writeHeader(dg, type);
            dg.writeDouble(ourInfo.date);
            dg.writeByte(ourInfo.nodetype);
            if(ourInfo.father != null)
                dg.writeUTF(ourInfo.father);
            else
                dg.writeUTF("");
            dg.writeInt(ourInfo.sonNumber);
            dg.writeInt(ourInfo.hops);
            dg.writeDouble(ourInfo.threshold);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Construct a Radiogram with temperature information.
     *
     * @param dg The Radiogram to construct.
     */
    public static void fillTemperatureRadiogram(Radiogram dg, double temperature, int coefficient){
        try {
            RadioUtilities.writeHeader(dg, TEMP);
            /** Write the date. **/
            dg.writeLong(System.currentTimeMillis());
            /** Writing the temperature in Celsius. **/
            dg.writeDouble(temperature);
            /** The coefficient representing nodes in the subtree + self. **/
            dg.writeInt(coefficient);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
    /**
     * Put the info of a Radiogram in a SPOTInfo class.
     * Only if the date of the recept is more recent than the last recept.
     *
     * @param dg The Radiogram with SPOT information.
     * @param info Structure that takes the informations.
     */
    public static void putInfo(Radiogram dg, SPOTInfo info) throws EOFException, IOException {
        long date = dg.readLong();
        String fatherTest;
        try{
            if(info.date > date){
                info.date = date;
                info.nodetype = dg.readByte();
                fatherTest = dg.readUTF();
                if(!fatherTest.equals("")){
                    info.father = fatherTest;
                }
                info.sonNumber = dg.readInt();
                info.hops = dg.readInt();
                info.threshold = dg.readDouble();
            }
        } catch(EOFException e){
            e.printStackTrace(); // debug
            throw new EOFException();
        } catch(IOException ex){
            ex.printStackTrace(); // debug
            throw new IOException();
        }
    }
    
    /**
     * Send information event.
     */
    public static void flashInfoLed(){
        info_led.setRGB(255, 255, 255); // WHITE
        info_led.setOn();
        Utils.sleep(50);
        info_led.setOff();
    }
    
    /**
     * Send Temperature event.
     */
    public static void flashDataLed(){
        data_led.setRGB(0, 0, 255); // BLUE
        data_led.setOn();
        Utils.sleep(50);
        data_led.setOff();
    }
    
    /**
     * Error event.
     */
    public static void flashErrorLed(){
        error_led.setRGB(255, 0, 0); // RED
        error_led.setOn();
        Utils.sleep(50);
        error_led.setOff();
    }
}
