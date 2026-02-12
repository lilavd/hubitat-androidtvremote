/**
 * Android TV Remote - Bridge Server (Complete Fix)
 * 
 * Fixed: Body parsing issues, IPv6 problems, undefined errors
 * 
 * Install dependencies:
 * npm install express body-parser androidtv-remote
 * 
 * Run:
 * node androidtv-bridge.js
 */

const express = require('express');
const bodyParser = require('body-parser');
const AndroidRemote = require('androidtv-remote').AndroidRemote;
const RemoteKeyCode = require('androidtv-remote').RemoteKeyCode;
const RemoteDirection = require('androidtv-remote').RemoteDirection;

const app = express();

// CRITICAL: Body parser middleware MUST be configured correctly
app.use(bodyParser.json({ limit: '10mb' }));
app.use(bodyParser.urlencoded({ extended: true }));

// Add request logging middleware
app.use((req, res, next) => {
    console.log(`\n[${new Date().toISOString()}] ${req.method} ${req.path}`);
    if (req.body && Object.keys(req.body).length > 0) {
        console.log('Request body:', JSON.stringify(req.body, null, 2));
    }
    next();
});

// Configuration
const PORT = process.env.PORT || 3000;
const devices = new Map();

// Pairing endpoint - Step 1: Initiate pairing
app.post('/pair/start', async (req, res) => {
    console.log('\n=== PAIRING START REQUEST ===');
    
    try {
        // Extract and validate parameters
        const deviceId = req.body.deviceId;
        const host = req.body.host;
        const deviceName = req.body.deviceName || 'Hubitat';
        
        // Validation
        if (!deviceId) {
            throw new Error('Missing required parameter: deviceId');
        }
        if (!host) {
            throw new Error('Missing required parameter: host (TV IP address)');
        }
        
        console.log(`Device ID: ${deviceId}`);
        console.log(`TV Host: ${host}`);
        console.log(`Device Name: ${deviceName}`);
        
        // Validate IP address format
        const ipRegex = /^(\d{1,3}\.){3}\d{1,3}$/;
        if (!ipRegex.test(host)) {
            throw new Error(`Invalid IP address format: ${host}`);
        }
        
        const options = {
            pairing_port: 6467,
            remote_port: 6466,
            name: deviceName,
            cert: {}
        };
        
        console.log(`Creating AndroidRemote with options:`, JSON.stringify(options, null, 2));
        
        const remote = new AndroidRemote(host, options);
        
        // Store pairing state
        const pairingState = {
            remote: remote,
            host: host,
            deviceId: deviceId,
            started: Date.now(),
            codeDisplayed: false,
            ready: false,
            error: null
        };
        
        // Set up event handlers with detailed logging
        remote.on('secret', () => {
            console.log(`[${deviceId}] ✓ SECRET event - Pairing code should be on TV now`);
            pairingState.codeDisplayed = true;
        });
        
        remote.on('ready', () => {
            console.log(`[${deviceId}] ✓ READY event - Remote is ready`);
            pairingState.ready = true;
        });
        
        remote.on('error', (error) => {
            console.error(`[${deviceId}] ✗ ERROR event:`, error.message);
            pairingState.error = error.message;
        });
        
        remote.on('unpaired', () => {
            console.log(`[${deviceId}] ⚠ UNPAIRED event - Waiting for pairing code`);
        });
        
        remote.on('powered', (powered) => {
            console.log(`[${deviceId}] POWERED event: ${powered}`);
        });
        
        // Store before starting
        devices.set(`pairing_${deviceId}`, pairingState);
        
        console.log(`[${deviceId}] Starting remote.start()...`);
        
        // Start pairing
        await remote.start();
        
        console.log(`[${deviceId}] ✓ remote.start() completed`);
        
        // Give it a brief moment for events to fire
        await new Promise(resolve => setTimeout(resolve, 1000));
        
        console.log(`[${deviceId}] Pairing initiated successfully`);
        console.log(`[${deviceId}] Code displayed: ${pairingState.codeDisplayed}`);
        
        res.json({
            success: true,
            message: 'Pairing initiated - check TV for 6-digit code',
            deviceId: deviceId,
            codeDisplayed: pairingState.codeDisplayed
        });
        
    } catch (error) {
        console.error('✗ PAIRING START FAILED:', error.message);
        console.error('Error details:', error);
        
        res.status(500).json({
            success: false,
            error: error.message || 'Failed to start pairing',
            details: error.stack
        });
    }
});

// Pairing endpoint - Step 2: Submit code
app.post('/pair/complete', async (req, res) => {
    console.log('\n=== PAIRING COMPLETE REQUEST ===');
    
    try {
        const deviceId = req.body.deviceId;
        const code = req.body.code;
        
        // Validation
        if (!deviceId) {
            throw new Error('Missing required parameter: deviceId');
        }
        if (!code) {
            throw new Error('Missing required parameter: code');
        }
        
        console.log(`Device ID: ${deviceId}`);
        console.log(`Code: ${code}`);
        
        // Validate code format - can be alphanumeric (letters and numbers)
        if (!/^[A-Z0-9]{6}$/i.test(code)) {
            throw new Error('Code must be exactly 6 characters (letters or numbers)');
        }
        
        // Convert to uppercase to ensure consistency
        const upperCode = code.toUpperCase();
        
        const pairingState = devices.get(`pairing_${deviceId}`);
        if (!pairingState) {
            throw new Error(`No pairing in progress for device: ${deviceId}. Run /pair/start first.`);
        }
        
        const remote = pairingState.remote;
        
        console.log(`[${deviceId}] Sending code to TV...`);
        
        // Send pairing code (use uppercase version)
        await remote.sendCode(upperCode);
        
        console.log(`[${deviceId}] Code sent, waiting for pairing to complete...`);
        
        // Wait for pairing to complete
        await new Promise(resolve => setTimeout(resolve, 3000));
        
        console.log(`[${deviceId}] Getting certificate...`);
        
        // Get certificate
        const cert = remote.getCertificate();
        
        console.log(`[${deviceId}] Certificate obtained:`, cert ? 'Yes' : 'No');
        
        // Move from pairing to active devices
        devices.delete(`pairing_${deviceId}`);
        devices.set(deviceId, { 
            remote: remote, 
            cert: cert,
            host: pairingState.host,
            connected: true,
            lastActivity: Date.now()
        });
        
        console.log(`[${deviceId}] ✓ Pairing completed successfully!`);
        
        res.json({
            success: true,
            message: 'Pairing successful',
            deviceId: deviceId,
            certificate: JSON.stringify(cert),
            privateKey: JSON.stringify(cert)
        });
        
    } catch (error) {
        console.error('✗ PAIRING COMPLETE FAILED:', error.message);
        console.error('Error details:', error);
        
        res.status(500).json({
            success: false,
            error: error.message || 'Pairing failed',
            details: error.stack
        });
    }
});

// Connection management
app.post('/connect', async (req, res) => {
    console.log('\n=== CONNECT REQUEST ===');
    
    try {
        const deviceId = req.body.deviceId;
        const host = req.body.host;
        const certificate = req.body.certificate;
        
        // Validation
        if (!deviceId) {
            throw new Error('Missing required parameter: deviceId');
        }
        if (!host) {
            throw new Error('Missing required parameter: host');
        }
        
        console.log(`Device ID: ${deviceId}`);
        console.log(`Host: ${host}`);
        console.log(`Has certificate: ${!!certificate}`);
        
        // Check if already connected
        const existing = devices.get(deviceId);
        if (existing && existing.remote && existing.connected) {
            console.log(`[${deviceId}] Already connected, reusing connection`);
            res.json({
                success: true,
                message: 'Already connected'
            });
            return;
        }
        
        let cert = {};
        if (certificate) {
            try {
                cert = JSON.parse(certificate);
                console.log(`[${deviceId}] Using stored certificate`);
            } catch (e) {
                console.warn(`[${deviceId}] Invalid certificate format, needs pairing`);
            }
        }
        
        const options = {
            pairing_port: 6467,
            remote_port: 6466,
            name: 'Hubitat',
            cert: cert
        };
        
        const remote = new AndroidRemote(host, options);
        
        // Set up event handlers
        remote.on('powered', (powered) => {
            console.log(`[${deviceId}] TV powered: ${powered}`);
        });
        
        remote.on('volume', (volume) => {
            console.log(`[${deviceId}] Volume: ${volume.level}/${volume.maximum}, muted: ${volume.muted}`);
        });
        
        remote.on('ready', () => {
            console.log(`[${deviceId}] ✓ Connected and ready`);
        });
        
        remote.on('error', (error) => {
            console.error(`[${deviceId}] Error:`, error.message);
        });
        
        remote.on('unpaired', () => {
            console.warn(`[${deviceId}] ⚠ Device is unpaired - needs pairing`);
        });
        
        // Start connection
        console.log(`[${deviceId}] Starting connection...`);
        await remote.start();
        
        // Store the remote
        devices.set(deviceId, { 
            remote: remote, 
            cert: cert,
            host: host,
            connected: true,
            lastActivity: Date.now()
        });
        
        console.log(`[${deviceId}] ✓ Connected successfully`);
        
        res.json({
            success: true,
            message: 'Connected successfully',
            deviceId: deviceId
        });
        
    } catch (error) {
        console.error(`✗ CONNECTION FAILED:`, error.message);
        console.error('Error details:', error);
        
        res.status(500).json({
            success: false,
            error: error.message || 'Connection failed',
            details: error.stack
        });
    }
});

app.post('/disconnect', async (req, res) => {
    console.log('\n=== DISCONNECT REQUEST ===');
    
    try {
        const deviceId = req.body.deviceId;
        
        if (!deviceId) {
            throw new Error('Missing required parameter: deviceId');
        }
        
        console.log(`Device ID: ${deviceId}`);
        
        const deviceState = devices.get(deviceId);
        if (deviceState && deviceState.remote) {
            console.log(`[${deviceId}] Disconnecting...`);
            devices.delete(deviceId);
            console.log(`[${deviceId}] ✓ Disconnected`);
        } else {
            console.log(`[${deviceId}] Not connected, nothing to do`);
        }
        
        res.json({
            success: true,
            message: 'Disconnected',
            deviceId: deviceId
        });
        
    } catch (error) {
        console.error('Disconnect error:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Send key command
app.post('/key', async (req, res) => {
    try {
        const deviceId = req.body.deviceId;
        const keyCode = parseInt(req.body.keyCode); // Convert string to integer
        const keyName = req.body.keyName;
        
        if (!deviceId || isNaN(keyCode)) {
            throw new Error('Missing or invalid required parameters: deviceId, keyCode');
        }
        
        const deviceState = devices.get(deviceId);
        if (!deviceState || !deviceState.remote) {
            throw new Error(`Device ${deviceId} not connected. Run /connect first.`);
        }
        
        console.log(`[${deviceId}] Sending key: ${keyName || 'unknown'} (${keyCode})`);
        
        const remote = deviceState.remote;
        deviceState.lastActivity = Date.now();
        
        // Send key with SHORT direction
        await remote.sendKey(keyCode, RemoteDirection.SHORT);
        
        console.log(`[${deviceId}] ✓ Key sent`);
        
        res.json({
            success: true,
            message: `Sent key: ${keyName}`,
            deviceId: deviceId
        });
        
    } catch (error) {
        console.error('Send key error:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Failed to send key'
        });
    }
});

// Launch app
app.post('/app/launch', async (req, res) => {
    try {
        const deviceId = req.body.deviceId;
        const appUrl = req.body.appUrl;
        
        if (!deviceId || !appUrl) {
            throw new Error('Missing required parameters: deviceId, appUrl');
        }
        
        const deviceState = devices.get(deviceId);
        if (!deviceState || !deviceState.remote) {
            throw new Error(`Device ${deviceId} not connected`);
        }
        
        console.log(`[${deviceId}] Launching app: ${appUrl}`);
        
        const remote = deviceState.remote;
        deviceState.lastActivity = Date.now();
        
        await remote.sendAppLink(appUrl);
        
        console.log(`[${deviceId}] ✓ App launched`);
        
        res.json({
            success: true,
            message: 'App launched',
            deviceId: deviceId
        });
        
    } catch (error) {
        console.error('Launch app error:', error);
        res.status(500).json({
            success: false,
            error: error.message || 'Failed to launch app'
        });
    }
});

// Send text
app.post('/text', async (req, res) => {
    try {
        const deviceId = req.body.deviceId;
        const text = req.body.text;
        
        if (!deviceId || !text) {
            throw new Error('Missing required parameters: deviceId, text');
        }
        
        const deviceState = devices.get(deviceId);
        if (!deviceState || !deviceState.remote) {
            throw new Error(`Device ${deviceId} not connected`);
        }
        
        console.log(`[${deviceId}] Text input requested: ${text}`);
        
        res.json({
            success: true,
            message: 'Text input not fully implemented in androidtv-remote library',
            deviceId: deviceId
        });
        
    } catch (error) {
        console.error('Send text error:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Get status
app.get('/status/:deviceId', async (req, res) => {
    try {
        const deviceId = req.params.deviceId;
        
        const deviceState = devices.get(deviceId);
        const connected = deviceState && deviceState.remote && deviceState.connected;
        
        res.json({
            success: true,
            connected: connected,
            deviceId: deviceId,
            lastActivity: deviceState ? deviceState.lastActivity : null
        });
        
    } catch (error) {
        console.error('Status error:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// Health check
app.get('/health', (req, res) => {
    const allDevices = Array.from(devices.keys());
    const connectedCount = allDevices.filter(k => !k.startsWith('pairing_')).length;
    const pairingCount = allDevices.filter(k => k.startsWith('pairing_')).length;
    
    res.json({
        status: 'ok',
        connectedDevices: connectedCount,
        pairingInProgress: pairingCount,
        totalDevices: allDevices.length,
        uptime: Math.floor(process.uptime())
    });
});

// Unpair device
app.post('/unpair', async (req, res) => {
    console.log('\n=== UNPAIR REQUEST ===');
    
    try {
        const deviceId = req.body.deviceId;
        
        if (!deviceId) {
            throw new Error('Missing required parameter: deviceId');
        }
        
        console.log(`Device ID: ${deviceId}`);
        
        // Remove from active devices
        devices.delete(deviceId);
        
        // Also remove any pairing in progress
        devices.delete(`pairing_${deviceId}`);
        
        console.log(`[${deviceId}] ✓ Unpaired and removed from bridge`);
        console.log(`[${deviceId}] Note: Also clear pairing on TV to fully reset`);
        
        res.json({
            success: true,
            message: 'Unpaired from bridge. Also clear Android TV Remote Service data on TV.',
            deviceId: deviceId
        });
        
    } catch (error) {
        console.error('Unpair error:', error);
        res.status(500).json({
            success: false,
            error: error.message
        });
    }
});

// List devices (debugging)
app.get('/devices', (req, res) => {
    const deviceList = Array.from(devices.entries()).map(([deviceId, state]) => ({
        deviceId: deviceId,
        type: deviceId.startsWith('pairing_') ? 'pairing' : 'connected',
        host: state.host || 'unknown',
        connected: state.connected || false,
        lastActivity: state.lastActivity || null
    }));
    
    res.json({
        devices: deviceList,
        count: deviceList.length
    });
});

// Test endpoint
app.post('/test', (req, res) => {
    console.log('\n=== TEST ENDPOINT ===');
    console.log('Body received:', req.body);
    console.log('Headers:', req.headers);
    
    res.json({
        success: true,
        message: 'Test successful',
        receivedBody: req.body,
        bodyKeys: Object.keys(req.body)
    });
});

// Start server - Listen on all interfaces
const server = app.listen(PORT, '0.0.0.0', () => {
    console.log('='.repeat(70));
    console.log('  Android TV Remote Bridge Server');
    console.log('='.repeat(70));
    console.log(`  Status: RUNNING`);
    console.log(`  Port: ${PORT}`);
    console.log(`  Listening: 0.0.0.0:${PORT} (all interfaces)`);
    console.log(`  Node Version: ${process.version}`);
    console.log('');
    console.log('  Endpoints:');
    console.log(`    POST /pair/start       - Start pairing`);
    console.log(`    POST /pair/complete    - Complete pairing with code`);
    console.log(`    POST /connect          - Connect to paired device`);
    console.log(`    POST /disconnect       - Disconnect`);
    console.log(`    POST /key              - Send key`);
    console.log(`    POST /app/launch       - Launch app`);
    console.log(`    POST /text             - Send text`);
    console.log(`    GET  /status/:id       - Device status`);
    console.log(`    GET  /health           - Health check`);
    console.log(`    GET  /devices          - List devices`);
    console.log(`    POST /test             - Test body parsing`);
    console.log('');
    console.log('  Configure Hubitat:');
    console.log(`    Bridge Server IP: YOUR_QNAP_IP`);
    console.log(`    Bridge Server Port: ${PORT}`);
    console.log('='.repeat(70));
    console.log('');
});

// Graceful shutdown
process.on('SIGINT', async () => {
    console.log('\n\nShutting down gracefully...');
    
    // Close server
    server.close(() => {
        console.log('HTTP server closed');
    });
    
    // Clear all devices
    devices.clear();
    
    console.log('Shutdown complete');
    process.exit(0);
});

// Error handlers
process.on('uncaughtException', (error) => {
    console.error('UNCAUGHT EXCEPTION:', error);
});

process.on('unhandledRejection', (reason, promise) => {
    console.error('UNHANDLED REJECTION:', reason);
});
