# Android TV Remote - Complete Installation Guide

This guide covers all installation methods from start to finish.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Method 1: HPM Installation (Easiest)](#method-1-hpm-installation)
3. [Method 2: QNAP Installation](#method-2-qnap-installation)
4. [Method 3: Raspberry Pi Installation](#method-3-raspberry-pi-installation)
5. [Method 4: Docker Installation](#method-4-docker-installation)
6. [Method 5: Windows Installation](#method-5-windows-installation)
7. [Pairing Your TV](#pairing-your-tv)
8. [Testing](#testing)
9. [Troubleshooting](#troubleshooting)

## Prerequisites

### What You Need

✅ **Android TV** (2015 or newer)
- Sony, Philips, Sharp, TCL, Hisense, etc.
- Android TV OS 8.0 or higher recommended
- **NOT** Fire TV (different protocol)

✅ **Hubitat Elevation Hub**
- Firmware 2.3.0 or later
- Connected to same network as TV

✅ **Bridge Server Host** (one of these):
- QNAP NAS
- Synology NAS  
- Raspberry Pi (any model)
- Linux server
- Windows PC
- macOS computer
- Docker host

✅ **Network Requirements**
- All devices on same network
- DHCP or static IPs
- No AP isolation
- Ports 3000, 6466, 6467 not blocked

### Before You Start

1. **Find your TV's IP address:**
   - TV: Settings → Network → Network Status
   - Write it down: `________________`

2. **Find your TV's MAC address** (optional, for Wake-on-LAN):
   - TV: Settings → Network → Network Status
   - Write it down: `________________`

3. **Note your Hubitat hub IP:**
   - Hubitat web interface URL
   - Write it down: `________________`

4. **Choose where to run bridge:**
   - QNAP? Raspberry Pi? PC?
   - Note the IP: `________________`

---

## Method 1: HPM Installation

### Step 1: Install Hubitat Package Manager

If you don't have HPM:
1. Go to [Hubitat Package Manager](https://community.hubitat.com/t/beta-hubitat-package-manager/38016)
2. Follow installation instructions
3. Install HPM app

### Step 2: Install Android TV Remote Package

1. Open **Apps** → **Hubitat Package Manager**
2. Click **Install**
3. Click **Search by Keywords**
4. Type: `android tv remote`
5. Select **Android TV Remote Bridge**
6. Click **Next**
7. Review and click **Install**

### Step 3: Create Virtual Device

1. Go to **Devices**
2. Click **Add Device**
3. Click **Virtual**
4. Enter:
   - **Name**: `Living Room TV` (or your choice)
   - **Type**: `Android TV Remote (Bridge)`
5. Click **Save Device**

### Step 4: Install Bridge Server

HPM only installs the Hubitat driver. You still need to install the bridge server separately.

**Choose your platform:**
- [QNAP](#method-2-qnap-installation)
- [Raspberry Pi](#method-3-raspberry-pi-installation)
- [Docker](#method-4-docker-installation)
- [Windows](#method-5-windows-installation)

---

## Method 2: QNAP Installation

### Prerequisites
- QNAP NAS with Container Station support
- QTS 4.3 or newer
- Admin access to QNAP

### Step 1: Install Container Station

1. Open **App Center**
2. Search for **Container Station**
3. Click **Install**
4. Wait for installation
5. Open Container Station

### Step 2: Prepare Files

1. Open **File Station**
2. Navigate to **Container** folder (create if needed)
3. Create new folder: `androidtv-bridge`
4. Upload these files:
   - `androidtv-bridge.js`
   - `package.json`

**Creating package.json:**
```json
{
  "name": "androidtv-bridge",
  "version": "1.0.0",
  "dependencies": {
    "express": "^4.18.2",
    "body-parser": "^1.20.2",
    "androidtv-remote": "^1.0.10"
  }
}
```

### Step 3: Create Container

1. Open **Container Station**
2. Click **Create** → **Create Application**
3. Paste this YAML:

```yaml
version: '3'
services:
  androidtv-bridge:
    image: node:18-alpine
    container_name: androidtv-bridge
    restart: unless-stopped
    ports:
      - "3000:3000"
    volumes:
      - /share/Container/androidtv-bridge:/app
    working_dir: /app
    command: sh -c "npm install && node androidtv-bridge.js"
    environment:
      - NODE_ENV=production
```

4. Click **Validate**
5. Click **Create**
6. Name: `androidtv-bridge`
7. Click **Create**

### Step 4: Verify Container

1. Go to **Containers** tab
2. Find **androidtv-bridge**
3. Status should be **Running** (green)
4. Click container → **Logs**
5. Should see: `Server running on port 3000`

### Step 5: Test Bridge

From any computer:
```bash
curl http://YOUR_QNAP_IP:3000/health
```

Should return:
```json
{"status":"ok","connectedDevices":0}
```

✅ **QNAP bridge is ready!**

Continue to [Install Hubitat Driver](#install-hubitat-driver)

---

## Method 3: Raspberry Pi Installation

### Prerequisites
- Raspberry Pi (any model)
- Raspberry Pi OS installed
- Network connection
- SSH access

### Step 1: Install Node.js

```bash
# Update system
sudo apt update
sudo apt upgrade -y

# Install Node.js 18
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# Verify installation
node --version  # Should show v18.x.x
npm --version   # Should show 9.x.x or higher
```

### Step 2: Create Bridge Directory

```bash
# Create directory
mkdir ~/androidtv-bridge
cd ~/androidtv-bridge

# Create package.json
cat > package.json << 'EOF'
{
  "name": "androidtv-bridge",
  "version": "1.0.0",
  "dependencies": {
    "express": "^4.18.2",
    "body-parser": "^1.20.2",
    "androidtv-remote": "^1.0.10"
  }
}
EOF
```

### Step 3: Upload Bridge Code

**Option A: SCP from computer:**
```bash
scp androidtv-bridge.js pi@YOUR_PI_IP:~/androidtv-bridge/
```

**Option B: Create directly:**
```bash
nano ~/androidtv-bridge/androidtv-bridge.js
# Paste the bridge code
# Save: Ctrl+O, Enter, Ctrl+X
```

### Step 4: Install Dependencies

```bash
cd ~/androidtv-bridge
npm install
```

### Step 5: Test Bridge

```bash
node androidtv-bridge.js
```

Should see:
```
====================================
Android TV Remote Bridge Server
====================================
Server running on port 3000
```

Press **Ctrl+C** to stop.

### Step 6: Auto-Start with systemd

```bash
# Create service file
sudo nano /etc/systemd/system/androidtv-bridge.service
```

Paste this:
```ini
[Unit]
Description=Android TV Bridge Server
After=network.target

[Service]
Type=simple
User=pi
WorkingDirectory=/home/pi/androidtv-bridge
ExecStart=/usr/bin/node /home/pi/androidtv-bridge/androidtv-bridge.js
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Save and exit.

Enable and start:
```bash
sudo systemctl enable androidtv-bridge
sudo systemctl start androidtv-bridge
sudo systemctl status androidtv-bridge
```

Should show **active (running)**.

### Step 7: Test from Network

From any computer:
```bash
curl http://YOUR_PI_IP:3000/health
```

✅ **Raspberry Pi bridge is ready!**

Continue to [Install Hubitat Driver](#install-hubitat-driver)

---

## Method 4: Docker Installation

### Prerequisites
- Docker installed
- Docker Compose (optional)

### Option A: Docker Run

```bash
# Create directory for files
mkdir ~/androidtv-bridge
cd ~/androidtv-bridge

# Create package.json
cat > package.json << 'EOF'
{
  "name": "androidtv-bridge",
  "version": "1.0.0",
  "dependencies": {
    "express": "^4.18.2",
    "body-parser": "^1.20.2",
    "androidtv-remote": "^1.0.10"
  }
}
EOF

# Copy androidtv-bridge.js to this directory

# Run container
docker run -d \
  --name androidtv-bridge \
  --restart unless-stopped \
  -p 3000:3000 \
  -v $(pwd):/app \
  -w /app \
  node:18-alpine \
  sh -c "npm install && node androidtv-bridge.js"
```

### Option B: Docker Compose

Create `docker-compose.yml`:
```yaml
version: '3'
services:
  androidtv-bridge:
    image: node:18-alpine
    container_name: androidtv-bridge
    restart: unless-stopped
    ports:
      - "3000:3000"
    volumes:
      - ./:/app
    working_dir: /app
    command: sh -c "npm install && node androidtv-bridge.js"
```

Run:
```bash
docker-compose up -d
```

### Verify

```bash
# Check logs
docker logs androidtv-bridge

# Should see
# Server running on port 3000

# Test
curl http://localhost:3000/health
```

✅ **Docker bridge is ready!**

Continue to [Install Hubitat Driver](#install-hubitat-driver)

---

## Method 5: Windows Installation

### Step 1: Install Node.js

1. Download from [nodejs.org](https://nodejs.org)
2. Run installer (choose LTS version)
3. Use default options
4. Restart computer

Verify:
```cmd
node --version
npm --version
```

### Step 2: Create Project

```cmd
REM Create directory
mkdir C:\androidtv-bridge
cd C:\androidtv-bridge

REM Create package.json
notepad package.json
```

Paste this:
```json
{
  "name": "androidtv-bridge",
  "version": "1.0.0",
  "dependencies": {
    "express": "^4.18.2",
    "body-parser": "^1.20.2",
    "androidtv-remote": "^1.0.10"
  }
}
```

Save and close.

### Step 3: Add Bridge File

Copy `androidtv-bridge.js` to `C:\androidtv-bridge\`

### Step 4: Install Dependencies

```cmd
cd C:\androidtv-bridge
npm install
```

### Step 5: Test

```cmd
node androidtv-bridge.js
```

Should see server running message.

### Step 6: Auto-Start (Optional)

**Using NSSM (Recommended):**

1. Download [NSSM](https://nssm.cc/download)
2. Extract to `C:\nssm`
3. Run as administrator:

```cmd
cd C:\nssm\win64
nssm install AndroidTVBridge

# In the GUI:
Path: C:\Program Files\nodejs\node.exe
Startup directory: C:\androidtv-bridge
Arguments: androidtv-bridge.js

# Click Install Service
# Start service
nssm start AndroidTVBridge
```

✅ **Windows bridge is ready!**

Continue to [Install Hubitat Driver](#install-hubitat-driver)

---

## Install Hubitat Driver

*(Skip if using HPM)*

### Step 1: Install Driver Code

1. Open Hubitat web interface
2. Go to **Drivers Code**
3. Click **+ New Driver**
4. Paste contents of `Android_TV_Remote_Bridge_Driver.groovy`
5. Click **Save**

### Step 2: Create Virtual Device

1. Go to **Devices**
2. Click **+ Add Device**
3. Click **Virtual**
4. Enter:
   - **Device Name**: `Living Room TV`
   - **Device Label**: (optional)
   - **Type**: `Android TV Remote (Bridge)`
5. Click **Save Device**

### Step 3: Configure Device

1. Click on your new device
2. Scroll to **Preferences**
3. Fill in:
   - **Android TV IP Address**: `192.168.1.100` (your TV)
   - **Android TV MAC Address**: `AA:BB:CC:DD:EE:FF` (optional)
   - **Bridge Server IP**: `192.168.1.50` (your bridge)
   - **Bridge Server Port**: `3000`
   - **Device ID**: `living-room-tv`
   - **Device Name**: `Hubitat`
4. Click **Save Preferences**

### Step 4: Verify Bridge Connection

1. Click **Commands** → **checkBridge**
2. Check **Current States**
3. Should see: `bridgeStatus: online`

✅ **Hubitat driver is configured!**

---

## Pairing Your TV

### Step 1: Prepare

1. Make sure TV is powered on
2. Bridge server is running
3. Hubitat device configured

### Step 2: Start Pairing

1. Open your Android TV Remote device in Hubitat
2. Click **Commands**
3. Find **startPairing**
4. Click it
5. **Look at your TV screen** within 10 seconds

### Step 3: Enter Code

1. TV will show a 6-character code (like `F8A987`)
2. In Hubitat, click **completePairing**
3. Enter the code **exactly** as shown
4. Click outside text box or press Enter
5. Wait for confirmation

### Step 4: Verify

1. Check Hubitat **Logs**
2. Should see: `PAIRING SUCCESSFUL!`
3. Click **Refresh**
4. Check **Current States**:
   - `paired: true`
   - `connectionStatus: connected`
   - `bridgeStatus: online`

✅ **TV is paired and ready!**

---

## Testing

### Test Basic Commands

Try these in order:

1. **Home**: Click `home` command
   - TV should go to home screen

2. **Volume**: Click `volumeUp`
   - Volume should increase

3. **Mute**: Click `mute`
   - Sound should mute

4. **Navigation**: Try `dpadUp`, `dpadDown`, etc.
   - Should navigate menus

### Test Power

1. **Turn Off**: Click `off` or `powerToggle`
   - TV should turn off

2. **Turn On**: Click `on` or `wakeUp`
   - TV should turn on
   - If not, configure MAC address for WOL

### Test Apps

```groovy
// Try launching YouTube
launchApp("vnd.youtube://")
```

### Check Logs

If anything doesn't work:
1. Enable debug logging in preferences
2. Try command again
3. Check logs for errors

---

## Troubleshooting

### Bridge Shows Offline

**Test manually:**
```bash
curl http://BRIDGE_IP:3000/health
```

**If fails:**
- Check bridge is running
- Verify IP address
- Check firewall
- Verify port 3000 not in use

### Pairing Fails

**No code on TV:**
- Check Android TV Remote Service is enabled
- TV: Settings → Apps → Show system apps
- Clear service data, try again

**Invalid code:**
- Must be exactly 6 characters
- Enter within 60 seconds
- Can be letters and numbers
- Try pairing again

### Commands Don't Work

**Check connection:**
- Click `refresh`
- Verify `connectionStatus: connected`

**If not connected:**
- Click `connect`
- Check bridge logs
- Verify TV is on
- Try re-pairing

### TV Won't Wake

**Solutions:**
1. Add MAC address to preferences
2. Enable WOL on TV
3. Use static IP for TV
4. Try `powerToggle` instead of `on`

---

## Next Steps

1. **Create automations** - See README for examples
2. **Add to dashboard** - Create control panel
3. **Test all features** - Explore commands
4. **Add more TVs** - Repeat for other rooms
5. **Join community** - Share your setup!

---

## Need More Help?

- 📖 [Full README](README.md)
- 🔧 [Troubleshooting Guide](TROUBLESHOOTING_PAIRING.md)
- 💾 [QNAP Detailed Guide](QNAP_Container_Station_Setup.md)
- 💬 [Hubitat Community Forums](https://community.hubitat.com)

**Enjoy your Android TV integration! 📺✨**
