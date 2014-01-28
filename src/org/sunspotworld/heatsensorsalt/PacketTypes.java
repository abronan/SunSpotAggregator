package org.sunspotworld.heatsensorsalt;

/**
 * Packet types for HeatSensors
 *
 * @author Alexandre
 * Date: April 5, 2012
 * Revised: May 10, 2012
 */
public interface PacketTypes {

    /** Port to use to Broadcast packet. */
    public static final String BROADCAST_PORT = "67";
    /** Port to use for sending commands and replies between the SPOT and the host application. */
    public static final String CONNECTED_PORT = "43";
    /** Port used for remote printing. */
    public static final String REMOTE_PRINTING_PORT = "90";

    // Command & reply codes for data packets
    
    /** Client and Host command to discover neighboors and construct a Tree Network. */
    public static final byte HELLO                      = 1;
    /** Client command to reply that the SPOT is non-attached and that we are now attached to the sender. */
    public static final byte REPLY                      = 2;
    /** Client command to reply that indicates that the SPOT is non-attached and in research of a new father. */
    public static final byte LOST                       = 3;
    /** Client command to reply that indicates that the SPOT is already attached to a father. */
    public static final byte TIED                       = 4;
    /** Client command to send temperature value and secondary info. */
    public static final byte TEMP                       = 5;
    /** Client command to ping another SPOT or host. */
    public static final byte PING                       = 6;
    /** Host command to inform SPOT of the threshold. */
    public static final byte THRESHOLD_VALUE            = 7;
    
    /** Host command to indicate it is restarting. */
    public static final byte SERVER_RESTART             = 30;    // sent to any clients (broadcast)
    /** Host command to indicate it is quitting. */
    public static final byte SERVER_QUITTING            = 31;    // (direct p2p)
    
    /** Possible send value for packet. */
    public static final byte BROADCAST                  = 50;
    public static final byte UNICAST                    = 51;
    
    /** Possible types of a SPOT. */
    public static final byte SPOT                       = 100;
    public static final byte BASESTATION                = 101;
}