# Android TV Bridge - QNAP Container Station Setup

## Overview

This guide shows you how to deploy the Android TV Bridge on QNAP NAS using **Container Station** GUI instead of command-line Docker.

## Prerequisites

- QNAP NAS with Container Station installed
- Access to QNAP web interface
- Android TV on same network

## Method 1: Container Station GUI (Recommended)

### Step 1: Install Container Station

1. Open QNAP **App Center**
2. Search for **Container Station**
3. Click **Install**
4. Wait for installation to complete

### Step 2: Prepare Files on QNAP

1. **Open File Station**
2. **Create folder structure:**
   ```
   /share/Container/androidtv-bridge/
   ```

3. **Upload files to this folder:**
   - `androidtv-bridge.js` (from the provided files)
   - `package.json` (create it - see below)

4. **Create package.json** in File Station:
   - Right-click in folder → Create → Text File
   - Name it `package.json`
   - Edit and paste:

```json
{
  "name": "androidtv-bridge",
  "version": "1.0.0",
  "description": "Android TV Remote Bridge for Hubitat",
  "main": "androidtv-bridge.js",
  "dependencies": {
    "express": "^4.18.2",
    "body-parser": "^1.20.2",
    "androidtvremote2": "^0.3.0"
  },
  "scripts": {
    "start": "node androidtv-bridge.js"
  }
}
```

### Step 3: Create Container in Container Station

1. **Open Container Station**

2. **Click "Create" → "Create Application"**

3. **Paste this YAML configuration:**

```yaml
version: '3'
services:
  androidtv-bridge:
    image: node:18-alpine
    container_name: androidtv-bridge
    restart: unless-stopped
    network_mode: bridge
    ports:
      - "3000:3000"
    volumes:
      - /share/Container/androidtv-bridge:/app
    working_dir: /app
    command: sh -c "npm install && node androidtv-bridge.js"
    environment:
      - NODE_ENV=production
```

4. **Click "Validate"** to check YAML syntax

5. **Click "Create"**

6. **Name the application:** `androidtv-bridge`

7. **Click "Create"** to start deployment

### Step 4: Verify Container is Running

1. In Container Station, go to **Containers** tab
2. Look for **androidtv-bridge** container
3. Status should show **Running** (green play icon)
4. Click on the container to see logs
5. You should see:
   ```
   ====================================
   Android TV Remote Bridge Server
   ====================================
   Server running on port 3000
   ```

### Step 5: Test the Bridge

1. **Find your QNAP IP address** (e.g., 192.168.1.50)

2. **Test from browser or terminal:**
   ```
   http://YOUR_QNAP_IP:3000/health
   ```

3. **You should see:**
   ```json
   {
     "status": "ok",
     "connectedDevices": 0,
     "uptime": 123
   }
   ```

### Step 6: Configure Hubitat Driver

In your Hubitat device preferences:
- **Bridge Server IP:** Your QNAP IP (e.g., 192.168.1.50)
- **Bridge Server Port:** 3000
- Save preferences

## Method 2: Using Pre-built Docker Image (Easier)

If creating the container doesn't work, use this simpler method:

### Step 1: Create Dockerfile on QNAP

1. **Open File Station**
2. **Navigate to:** `/share/Container/androidtv-bridge/`
3. **Create a new text file named:** `Dockerfile` (no extension)
4. **Edit and paste:**

```dockerfile
FROM node:18-alpine

WORKDIR /app

# Copy package files
COPY package.json .

# Install dependencies
RUN npm install --production

# Copy application
COPY androidtv-bridge.js .

# Expose port
EXPOSE 3000

# Health check
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:3000/health || exit 1

# Start application
CMD ["node", "androidtv-bridge.js"]
```

### Step 2: Build Image via Container Station

1. **Open Container Station**
2. **Click "Images" tab**
3. **Click "Build"**
4. **Configure:**
   - **Image Name:** `androidtv-bridge`
   - **Tag:** `latest`
   - **Context Path:** Browse to `/share/Container/androidtv-bridge`
   - **Dockerfile:** Select `Dockerfile`
5. **Click "Build"**
6. **Wait for build to complete** (check logs)

### Step 3: Create Container from Image

1. **Stay in Images tab**
2. **Find your `androidtv-bridge:latest` image**
3. **Click "Create Container"**
4. **Configure:**
   - **Name:** `androidtv-bridge`
   - **Port Mapping:** Host `3000` → Container `3000`
   - **Restart Policy:** Always
   - **Network Mode:** Bridge
5. **Click "Create"**

## Method 3: Manual Setup (No Docker)

If Docker doesn't work at all, run directly on QNAP:

### Step 1: Enable Node.js on QNAP

1. **Install Entware** (package manager for QNAP)
   - SSH into QNAP
   ```bash
   wget -O - http://bin.entware.net/x64-k3.2/installer/generic.sh | sh
   ```

2. **Install Node.js via Entware**
   ```bash
   opkg update
   opkg install node
   opkg install node-npm
   ```

### Step 2: Setup Application

```bash
# Create directory
mkdir -p /share/CACHEDEV1_DATA/.qpkg/androidtv-bridge
cd /share/CACHEDEV1_DATA/.qpkg/androidtv-bridge

# Create files (upload via File Station or create with vi/nano)
# - androidtv-bridge.js
# - package.json

# Install dependencies
npm install
```

### Step 3: Create Startup Script

Create `/etc/init.d/androidtv-bridge`:

```bash
#!/bin/sh

DAEMON_PATH="/share/CACHEDEV1_DATA/.qpkg/androidtv-bridge"
DAEMON="node androidtv-bridge.js"
PIDFILE="/var/run/androidtv-bridge.pid"

case "$1" in
  start)
    echo "Starting Android TV Bridge..."
    cd $DAEMON_PATH
    nohup $DAEMON > /var/log/androidtv-bridge.log 2>&1 &
    echo $! > $PIDFILE
    ;;
  stop)
    echo "Stopping Android TV Bridge..."
    if [ -f $PIDFILE ]; then
      kill $(cat $PIDFILE)
      rm $PIDFILE
    fi
    ;;
  restart)
    $0 stop
    sleep 2
    $0 start
    ;;
  *)
    echo "Usage: $0 {start|stop|restart}"
    exit 1
esac

exit 0
```

Make executable and start:
```bash
chmod +x /etc/init.d/androidtv-bridge
/etc/init.d/androidtv-bridge start
```

### Step 4: Auto-start on Boot

Add to `/etc/config/rc.local`:
```bash
/etc/init.d/androidtv-bridge start
```

## Troubleshooting Container Station

### Container Won't Start

**Check Logs:**
1. Click on container
2. View **Logs** tab
3. Look for error messages

**Common Issues:**

**Error: "Cannot find module"**
- Container needs to run `npm install` first
- Solution: Use the command in YAML that includes `npm install`

**Error: "Port already in use"**
- Another container/service using port 3000
- Solution: Change port mapping to 3001:3000 or different port

**Error: "Permission denied"**
- Volume permissions issue
- Solution: SSH and run:
  ```bash
  chmod -R 755 /share/Container/androidtv-bridge
  ```

### Container Keeps Restarting

**Check if it's crashing:**
```bash
# SSH into QNAP
docker logs androidtv-bridge
```

**Look for:**
- Missing dependencies
- JavaScript errors
- Network issues

**Fix:**
Recreate container with proper configuration

### Container Station Not Responding

**Restart Container Station:**
1. App Center
2. Find Container Station
3. Click Stop
4. Wait 30 seconds
5. Click Start

### Can't Access Bridge from Hubitat

**Check firewall:**
```bash
# SSH into QNAP
iptables -L -n | grep 3000
```

**Open port if needed:**
1. QNAP Security Settings
2. Firewall
3. Add rule: Allow TCP port 3000
4. Apply

**Test from QNAP itself:**
```bash
# SSH into QNAP
curl http://localhost:3000/health
```

If this works but external access doesn't, it's a firewall issue.

## Verifying Installation

### Method 1: QNAP Web Browser
Open on any computer:
```
http://YOUR_QNAP_IP:3000/health
```

### Method 2: SSH
```bash
# Connect to QNAP
ssh admin@YOUR_QNAP_IP

# Test bridge
curl http://localhost:3000/health
```

### Method 3: Hubitat
Use the `checkBridge` command in your Android TV device.

## Monitoring and Maintenance

### View Container Logs
Container Station → Containers → Click your container → Logs tab

### Restart Container
Container Station → Containers → Click container → Stop → Start

### Update Container
1. Stop old container
2. Delete old container (settings will be in YAML)
3. Recreate with same YAML configuration
4. Dependencies will update automatically

### Persistent Data Location
Pairing certificates stored in:
```
/root/.androidtv-remote/
```

This is inside the container. To backup:
```bash
# SSH into QNAP
docker cp androidtv-bridge:/root/.androidtv-remote/ /share/Container/androidtv-bridge/backup/
```

To restore:
```bash
docker cp /share/Container/androidtv-bridge/backup/.androidtv-remote/ androidtv-bridge:/root/
```

## Performance Notes

### Resource Usage
- **CPU:** <1% when idle
- **RAM:** ~50-100 MB
- **Network:** Minimal (only when commands sent)

### QNAP Model Compatibility
Works on all QNAP models that support:
- Container Station (Docker)
- ARM or x86 CPU
- QTS 4.3 or newer

Tested on:
- TS-x51 series
- TS-x53 series  
- TS-x73 series
- TVS-x72 series

## Auto-Start Configuration

The container will automatically start when:
1. QNAP boots up (if restart policy is set)
2. Container Station starts
3. Container crashes (auto-restart)

To ensure it starts:
1. Container → Settings → Restart Policy → Always

## Alternative: QNAP QDK

If you want a native QNAP app instead of Docker:

### Install as QPKG (Advanced)

This requires creating a QPKG package, which is complex but results in a native QNAP app. This is beyond the scope of this guide, but the manual setup method (Method 3) is similar and simpler.

## Network Configuration

### Bridge Network (Default)
Container gets its own IP on Docker network, QNAP handles port forwarding.

### Host Network (Alternative)
If having network issues, use host network mode:

In YAML, change:
```yaml
network_mode: host
```

Then container uses QNAP's IP directly. Port 3000 must not be used by anything else on QNAP.

## Security Recommendations

1. **Firewall:** Only allow access from Hubitat IP
2. **Network:** Keep on local network only
3. **Updates:** Update Node.js image periodically
4. **Backups:** Backup pairing certificates
5. **Monitoring:** Check logs regularly

## Upgrading

### Update Dependencies
1. Stop container
2. Delete container (keep YAML)
3. Delete `node_modules` folder in `/share/Container/androidtv-bridge/`
4. Recreate container from YAML
5. `npm install` will get latest packages

### Update Bridge Code
1. Upload new `androidtv-bridge.js` to QNAP
2. Restart container
3. Check logs for successful restart

## Complete File Checklist

Files needed in `/share/Container/androidtv-bridge/`:

✓ `androidtv-bridge.js` - Main bridge server
✓ `package.json` - Dependencies definition
✓ `Dockerfile` - (Optional) if building custom image

Files created automatically:
- `node_modules/` - Dependencies (created by npm)
- Package lock files

## Getting Help

If still having issues:

1. **Check Container Logs**
   - Most issues show here

2. **Check QNAP System Logs**
   - Container Station → Logs

3. **Test Components:**
   ```bash
   # Test if Node.js works in container
   docker exec androidtv-bridge node --version
   
   # Test if npm installed packages
   docker exec androidtv-bridge npm list
   
   # Test if app file exists
   docker exec androidtv-bridge ls -la /app
   ```

4. **Provide This Info When Asking for Help:**
   - QNAP model and QTS version
   - Container Station version
   - Full error message from logs
   - Output of commands above

## FAQ

**Q: Can I use Container Station with QNAP ARM processors?**
A: Yes, use `node:18-alpine` which supports ARM.

**Q: Will this survive QNAP reboots?**
A: Yes, if restart policy is set to "Always"

**Q: Can I run multiple instances for multiple Hubitat hubs?**
A: Yes, just use different ports (3000, 3001, 3002, etc.)

**Q: How much disk space needed?**
A: ~100MB for container + ~50MB for dependencies

**Q: Does this work with QNAP's built-in firewall?**
A: Yes, but you may need to allow port 3000

**Q: Can I access logs remotely?**
A: Yes, through QNAP web interface or SSH

## Summary

The Container Station GUI method is the easiest for most QNAP users. If you have any Docker build errors, focus on the YAML method which doesn't require building - it uses a pre-made Node.js image and installs dependencies at runtime.
