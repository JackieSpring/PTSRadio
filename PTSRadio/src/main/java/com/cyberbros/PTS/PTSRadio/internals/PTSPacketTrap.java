package com.cyberbros.PTS.PTSRadio.internals;

import android.util.Log;

import androidx.annotation.NonNull;

public abstract class PTSPacketTrap {
    private PTSPacketTrap prev;
    private PTSPacketTrap next;


    public synchronized void destroy(){
        if ( prev != null )
            prev.next = next;

        if ( next != null )
            next.prev = prev;
    }

    public synchronized void destroyChain(){
        PTSPacketTrap n = next;
        destroy();
        if ( n != null )
            n.destroyChain();
    }

    public synchronized void addNext( PTSPacketTrap newtrap ){
        if ( newtrap != null ) {
            newtrap.prev = this;
            newtrap.next = next;
        }
        this.next = newtrap;
    }

    public synchronized void addPrev( @NonNull PTSPacketTrap newtrap ){
        newtrap.next = this;
        newtrap.prev = prev;
        prev = newtrap;
    }

    public synchronized void handle( PTSPacket pk ){
        if ( this.trap(pk) == false && next != null )
            next.handle(pk);
    }

    // Return true to stop packet propagation
    public abstract boolean trap( PTSPacket pk );





    // TODO: DEBUG
    public void printChain(){
        if ( next != null )
            next.printChain();
    }

    private String name;

    public void setName(String n){
        this.name = n;
    }
    public String getName(){
        return name;
    }
}
