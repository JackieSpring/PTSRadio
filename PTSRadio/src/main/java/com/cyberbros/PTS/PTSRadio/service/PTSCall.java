package com.cyberbros.PTS.PTSRadio.service;

import com.cyberbros.PTS.PTSRadio.internals.PTSPacket;
import com.cyberbros.PTS.PTSRadio.internals.PTSPacketTrap;
import com.cyberbros.PTS.PTSRadio.io.PTSSerial;

public class PTSCall extends PTSService {
// EVENTS
    private static final String
    CALL_ERROR = "call_error",
    CALL_ACCEPTED = "call_accepted",
    CALL_REFUSED = "call_refused",
    CALL_REQUEST_TIMEOUT = "call_request_timeout",
    CALL_NO_CHANNEL = "call_no_channel",
    CALL_VOICE_BUSY = "call_voice_busy",
    CALL_VOICE_FREE = "call_voice_free";

//Commands
    private static final String
    SERVICE_ACCEPT = "Y",
    SERVICE_REFUSE = "N",
    SERVICE_QUIT = "E",
    SERVICE_TALK = "T",
    SERVICE_LISTEN = "L",
    SERVICE_START_PREFIX = "A",
    SERVICE_START_HOST_SUFFIX = "B",
    SERVICE_START_CLIENT_SUFFIX = "A";

    private String callMember;
    private String callChannel;
    private boolean flagCallOpen = false;
    private boolean flagCallClosed = false;
    private boolean flagChannelFound = false;
    private boolean flagSempahore = false;


    public PTSCall(String target) {
        super();
        callMember = target;
    }

    @Override
    public boolean trap(PTSPacket pk) {
        return false;
    }

    @Override
    public void startService(PTSSerial io, String id) {
        super.startService(io, id);
    }
}
