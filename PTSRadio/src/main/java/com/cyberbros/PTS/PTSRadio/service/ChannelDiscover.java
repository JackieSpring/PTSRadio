package com.cyberbros.PTS.PTSRadio.service;

import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;

public class ChannelDiscover extends PTSService {

    private int channel;
    private boolean flagFirstChannelFound = false;
    private Runnable callback;


    @Override
    public boolean trap(PTSPacket pk) {
        return false;
    }


    private void discoverChannel(){
        //
    }


}
