/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sunspotworld.heatsensorsalt;

/**
 *
 * @author Alexandre
 */
public class SPOTInfo implements PacketTypes {
        /** Date of the last info receive. */
        public long date;
        /** Type of the SPOT device. Can be a SPOT or a basestation */
        public byte nodetype;
        /** The IEEE address of the father if existing. */
        public String father;
        /** The number of son in the tree. O if none. */
        public int sonNumber;
        /** The number of hops to the basestation . */
        public int hops;
        /** The threshold to use. */
        public double threshold;
        
        /**
         * Constructor.
         */
        public SPOTInfo(){
            nodetype = SPOT;
            father = null;
            sonNumber = 0;
            hops = 0;
            threshold = 0.2;
        };
        
       /**
        * Updates the timestamp of a SPOTInfo instance.
        */
        public void update(){
            this.date = System.currentTimeMillis();
        }
}
