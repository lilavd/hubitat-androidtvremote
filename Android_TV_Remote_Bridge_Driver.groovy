/**
 *  Android TV Remote Device Driver (Bridge Version)
 *  
 *  Copyright 2025
 *  
 *  Licensed under the Apache License, Version 2.0
 *  
 *  Description:
 *  Hubitat driver for Android TV devices using bridge server
 *  Works with androidtv-bridge.js Node.js server
 *  Implements Android TV Remote Protocol v2 via HTTP bridge
 *
 *  Version: 1.0.0-bridge
 *  
 *  Requirements:
 *  - Node.js bridge server running (androidtv-bridge.js)
 *  - Android TV with Remote Service enabled
 */

import groovy.transform.Field
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

metadata {
    definition(
        name: "Android TV Remote (Bridge)",
        namespace: "community",
        author: "Community Driver",
        importUrl: "https://raw.githubusercontent.com/yourrepo/android-tv-remote-hubitat/main/drivers/Android_TV_Remote_Bridge_Driver.groovy"
    ) {
        capability "Switch"
        capability "SwitchLevel"
        capability "Refresh"
        capability "Initialize"
        capability "MediaController"
        
        // Basic Navigation
        command "pressKey", [[name:"key*", type:"ENUM", constraints:[
            "DPAD_UP", "DPAD_DOWN", "DPAD_LEFT", "DPAD_RIGHT", "DPAD_CENTER",
            "ENTER", "BACK", "HOME", "MENU", "SEARCH"
        ]]]
        command "dpadUp"
        command "dpadDown"
        command "dpadLeft"
        command "dpadRight"
        command "dpadCenter"
        command "back"
        command "home"
        command "menu"
        
        // Media Control
        command "play"
        command "pause"
        command "playPause"
        command "stop"
        command "fastForward"
        command "rewind"
        command "nextTrack"
        command "previousTrack"
        
        // Volume Control
        command "volumeUp"
        command "volumeDown"
        command "mute"
        command "unmute"
        command "setVolume", [[name:"volume*", type:"NUMBER"]]
        
        // Power Control
        command "powerToggle"
        command "sleep"
        command "wakeUp"
        
        // App Control
        command "launchApp", [[name:"appUrl*", type:"STRING"]]
        command "sendText", [[name:"text*", type:"STRING"]]
        
        // Pairing
        command "startPairing"
        command "completePairing", [[name:"code*", type:"STRING"]]
        command "unpair"
        
        // Connection
        command "connect"
        command "disconnect"
        command "checkBridge"
        
        attribute "power", "string"
        attribute "volume", "number"
        attribute "muted", "string"
        attribute "connectionStatus", "string"
        attribute "paired", "string"
        attribute "bridgeStatus", "string"
    }
    
    preferences {
        input name: "deviceIP", type: "text", title: "Android TV IP Address", required: true
        input name: "deviceMAC", type: "text", title: "Android TV MAC Address (for Wake-on-LAN)", description: "Format: AA:BB:CC:DD:EE:FF", required: false
        input name: "bridgeIP", type: "text", title: "Bridge Server IP", required: true
        input name: "bridgePort", type: "number", title: "Bridge Server Port", defaultValue: 3000
        input name: "deviceId", type: "text", title: "Device ID", description: "Unique ID for this TV", required: true
        input name: "deviceName", type: "text", title: "Device Name (for pairing)", defaultValue: "Hubitat"
        input name: "autoConnect", type: "bool", title: "Auto-connect on Initialize", defaultValue: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
        input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
    }
}

// Key code mapping
@Field static final Map KEY_CODES = [
    "DPAD_UP": 19,
    "DPAD_DOWN": 20,
    "DPAD_LEFT": 21,
    "DPAD_RIGHT": 22,
    "DPAD_CENTER": 23,
    "ENTER": 66,
    "BACK": 4,
    "HOME": 3,
    "MENU": 82,
    "SEARCH": 84,
    "MEDIA_PLAY": 126,
    "MEDIA_PAUSE": 127,
    "MEDIA_PLAY_PAUSE": 85,
    "MEDIA_STOP": 86,
    "MEDIA_NEXT": 87,
    "MEDIA_PREVIOUS": 88,
    "MEDIA_REWIND": 89,
    "MEDIA_FAST_FORWARD": 90,
    "VOLUME_UP": 24,
    "VOLUME_DOWN": 25,
    "VOLUME_MUTE": 164,
    "POWER": 26,
    "SLEEP": 223,
    "WAKEUP": 224
]

// Lifecycle
def installed() {
    log.info "Android TV Remote (Bridge) driver installed"
    sendEvent(name: "paired", value: "unknown")
    sendEvent(name: "connectionStatus", value: "disconnected")
    sendEvent(name: "bridgeStatus", value: "unknown")
    initialize()
}

def updated() {
    log.info "Android TV Remote driver updated"
    unschedule()
    if (logEnable) runIn(1800, logsOff)
    initialize()
}

def initialize() {
    log.info "Initializing Android TV Remote"
    
    if (!deviceIP || !bridgeIP || !deviceId) {
        log.error "Missing required configuration"
        return
    }
    
    // Check bridge connectivity
    runIn(1, checkBridge)
    
    // Check pairing status
    if (state.certificate && state.privateKey) {
        sendEvent(name: "paired", value: "true")
        
        // Check if already connected on bridge
        runIn(3, getStatus)
        
        // Schedule periodic status checks (every 60 seconds)
        schedule("0 * * * * ?", getStatus)
    } else {
        sendEvent(name: "paired", value: "false")
        log.warn "Device not paired - use startPairing command"
    }
}

def refresh() {
    if (logEnable) log.debug "Refreshing status"
    checkBridge()
    getStatus()
    
    if (txtEnable) {
        def bridgeStatus = device.currentValue("bridgeStatus")
        def connectionStatus = device.currentValue("connectionStatus")
        def paired = device.currentValue("paired")
        
        log.info "Status - Bridge: ${bridgeStatus}, Connection: ${connectionStatus}, Paired: ${paired}"
    }
}

// Bridge Communication
private String getBridgeUrl() {
    return "http://${bridgeIP}:${bridgePort}"
}

def checkBridge() {
    if (logEnable) log.debug "Checking bridge server"
    
    def params = [
        uri: getBridgeUrl(),
        path: "/health",
        timeout: 5
    ]
    
    try {
        httpGet(params) { resp ->
            if (resp.status == 200) {
                sendEvent(name: "bridgeStatus", value: "online")
                if (txtEnable) log.info "Bridge server is online"
                return true
            }
        }
    } catch (Exception e) {
        log.error "Bridge server unreachable: ${e.message}"
        sendEvent(name: "bridgeStatus", value: "offline")
        return false
    }
}

private Map callBridge(String endpoint, Map body = null, String method = "POST") {
    // Use longer timeout for pairing operations (30 seconds)
    def timeout = endpoint.contains("/pair") ? 30 : 10
    
    def params = [
        uri: getBridgeUrl(),
        path: endpoint,
        contentType: "application/json",
        timeout: timeout
    ]
    
    if (body) {
        params.body = body
    }
    
    try {
        if (method == "POST") {
            httpPost(params) { resp ->
                return handleBridgeResponse(resp)
            }
        } else {
            httpGet(params) { resp ->
                return handleBridgeResponse(resp)
            }
        }
    } catch (Exception e) {
        log.error "Bridge call failed (${endpoint}): ${e.message}"
        return [success: false, error: e.message]
    }
}

private Map handleBridgeResponse(resp) {
    if (resp.status == 200 && resp.data) {
        if (logEnable) log.debug "Bridge response: ${resp.data}"
        return resp.data
    } else {
        log.error "Bridge returned error: ${resp.status}"
        return [success: false, error: "HTTP ${resp.status}"]
    }
}

// Connection Management
def connect() {
    if (!state.certificate || !state.privateKey) {
        log.error "Cannot connect - device not paired"
        return
    }
    
    if (logEnable) log.debug "Connecting to ${deviceIP}"
    
    sendEvent(name: "connectionStatus", value: "connecting")
    
    def result = callBridge("/connect", [
        deviceId: deviceId,
        host: deviceIP,
        certificate: state.certificate,
        privateKey: state.privateKey
    ])
    
    if (result?.success) {
        sendEvent(name: "connectionStatus", value: "connected")
        if (txtEnable) log.info "Connected to Android TV"
    } else {
        sendEvent(name: "connectionStatus", value: "error")
        log.error "Connection failed: ${result?.error}"
    }
}

def disconnect() {
    if (logEnable) log.debug "Disconnecting"
    
    def result = callBridge("/disconnect", [
        deviceId: deviceId
    ])
    
    sendEvent(name: "connectionStatus", value: "disconnected")
    if (txtEnable) log.info "Disconnected"
}

def getStatus() {
    if (logEnable) log.debug "Getting device status from bridge"
    
    def result = callBridge("/status/${deviceId}", null, "GET")
    
    if (result?.success) {
        if (result.connected) {
            sendEvent(name: "connectionStatus", value: "connected")
            if (logEnable) log.debug "Status: Connected"
        } else {
            sendEvent(name: "connectionStatus", value: "disconnected")
            if (logEnable) log.debug "Status: Disconnected"
        }
    } else {
        // If we can't get status but bridge is online, assume disconnected
        if (device.currentValue("bridgeStatus") == "online") {
            sendEvent(name: "connectionStatus", value: "disconnected")
        }
    }
}

// Pairing
def startPairing() {
    if (logEnable) log.debug "Starting pairing"
    
    log.warn "=============================================="
    log.warn "PAIRING STARTED"
    log.warn "Look for a 6-digit code on your TV screen"
    log.warn "Then use: completePairing(\"123456\")"
    log.warn "=============================================="
    
    def result = callBridge("/pair/start", [
        deviceId: deviceId,
        host: deviceIP,
        deviceName: deviceName
    ])
    
    if (result?.success) {
        sendEvent(name: "paired", value: "pairing")
        log.warn "Pairing code should be displayed on TV"
        if (result.code) {
            log.warn "Bridge received code: ${result.code}"
        }
    } else {
        log.error "Pairing start failed: ${result?.error}"
        sendEvent(name: "paired", value: "false")
    }
}

def completePairing(String code) {
    if (!code || code.length() != 6) {
        log.error "Invalid code - must be 6 characters"
        return
    }
    
    if (logEnable) log.debug "Completing pairing with code: ${code}"
    
    def result = callBridge("/pair/complete", [
        deviceId: deviceId,
        code: code
    ])
    
    if (result?.success) {
        // Store credentials
        state.certificate = result.certificate
        state.privateKey = result.privateKey
        
        log.debug "Certificate stored: ${state.certificate ? 'Yes' : 'No'}"
        log.debug "Private key stored: ${state.privateKey ? 'Yes' : 'No'}"
        
        // Update paired status
        sendEvent(name: "paired", value: "true")
        
        // After successful pairing, device is already connected by the bridge
        // Update connection status to reflect this
        sendEvent(name: "connectionStatus", value: "connected")
        
        log.warn "=============================================="
        log.warn "PAIRING SUCCESSFUL!"
        log.warn "Credentials have been saved"
        log.warn "Device is now connected and ready to use"
        log.warn "Paired: true"
        log.warn "Connection: connected"
        log.warn "=============================================="
        
        // Get initial status
        runIn(2, getStatus)
        
        // Reinitialize to set up status checking schedule
        runIn(3, initialize)
    } else {
        log.error "Pairing failed: ${result?.error}"
        sendEvent(name: "paired", value: "false")
        sendEvent(name: "connectionStatus", value: "disconnected")
    }
}

def unpair() {
    if (logEnable) log.debug "Unpairing device"
    
    // Call bridge to unpair
    def result = callBridge("/unpair", [
        deviceId: deviceId
    ])
    
    // Clear local state
    state.remove("certificate")
    state.remove("privateKey")
    
    sendEvent(name: "paired", value: "false")
    sendEvent(name: "connectionStatus", value: "disconnected")
    
    log.warn "=============================================="
    log.warn "DEVICE UNPAIRED"
    log.warn "Cleared from Hubitat and bridge"
    log.warn "Also clear Android TV Remote Service data on TV:"
    log.warn "Settings > Apps > Android TV Remote Service > Clear storage"
    log.warn "=============================================="
    
    if (txtEnable) log.info "Device unpaired"
}

// Switch Capability
def on() {
    wakeUp()
}

def off() {
    powerToggle()
}

// Volume
def setLevel(level, duration=0) {
    setVolume(level as int)
}

def setVolume(int volume) {
    volume = Math.max(0, Math.min(100, volume))
    
    def currentVolume = device.currentValue("volume") ?: 50
    def steps = volume - currentVolume
    
    if (steps > 0) {
        steps.times { volumeUp() }
    } else if (steps < 0) {
        Math.abs(steps).times { volumeDown() }
    }
    
    sendEvent(name: "volume", value: volume)
    sendEvent(name: "level", value: volume)
}

// Navigation
def pressKey(String keyName) {
    if (!KEY_CODES.containsKey(keyName)) {
        log.error "Unknown key: ${keyName}"
        return
    }
    
    sendKey(KEY_CODES[keyName], keyName)
}

def dpadUp() { pressKey("DPAD_UP") }
def dpadDown() { pressKey("DPAD_DOWN") }
def dpadLeft() { pressKey("DPAD_LEFT") }
def dpadRight() { pressKey("DPAD_RIGHT") }
def dpadCenter() { pressKey("DPAD_CENTER") }
def back() { pressKey("BACK") }
def home() { pressKey("HOME") }
def menu() { pressKey("MENU") }

// Media
def play() { pressKey("MEDIA_PLAY") }
def pause() { pressKey("MEDIA_PAUSE") }
def playPause() { pressKey("MEDIA_PLAY_PAUSE") }
def stop() { pressKey("MEDIA_STOP") }
def fastForward() { pressKey("MEDIA_FAST_FORWARD") }
def rewind() { pressKey("MEDIA_REWIND") }
def nextTrack() { pressKey("MEDIA_NEXT") }
def previousTrack() { pressKey("MEDIA_PREVIOUS") }

// Volume
def volumeUp() { pressKey("VOLUME_UP") }
def volumeDown() { pressKey("VOLUME_DOWN") }
def mute() { 
    pressKey("VOLUME_MUTE")
    sendEvent(name: "muted", value: "muted")
}
def unmute() { 
    pressKey("VOLUME_MUTE")
    sendEvent(name: "muted", value: "unmuted")
}

// Power
def powerToggle() { 
    pressKey("POWER")
    def current = device.currentValue("switch")
    def newState = current == "on" ? "off" : "on"
    sendEvent(name: "switch", value: newState)
    sendEvent(name: "power", value: newState)
    
    if (logEnable) log.debug "Power toggled to: ${newState}"
}

def sleep() { 
    pressKey("SLEEP")
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "power", value: "off")
}

def wakeUp() {
    if (logEnable) log.debug "Attempting to wake TV"
    
    // Try Wake-on-LAN first if MAC address is configured
    if (deviceMAC) {
        if (logEnable) log.debug "Sending Wake-on-LAN magic packet to ${deviceMAC}"
        sendWOL(deviceMAC)
        pauseExecution(1000)
    }
    
    // Method 1: Send POWER key (works if TV is in light sleep)
    pressKey("POWER")
    pauseExecution(500)
    
    // Method 2: Send HOME key (often more reliable for Android TV)
    pressKey("HOME")
    
    // Update state optimistically
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "power", value: "on")
    
    if (txtEnable) log.info "Wake commands sent to TV"
}

// Send Wake-on-LAN magic packet
private void sendWOL(String mac) {
    try {
        def macBytes = mac.replaceAll("[:-]", "").decodeHex()
        
        if (macBytes.size() != 6) {
            log.error "Invalid MAC address format: ${mac}"
            return
        }
        
        // Build magic packet: 6 bytes of FF + 16 repetitions of MAC
        def packet = []
        
        // Fill first 6 bytes with 0xFF
        for (int i = 0; i < 6; i++) {
            packet << (byte) 0xFF
        }
        
        // Repeat MAC address 16 times
        for (int i = 0; i < 16; i++) {
            packet.addAll(macBytes)
        }
        
        // Convert to byte array
        byte[] packetBytes = packet as byte[]
        
        // Convert to hex string for HubAction
        def hexString = hubitat.helper.HexUtils.byteArrayToHexString(packetBytes)
        
        // Send broadcast packet
        def hubAction = new hubitat.device.HubAction(
            hexString,
            hubitat.device.Protocol.LAN,
            [type: hubitat.device.HubAction.Type.LAN_TYPE_UDPCLIENT,
             destinationAddress: "255.255.255.255:9",
             encoding: hubitat.device.HubAction.Encoding.HEX_STRING]
        )
        
        sendHubCommand(hubAction)
        
        if (logEnable) log.debug "WOL magic packet sent to ${mac}"
        
    } catch (Exception e) {
        log.error "Failed to send WOL packet: ${e.message}"
    }
}

// App Control
def launchApp(String appUrl) {
    if (!appUrl) {
        log.error "App URL required"
        return
    }
    
    if (logEnable) log.debug "Launching app: ${appUrl}"
    
    def result = callBridge("/app/launch", [
        deviceId: deviceId,
        appUrl: appUrl
    ])
    
    if (result?.success) {
        if (txtEnable) log.info "Launched app: ${appUrl}"
    } else {
        log.error "Failed to launch app: ${result?.error}"
    }
}

def sendText(String text) {
    if (!text) {
        log.error "Text required"
        return
    }
    
    if (logEnable) log.debug "Sending text: ${text}"
    
    def result = callBridge("/text", [
        deviceId: deviceId,
        text: text
    ])
    
    if (result?.success) {
        if (txtEnable) log.info "Sent text: ${text}"
    } else {
        log.error "Failed to send text: ${result?.error}"
    }
}

// Send key via bridge
private sendKey(int keyCode, String keyName) {
    if (logEnable) log.debug "Sending key: ${keyName} (${keyCode})"
    
    def result = callBridge("/key", [
        deviceId: deviceId,
        keyCode: keyCode,
        keyName: keyName
    ])
    
    if (!result?.success) {
        log.error "Failed to send key: ${result?.error}"
    }
}

// Utility
def logsOff() {
    log.warn "Debug logging disabled"
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}
