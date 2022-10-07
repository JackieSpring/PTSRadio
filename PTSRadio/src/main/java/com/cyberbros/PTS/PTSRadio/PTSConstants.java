package com.cyberbros.PTS.PTSRadio;
//vendor-id="6790" product-id="29987" class="255" subclass="0" protocol="0"
public class PTSConstants {
    public static final int
    VERSION_MAJOR   = 0,
    VERSION_MINOR   = 2,

    ID_LENGTH = 5,

    USB_BAUD_RATE   = 9600,
    USB_DATA_BITS   = 8,
    USB_VENDOR_ID   = 6790,
    USB_PRODUCT_ID  = 29987,
    USB_CLASS       = 255,
    USB_SUBCLASS    = 0,
    USB_PROTOCOL    = 0,

    PACKET_INVALID_BYTES = 2,
    PACKET_MAX_LEN = 26,

    PING_MIN_TIMEOUT = 30,
    PING_MAX_TIMEOUT = 60,

    SERVICE_TIMEOUT = 45,

    CALL_CHANNEL_FIRST_CHANNEL = 0,
    CALL_CHANNEL_LAST_CHANNEL = 50,
    CALL_CHANNEL_INCREMENT = 2,
    CALL_DISTURB_CONSTANT = 30_000;


    public static final String
    BANNER_TEXT_MODE = "text",
    BANNER_AUDIO_MODE = "audio",
    BANNER_RESET = "reset",

    CMD_BOARD_RESTART   = "R",
    CMD_BOARD_MODE_TEXT = "T",
    CMD_BOARD_MODE_AUDIO= "A",
    CMD_BOARD_GET_ID    = "I",

    CMD_SERVICE_REQUEST_CHAT    = "C",
    CMD_SERVICE_REQUEST_GROUP   = "G",
    CMD_SERVICE_REQUEST_CALL    = "A",

    CMD_SERVICE_ACCEPT  = "Y",
    CMD_SERVICE_REFUSE  = "N",
    CMD_SERVICE_INVITE  = "+",
    CMD_SERVICE_KICK    = "-",
    CMD_SERVICE_QUIT    = "E",
    CMD_SERVICE_MESSAGE = "M",

    CMD_CHANNEL_DISCOVER = "S",

    CMD_CALL_TALK           = "T",
    CMD_CALL_LISTEN         = "L",
    CMD_CALL_START_PREFIX   = "A",
    CMD_CALL_START_HOST_SUFFIX = "B",
    CMD_CALL_START_CLIENT_SUFFIX = "A";
}
