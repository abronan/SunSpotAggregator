/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sunspotworld.heatsensorsalt.util;

import com.sun.spot.io.j2me.radiogram.Radiogram;
import com.sun.spot.io.j2me.radiogram.RadiogramConnection;
import com.sun.spot.peripheral.NoRouteException;
import java.io.IOException;
import javax.microedition.io.Connector;
import org.sunspotworld.heatsensorsalt.PacketTypes;
import org.sunspotworld.heatsensorsalt.SPOTInfo;

/**
 *
 * @author Alexandre
 */
public class PacketTransmitter implements PacketTypes {
    
    /** Send Broadcast Connection. */
    private RadiogramConnection sendbc = null;
    
    /** Our Address. */
    String ourAddress = System.getProperty("IEEE_ADDRESS");
    
    /**
     * Constructor.
     */
    public PacketTransmitter(){
        try {
            /* Open up a broadcast connection to the host port */
            sendbc = (RadiogramConnection) 
                    Connector.open("radiogram://broadcast:" + BROADCAST_PORT);
        } catch (Exception e) {
            System.err.println("Caught " + e + " in connection initialization.");
            e.printStackTrace();
        }
    }
    
    /**
     * Send a typed packet to a specific host or to all neighbors regarding the connection type.
     *
     * @param connectionType The type of the connection. May be BROADCAST or UNICAST.
     * @param messageType The type of the packet. May be CHECK, TIED or LOST.
     * @param host The host we want to transmit the packet. null in case of a Broadcast.
     */
    public void send(byte connectionType, byte messageType, SPOTInfo info, String host) throws IOException {
        /* Updates the timestamp of the packet */
        info.update();
        String type;
        switch(messageType){
            case HELLO:
                type = "HELLO";
                break;
            case REPLY:
                type = "REPLY";
                break;
            case LOST:
                type = "LOST";
                break;
            case PING:
                type = "PING";
                break;
            case TIED:
                type = "TIED";
                break;
            default:
                type = "default";
                break;
        }
        switch(connectionType){
            /** BROADCAST SECTION. */
            case BROADCAST:
                System.out.println("[" + ourAddress + "] Sending Broadcast " + type + "...");
                broadcast(info, messageType);
                break;
            /** UNICAST SECTION. */
            case UNICAST:
                sendInformation(host, messageType, info);
                System.out.println("[" + ourAddress + "] Sending Unicast " + type + " to : " + host);
                break;
            default :
                break;
        }
    }
    
    /**
     * Send a Broadcast info packet of a specified type to all neighbors.
     *
     * @param conn The connection used to send the Broadcast datagram. (must be initialized)
     * @param type The type of the packet to send.
     */
    public void broadcast(SPOTInfo info, byte type) throws IOException {
        Radiogram sbdg = (Radiogram)sendbc.newDatagram(sendbc.getMaximumLength());
        RadioUtilities.fillInfoRadiogram(sbdg, type, info);
        RadioUtilities.flashInfoLed();
        sendbc.send(sbdg);
    }
    
    /**
     * Send a Unicast info packet of a specified type to a specific host.
     *
     * @param type The type of the packet to send.
     * @param host The IEEE address of the device we want to send a Radiogram.
     */
    public void sendInformation(String host, byte type, SPOTInfo info) 
            throws NoRouteException, IOException {
        RadiogramConnection conn = null;
        try{
            conn = (RadiogramConnection) 
                    Connector.open("radiogram://" + host + ":" + CONNECTED_PORT);
            Radiogram sudg = (Radiogram)conn.newDatagram(conn.getMaximumLength());
            RadioUtilities.fillInfoRadiogram(sudg, type, info);
            RadioUtilities.flashInfoLed();
            conn.send(sudg);
        } catch(NoRouteException e) {
            e.printStackTrace();
            throw new NoRouteException("No Route found to join host with address : " + host);
        } catch(IOException e2) {
            e2.printStackTrace();
            throw new IOException("IOException sending UNICAST INFO packet to host : " + host);
        } finally {
            try{
                conn.close();
            } catch(Exception e){
                // Ignore
            }
        }
    }
    
    public void sendTemperature(String host, double temperature, int coefficient)
            throws NoRouteException, IOException {
        RadiogramConnection conn = null;
        try{
            /* Prints information about temperature data */
            System.out.println(
                    "[DATA] Sending data to father : "
                    + " [Value = " + temperature + "]" 
                    + " [Coefficient = " + coefficient + "]"
            );
            conn = (RadiogramConnection) 
                    Connector.open("radiogram://" + host + ":" + CONNECTED_PORT);
            Radiogram sudg = (Radiogram)conn.newDatagram(conn.getMaximumLength());
            RadioUtilities.fillTemperatureRadiogram(sudg, temperature, coefficient);
            RadioUtilities.flashDataLed();
            conn.send(sudg);
        } catch(NoRouteException e) {
            e.printStackTrace();
            throw new NoRouteException("No Route found to join host with address : " + host);
        } catch(IOException e2) {
            e2.printStackTrace();
            throw new IOException("IOException sending UNICAST Temperature packet to host : " + host);
        } finally {
            try{
                conn.close();
            } catch(Exception e){
                // Ignore
            }
        }
    }
}
