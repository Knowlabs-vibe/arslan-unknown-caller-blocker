# Unknown Caller Blocker

Android app that automatically blocks calls from numbers not in your contact list and sends them an auto-reply via SMS and WhatsApp.

## Features

- ✅ Automatically blocks calls from unknown numbers
- ✅ Sends SMS auto-reply
- ✅ Opens WhatsApp with pre-filled message
- ✅ Customizable message
- ✅ Toggle SMS/WhatsApp separately
- ✅ Works after phone restarts

## Requirements

- Android 10+ (API 29+)
- Samsung S24 Ultra or similar device
- WhatsApp installed (for WhatsApp messages)

## Permissions Required

- READ_CONTACTS - Check if caller is in contacts
- READ_CALL_LOG - Access call information
- READ_PHONE_STATE - Detect incoming calls
- ANSWER_PHONE_CALLS - Decline/block calls
- SEND_SMS - Send auto-reply SMS

## Build Instructions

### Option 1: Android Studio (Recommended)

1. Install [Android Studio](https://developer.android.com/studio)
2. Open this folder as a project
3. Wait for Gradle sync to complete
4. Connect your phone via USB (enable USB debugging)
5. Click "Run" (green play button)

### Option 2: Command Line

```bash
# Make sure ANDROID_HOME is set
export ANDROID_HOME=~/Android/Sdk

# Build debug APK
./gradlew assembleDebug

# APK will be at:
# app/build/outputs/apk/debug/app-debug.apk
```

## Install on Phone (Sideload)

1. On your Samsung S24 Ultra:
   - Go to Settings → Apps → Special access → Install unknown apps
   - Allow your file manager or browser

2. Transfer the APK to your phone:
   - USB cable
   - Google Drive
   - Email to yourself
   - AirDrop equivalent

3. Open the APK file on your phone and install

4. Open the app and:
   - Grant all permissions when prompted
   - Set as default call screening app when prompted

## Usage

1. Open the app
2. Make sure "Enable Blocking" is ON
3. Customize your auto-reply message
4. Toggle SMS and/or WhatsApp as desired
5. That's it! Unknown callers will be blocked automatically

## How It Works

The app uses Android's `CallScreeningService` API which:
1. Intercepts incoming calls before your phone rings
2. Checks if the number exists in your contacts
3. If not found: blocks the call and sends messages
4. If found: lets the call through normally

## Troubleshooting

**Calls not being blocked:**
- Make sure the app is set as the default call screening app
- Check that all permissions are granted
- Ensure "Enable Blocking" is turned on

**WhatsApp message not sending:**
- WhatsApp needs to be installed
- The app opens WhatsApp with a pre-filled message - you may need to tap send
- For fully automatic WhatsApp, you'd need WhatsApp Business API (requires business account)

**SMS not sending:**
- Check SMS permission is granted
- Make sure you have SMS credit/plan

## Publishing to Play Store

When ready to publish:

1. Create a Google Play Developer account ($25 one-time fee)
2. Generate a signed release APK:
   ```bash
   ./gradlew assembleRelease
   ```
3. Create store listing with screenshots
4. Upload APK and submit for review

## License

MIT License - Free to use and modify
