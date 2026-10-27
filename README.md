# Notify Forwarder (Android → iPhone)

Mirrors every notification on your Android phone to your iPhone as a plain,
read-only notification. You reply on the Android phone.

Why this direction: iOS does not allow any app to read other apps'
notifications, so an iPhone can never be the sender. Android can.

## How it works
Android app (NotificationListenerService) → POST to an ntfy topic →
ntfy app on the iPhone shows it as a push notification.

## Setup
1. **Build:** open this folder in Android Studio, let Gradle sync, then
   Run onto the Android phone (or Build > Build APK and sideload it).
2. **Android:** open *Notify Forwarder*, tap *Grant notification access*,
   switch it on for Notify Forwarder. Tap *Copy topic*.
   - On some phones (Samsung, Pixel with battery saver) also set the app's
     battery usage to *Unrestricted* so it keeps running.
3. **iPhone:** install **ntfy** from the App Store, tap **+**, paste the
   topic, leave server as `ntfy.sh`, subscribe. Allow notifications.
4. Back on Android tap *Send test to iPhone*.

## What gets skipped
Ongoing notifications (music, navigation, downloads), group summaries,
progress bars, duplicates of the same notification, and any package names
you list in the block box (e.g. `com.whatsapp`).

## Privacy
- On the public ntfy.sh server the topic name works like a password: anyone
  who knows it can read the messages. The app generates a random one — keep it.
- *Hide message text* sends only the app name ("WhatsApp — New notification").
- For full privacy, self-host ntfy (one Docker container) on your home
  server and put its URL in the *ntfy server* field.
