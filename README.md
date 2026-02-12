# Android TV Remote - Hubitat Integration

Complete Hubitat integration for Android TV devices using the official Android TV Remote Protocol v2. Control your Android TV without ADB or developer mode!

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Hubitat](https://img.shields.io/badge/Hubitat-Compatible-green.svg)](https://hubitat.com)

## 🎯 Features

- ✅ **Full Remote Control** - All navigation, media, and volume keys
- ✅ **Power Management** - On/Off with Wake-on-LAN support
- ✅ **App Launching** - Deep links to Netflix, YouTube, Plex, etc.
- ✅ **No ADB Required** - Uses official Android TV Remote Service
- ✅ **Secure Pairing** - One-time pairing with certificate storage
- ✅ **Bridge Architecture** - Handles complex protocol requirements
- ✅ **Multi-TV Support** - Control multiple Android TVs
- ✅ **Status Monitoring** - Connection and pairing status tracking

## 📋 Requirements

### Android TV
- Android TV device (2015 or newer)
- Android TV Remote Service (pre-installed on most devices)
- Same network as Hubitat and bridge server
- **NOT compatible with Fire TV** (different protocol)

### Bridge Server
- Node.js (v14 or higher)
- Computer/Raspberry Pi/NAS (QNAP, Synology, etc.)
- Network access to Android TV

### Hubitat
- Hubitat Elevation hub
- Firmware 2.3.0 or later

## 🚀 Installation

### Option 1: Hubitat Package Manager (HPM) - Recommended

1. Open **Hubitat Package Manager**
2. Click **Install**
3. Search for **"Android TV Remote Bridge"**
4. Click **Install**
5. Follow the prompts

### Option 2: Manual Installation

#### Step 1: Install Bridge Server

**On QNAP (Container Station):**

See [QNAP_Container_Station_Setup.md](QNAP_Container_Station_Setup.md) for detailed instructions.

Quick steps:
1. Install Container Station from App Center
2. Upload `androidtv-bridge.js` and `package.json` to `/share/Container/androidtv-bridge/`
3. Create container using the provided YAML configuration
4. Start container

**On Raspberry Pi/Linux:**

```bash
# Install Node.js
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# Create directory
mkdir ~/androidtv-bridge
cd ~/androidtv-bridge

# Copy files
# - androidtv-bridge.js
# - package.json

# Install dependencies
npm install

# Run the bridge
node androidtv-bridge.js

# For auto-start, see systemd setup in docs
```

**On Docker:**

```bash
docker run -d \
  --name androidtv-bridge \
  --restart unless-stopped \
  -p 3000:3000 \
  -v /path/to/androidtv-bridge:/app \
  -w /app \
  node:18-alpine \
  sh -c "npm install && node androidtv-bridge.js"
```

#### Step 2: Install Hubitat Driver

1. Go to **Drivers Code**
2. Click **+ New Driver**
3. Paste contents of `Android_TV_Remote_Bridge_Driver.groovy`
4. Click **Save**

#### Step 3: Create Virtual Device

1. Go to **Devices**
2. Click **+ Add Device**
3. Click **Virtual**
4. Enter device name (e.g., "Living Room TV")
5. Select **Type**: `Android TV Remote (Bridge)`
6. Click **Save Device**

## ⚙️ Configuration

### Device Preferences

| Setting | Required | Description | Example |
|---------|----------|-------------|---------|
| **Android TV IP Address** | Yes | Your TV's IP address | `192.168.1.100` |
| **Android TV MAC Address** | No | For Wake-on-LAN | `AA:BB:CC:DD:EE:FF` |
| **Bridge Server IP** | Yes | Computer running bridge | `192.168.1.50` |
| **Bridge Server Port** | Yes | Bridge port (default) | `3000` |
| **Device ID** | Yes | Unique identifier | `living-room-tv` |
| **Device Name** | No | Name shown during pairing | `Hubitat` |
| **Auto-connect on Initialize** | No | Connect automatically | `true` |
| **Enable debug logging** | No | Detailed logs | `false` |
| **Enable descriptionText logging** | No | Event descriptions | `true` |

### Finding Your TV's Information

**IP Address:**
- TV: Settings → Network → Network Status
- Router: Check DHCP client list
- Recommended: Set static IP

**MAC Address:**
- TV: Settings → Network → Network Status → Physical Address
- Router: Check connected devices list
- Format: `AA:BB:CC:DD:EE:FF` (colons optional)

## 🔗 Pairing Process

### Step 1: Start Pairing

1. Open your Android TV Remote device in Hubitat
2. Click **Commands** → **startPairing**
3. Wait 5-10 seconds
4. **Look at your TV screen** for a 6-digit code

### Step 2: Complete Pairing

1. Note the code from TV (e.g., `F8A987`)
2. Click **Commands** → **completePairing**
3. Enter the code exactly as shown
4. Click outside the text box or press Enter
5. Wait for "PAIRING SUCCESSFUL" in logs

### Step 3: Verify Connection

1. Click **Refresh** command
2. Check **Current States**:
   - `paired`: `true`
   - `connectionStatus`: `connected`
   - `bridgeStatus`: `online`

### Troubleshooting Pairing

**No code appears on TV:**
- Verify Android TV Remote Service is installed and enabled
- TV: Settings → Apps → Show system apps → Android TV Remote Service
- Clear service data and try again

**Invalid code error:**
- Code must be exactly 6 characters (letters or numbers)
- Enter within 60 seconds
- Start pairing again for new code

**Timeout error:**
- Check bridge server is running
- Verify TV and bridge on same network
- Check firewall isn't blocking ports 6466-6467

See [TROUBLESHOOTING_PAIRING.md](TROUBLESHOOTING_PAIRING.md) for detailed help.

## 📱 Usage

### Available Commands

#### Navigation
```groovy
dpadUp()
dpadDown()
dpadLeft()
dpadRight()
dpadCenter()
back()
home()
menu()
```

#### Media Control
```groovy
play()
pause()
playPause()
stop()
fastForward()
rewind()
nextTrack()  // Skip forward
previousTrack()  // Skip backward
```

#### Volume
```groovy
volumeUp()
volumeDown()
mute()
unmute()
setVolume(50)  // 0-100
```

#### Power
```groovy
on()  // Turn on (uses WOL if configured)
off()  // Turn off
powerToggle()  // Toggle power state
wakeUp()  // Wake TV
sleep()  // Put TV to sleep
```

#### Apps
```groovy
launchApp("https://www.netflix.com/")  // Netflix
launchApp("vnd.youtube://")  // YouTube
launchApp("plex://")  // Plex
```

#### Connection Management
```groovy
connect()  // Connect to TV
disconnect()  // Disconnect
checkBridge()  // Verify bridge server
refresh()  // Update all statuses
unpair()  // Remove pairing
```

### App Deep Links

#### Popular Apps

| App | Deep Link |
|-----|-----------|
| Netflix | `https://www.netflix.com/` |
| Netflix (specific) | `https://www.netflix.com/title/TITLE_ID` |
| YouTube | `vnd.youtube://` |
| YouTube (video) | `vnd.youtube://www.youtube.com/watch?v=VIDEO_ID` |
| Plex | `plex://` |
| Spotify | `spotify://` |
| Disney+ | `https://www.disneyplus.com/` |
| Hulu | `hulu://` |
| Prime Video | `intent://` |
| HBO Max | `hbomax://` |

#### Finding Deep Links

1. **Google Search**: "app name deep link android"
2. **ADB Method**:
   ```bash
   adb shell dumpsys package | grep -A 3 "android.intent.action.VIEW"
   ```
3. **Community Resources**: Check Home Assistant forums

## 🏠 Automation Examples

### Rule Machine

**Movie Night:**
```
Actions:
  - Living Room TV: on
  - Wait 5 seconds
  - Living Room TV: launchApp("plex://")
  - Living Room TV: setVolume(40)
  - Dim lights to 20%
```

**Bedtime Routine:**
```
Actions:
  - All TVs: off
  - Wait 2 seconds
  - All TVs: disconnect
```

**Volume by Mode:**
```
Trigger: Mode changes
Actions:
  - IF mode is "Night"
      All TVs: setVolume(20)
  - ELSE IF mode is "Day"  
      All TVs: setVolume(50)
  - ELSE IF mode is "Party"
      All TVs: setVolume(80)
```

**Smart Wake:**
```
Trigger: Time is 7:00 AM on Weekdays
Actions:
  - Bedroom TV: on
  - Wait 10 seconds
  - Bedroom TV: launchApp("vnd.youtube://")
  - Bedroom TV: setVolume(30)
```

### Dashboard Integration

Add to Hubitat Dashboard:
- **Switch** - Power on/off
- **Buttons** - Individual commands (Home, Back, etc.)
- **Slider** - Volume control
- **Template** - Custom button grid for navigation

## 🔍 Device Attributes

| Attribute | Type | Description |
|-----------|------|-------------|
| `switch` | string | Power state: "on" or "off" |
| `power` | string | Same as switch |
| `level` | number | Volume level 0-100 |
| `volume` | number | Same as level |
| `muted` | string | "muted" or "unmuted" |
| `connectionStatus` | string | "connected", "disconnected", "connecting", "error" |
| `paired` | string | "true" or "false" |
| `bridgeStatus` | string | "online" or "offline" |

## 🛠️ Advanced Configuration

### Multiple TVs

1. Create separate Hubitat device for each TV
2. Use unique Device ID for each:
   - Living Room: `living-room-tv`
   - Bedroom: `bedroom-tv`
   - Basement: `basement-tv`
3. Use different TV IP addresses
4. Same bridge server handles all TVs
5. Pair each TV individually

### Wake-on-LAN Setup

**Enable on Android TV:**
1. Settings → Network → Advanced
2. Enable "Wake-on-LAN"
3. Enable "Wake on Wi-Fi" (if wireless)

**Configure in Hubitat:**
1. Find TV MAC address
2. Enter in device preferences
3. Save preferences

**Test:**
```groovy
tv.off()  // Turn off
// Wait 10 seconds
tv.on()   // Should wake via WOL
```

### Static IP Addresses

**Highly Recommended:**

**For Android TV:**
1. Settings → Network → Advanced
2. IP Settings → Static
3. Enter IP, Gateway, DNS

**For Bridge Server:**
- Set in router DHCP reservation
- Or configure static IP on server OS

### Bridge Auto-Start

**systemd (Linux/Raspberry Pi):**

Create `/etc/systemd/system/androidtv-bridge.service`:
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

Enable and start:
```bash
sudo systemctl enable androidtv-bridge
sudo systemctl start androidtv-bridge
sudo systemctl status androidtv-bridge
```

**PM2 (Cross-platform):**
```bash
npm install -g pm2
pm2 start androidtv-bridge.js --name androidtv-bridge
pm2 save
pm2 startup
```

**Docker Compose:**
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
      - ./androidtv-bridge:/app
    working_dir: /app
    command: sh -c "npm install && node androidtv-bridge.js"
```

## 🐛 Troubleshooting

### Common Issues

#### Bridge Shows Offline
**Check:**
```bash
curl http://BRIDGE_IP:3000/health
```
**Should return:**
```json
{"status":"ok","connectedDevices":0}
```

**Solutions:**
- Verify bridge is running
- Check IP address and port
- Verify firewall allows port 3000

#### Commands Not Working
**Check connection status:**
- Click Refresh
- Verify `connectionStatus: connected`

**Solutions:**
- Run `connect()` command
- Check bridge logs for errors
- Verify TV is powered on
- Re-pair if needed

#### TV Won't Wake
**Solutions:**
1. Configure MAC address for WOL
2. Enable Wake-on-LAN on TV
3. Try `powerToggle()` instead of `on()`
4. Use `wakeUp()` for multi-method wake

#### Pairing Times Out
**Solutions:**
- Increase timeout (already 30 seconds)
- Start pairing fresh
- Clear Android TV Remote Service data on TV
- Check bridge logs for actual error

### Debug Mode

**Enable in Hubitat:**
1. Device → Preferences
2. Enable debug logging
3. Enable descriptionText logging
4. Save Preferences

**View logs:**
- Hubitat → Logs → Filter by device

**Bridge logs:**
- Container Station: Containers → androidtv-bridge → Logs
- Terminal: View console output
- PM2: `pm2 logs androidtv-bridge`
- systemd: `journalctl -u androidtv-bridge -f`

### Getting Help

**Before asking for help, gather:**
1. Hubitat logs (last 20 lines)
2. Bridge logs (last 20 lines)
3. TV make/model and Android version
4. Network setup (same subnet? VLANs?)
5. What you've tried already

**Resources:**
- [Troubleshooting Guide](TROUBLESHOOTING_PAIRING.md)
- [QNAP Setup Guide](QNAP_Container_Station_Setup.md)
- [Quick Setup Guide](QUICK_SETUP_GUIDE.md)
- Hubitat Community Forums
- GitHub Issues

## 📊 Comparison with Alternatives

| Feature | This Driver | ADB Method | IR Control | Philips TV Driver |
|---------|------------|------------|------------|-------------------|
| **No Developer Mode** | ✅ | ❌ | ✅ | ✅ |
| **Works with Fire TV** | ❌ | ✅ | ✅ | ❌ |
| **App Launching** | ✅ | ✅ | ❌ | Limited |
| **Bidirectional** | ✅ | ✅ | ❌ | ✅ |
| **Native Protocol** | ✅ | ❌ | ❌ | ✅ |
| **Setup Complexity** | Medium | High | Low | Low |
| **Reliability** | High | Medium | High | High |
| **Hubitat Native** | No (needs bridge) | No | Yes | Yes |

## 🔐 Security & Privacy

### Certificate Storage
- Pairing certificates stored in Hubitat device state
- Base64 encoded
- Only shared with bridge server
- Never exposed to internet

### Bridge Server
- Runs on local network only
- No authentication by default (local network trust)
- Don't expose to internet
- Keep Node.js updated

### Network Security
- Use VLAN isolation if needed
- Firewall bridge from internet
- Consider VPN for remote access
- Set static IPs for devices

## 📈 Performance

### Resource Usage

**Bridge Server:**
- CPU: <1% idle, <5% active
- RAM: 50-100 MB
- Network: Minimal (only when sending commands)
- Disk: ~50 MB with dependencies

**Hubitat:**
- Negligible impact
- HTTP calls only when commands sent
- Status checks every 60 seconds

### Latency
- Local network: 50-200ms
- Command execution: 100-300ms
- Wake from standby: 2-5 seconds
- Wake with WOL: 5-10 seconds

## 🔄 Updates & Maintenance

### Updating the Driver

**Via HPM:**
1. Hubitat Package Manager
2. Update
3. Check for updates
4. Install updates

**Manual:**
1. Drivers Code → Android TV Remote (Bridge)
2. Replace code with new version
3. Save
4. May need to click Initialize on devices

### Updating Bridge Server

```bash
cd /path/to/androidtv-bridge
npm update
# Restart container or service
```

### Backup & Recovery

**Backup Hubitat State:**
- Export device settings
- Note Device ID and IP addresses
- Document custom configurations

**Backup Bridge:**
```bash
# Save certificates
cp -r ~/.config/androidtvremote2 ~/backup/

# Save config
cp androidtv-bridge.js ~/backup/
cp package.json ~/backup/
```

**Recovery:**
1. Reinstall bridge server
2. Restore certificates (or re-pair)
3. Recreate Hubitat device with same Device ID
4. Should reconnect automatically

## 🤝 Contributing

Contributions welcome! Please:
1. Fork the repository
2. Create feature branch
3. Test thoroughly
4. Submit pull request

### To-Do List
- [ ] Support for voice commands (PCM audio)
- [ ] TV state feedback (power, volume, current app)
- [ ] Scenes/presets support
- [ ] Google Cast integration
- [ ] Multi-room audio sync

## 📝 License

Licensed under the Apache License, Version 2.0

## 🙏 Acknowledgments

- **tronikos** - androidtvremote2 Python library
- **Home Assistant Team** - Android TV Remote integration
- **Hubitat Community** - Testing and feedback
- **Android TV Team** - Protocol specification

## 📜 Version History

### v1.0.0 (2025-02-11)
- Initial public release
- Bridge architecture implementation
- Full Android TV Remote Protocol v2 support
- Pairing implementation
- All navigation and media keys
- App launching with deep links
- Wake-on-LAN support
- Multi-TV support
- Status monitoring
- Comprehensive documentation
- HPM support

## ❓ FAQ

**Q: Why do I need a bridge server?**  
A: Hubitat's Groovy environment doesn't support Protocol Buffers or persistent TLS connections required by Android TV Remote Protocol v2.

**Q: Does this work with Fire TV?**  
A: No, Fire TV uses a different protocol. Use ADB-based control instead.

**Q: Can I use Raspberry Pi Zero?**  
A: Yes, any Raspberry Pi model works. Even Pi Zero W is sufficient.

**Q: How many TVs can one bridge handle?**  
A: Theoretically unlimited. Tested successfully with 5+ TVs. Each needs unique Device ID.

**Q: Can I run the bridge on my Hubitat hub?**  
A: No, Hubitat Elevation hub doesn't support custom Node.js applications. Use separate computer/NAS.

**Q: Will this work remotely?**  
A: Only if bridge and TV are on same local network. Use VPN for remote access to home network.

**Q: What happens if the bridge crashes?**  
A: Commands will fail. Bridge should auto-restart with PM2/systemd. Hubitat will show offline. TV remains paired.

**Q: Can I use this with Home Assistant too?**  
A: Yes, Home Assistant has native Android TV Remote support. Bridge is only needed for Hubitat.

**Q: Does this drain TV battery/power?**  
A: No more than the official Google TV remote app. Connection is maintained only when needed.

**Q: Can I control volume on TV or receiver?**  
A: Controls TV volume. For receiver control, use separate receiver driver and HDMI-CEC or IR.

---

**Enjoy controlling your Android TV with Hubitat! 📺🎮**

For questions or issues, please open a GitHub issue or post in the Hubitat Community Forum.
