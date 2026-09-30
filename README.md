# MediRemind 💊 — Smart Medicine Reminder for Senior Citizens

A complete Android Studio project: a medicine reminder app designed **for elderly users**
(big fonts, huge buttons, voice announcements) in **their own native language** —
English, हिंदी (Hindi), मराठी (Marathi), ગુજરાતી (Gujarati).

If the elder does not answer a reminder, the app **automatically sends an SMS to their
guardian** — in the same language.

---

## Features

| Feature | Details |
|---|---|
| 🗣 **Native-language reminders** | Whole UI + spoken announcement (Text-to-Speech) in English/Hindi/Marathi/Gujarati |
| 🔔 **Reliable alarms** | Exact `AlarmManager` alarms, full-screen reminder even when the phone is locked, survives reboot & midnight |
| ⏰ **Missed-dose escalation** | If nobody taps an answer within the grace period (5–60 min, configurable) → automatic **SMS to the guardian** |
| 🍽 **Meal-aware schedule** | Stores breakfast/lunch/evening-tea/dinner times; dose can be "30 min after lunch" etc. |
| 📸 **Medicine photo** | Camera or gallery photo of the tablet/bottle so elders recognise it instantly |
| 🌐 **Twilio SMS API** | Guardian SMS sent via Twilio cloud (no SIM needed in the phone), automatic SIM fallback |
| 📦 **Stock & refill alerts** | Tracks doses remaining, warns when only ≤5 are left |
| 🔊 **Voice announcement** | Speaks the reminder out loud at alarm time **and** on the full-screen page, in the chosen language (switchable in Settings) |
| 🩺 **Emergency doctor** | Family doctor's name + number in Settings → one big blue **Doctor** button opens the dialler |
| 📅 **Doctor appointments** | Add a visit (doctor, reason, place, date, time) → full-screen reminder + voice **15 min / 1 h / 1 day before** (or at the time) |
| 🆘 **SOS button** | One big red button → call guardian or send SOS SMS |
| 📊 **Weekly report** | % of doses taken, per-day taken/missed, guardian-informed history |
| 📵 **100% offline** | Local SQLite database, no internet needed, no ads |

## Senior-friendly design

- Text sizes 18–40 sp, buttons ≥ 64 dp tall
- High-contrast colours, status chips (green ✓ Taken / red ✗ Missed)
- 6 big buttons on the home screen (medicines, settings, report, SOS, doctor, appointments)
- Every screen in one language — no mixed English labels

---

## How to open & run in Android Studio

### 1. Requirements
- **Android Studio** (Ladybug / Meerkat / newer — anything with AGP 9.x support)
- Internet for the **first Gradle sync** (downloads AGP 9.4.1 + libraries)
- A phone with **Android 8.0+ (API 26)** — recommended: a real phone with a SIM card
  (needed to actually send SMS)

### 2. Open the project
1. Start Android Studio → **Open** (not "New Project")
2. Select the folder **`MediRemind`** (the one containing `settings.gradle.kts`) → **OK**
3. Android Studio asks to trust the project → **Trust Project**
4. Wait for **Gradle sync** (first time downloads dependencies; a progress bar appears
   at the bottom). If it complains about a missing SDK platform, accept the
   suggested install (SDK 37 / build-tools 36).
   - The project is pre-configured with the Gradle wrapper (`gradle-9.6.0`),
     AGP `9.4.1`, `compileSdk 37`, `minSdk 26`.

### 3. Run it
1. Plug in your phone with **USB debugging** enabled
   (Settings → About phone → tap *Build number* 7 times → Settings → Developer options → USB debugging)
2. Or create an emulator: **Device Manager → Create device → Pixel 7 → API 33+ image**
3. Press the green **▶ Run** button (or `Shift+F10`) → pick your device
4. On first launch, **allow the permissions** it asks for:
   - **Notifications** → Allow
   - **SMS** → Allow (needed for guardian alerts)
   - **Alarms & reminders** → Allow (exact dose times)

### 4. Build an APK manually (optional)
```powershell
cd C:\Users\Kirti\MediRemind
.\gradlew.bat :app:assembleDebug
# APK: app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :app:testDebugUnitTest   # runs the unit tests
```
> Note: build with Android Studio's embedded JDK (or set `JAVA_HOME` to
> `C:\Program Files\Android\Android Studio1\jbr`) — Gradle 9.6 does not run on newer JDKs like 26.

---

## How to test the whole flow quickly

1. **Settings** → set *Elder's name*, *Guardian's number*, pick **Language**, keep
   grace period = **5 minutes** → Save.
2. **+ Add Medicine** → name "Test", dose "1 tablet", stock 30 → tap **🕐 time** →
   choose a time **2 minutes from now** → Save.
3. Lock the phone and wait. The **full-screen reminder** appears with the medicine
   photo and 3 big buttons; it also **speaks** the instruction in the selected language.
4. **Do not touch anything** for 5 minutes →
   `MissedCheckReceiver` marks the dose missed, shows a red alert, and sends:
   `⚠ MISSED DOSE: Ramesh did not take Test (1 tablet) at 10:30 ...`
   to the guardian (in Hindi/Marathi/Gujarati if selected).
5. Instead tap **✅ I took it** → stock decreases, no SMS, weekly report shows *taken*.

**SMS on an emulator:** emulators cannot send real SMS — use a real phone with a SIM,
or watch Logcat for `SmsHelper: SMS sent to …`.

---

## Twilio SMS API setup (guardian alerts — no SIM needed)

Missed-dose, skip and SOS messages are sent through **Twilio's REST API**
(`util/SmsApi.kt`), and the app **automatically falls back to the phone's SIM**
if Twilio is off or fails. Requires the `INTERNET` permission (already declared).

**5-minute setup:**

1. Create a free account: https://www.twilio.com/try-twilio → verify email
2. Open the **Console** → copy **Account SID** and **Auth Token**
3. Get a trial sender number: Console → Phone Numbers → Manage → Active numbers →
   **Get a trial number** (a +1… number) → that goes into *From*
4. ⚠️ Trial accounts may only send **to verified** numbers:
   Console → Phone Numbers → **Verified Caller IDs** → **Add** the guardian's number
5. In the app: **Settings → SMS service (Twilio)** → paste SID, Auth Token, From
   number → tick **“Send guardian SMS through internet”** → **Save Settings**
6. Test the missed-dose flow, then check **Logcat** (filter `SmsHelper`):
   - `Guardian SMS sent via Twilio` ✅
   - `Twilio failed – trying SIM card instead` → Twilio rejected it
     (most common: number not added as Verified Caller ID, error `21608`)

**How it works technically**
- `POST https://api.twilio.com/2010-04-01/Accounts/{SID}/Messages.json`
- `Authorization: Basic base64(SID:Token)`, body `To/From/Body` form-encoded UTF-8
  (Hindi/Marathi/Gujarati SMS text is percent-encoded correctly)
- Numbers normalised to **E.164** — 10-digit Indian numbers auto-get `+91`
- Network runs off the main thread (`goAsync()` in `MissedCheckReceiver`),
  8 s timeouts, then SIM fallback

---

## 🔊 Voice reminders (spoken in the selected language)

The dose announcement is spoken **from the alarm itself** (`DoseAlarmReceiver`),
so it works even when the screen is off or another app is open — plus on the
full-screen page. `util/VoiceAnnouncer.kt` guarantees the same dose is never
announced twice (`speakOnce` de-duplicates by dose key) and re-applies the
selected language on every utterance, so changing the language in Settings takes
effect on the very next reminder. Turn it off in **Settings → Speak the reminder
out loud**.

*No voice?* Install/enable a TTS engine (e.g. Google TTS) and download the
हिंदी / मराठी / ગુજરાતી language data — without it the app falls back to the
device's default voice.

## 🩺 Emergency doctor contact

1. **Settings → Family doctor** → enter the doctor's name and mobile number
2. Home screen → big blue **🩺 Doctor** button → confirm → the phone's dialler
   opens with the number (the app never places the call by itself)

## 📅 Doctor appointment reminders

1. Home screen → **📅 Appointments → + Add appointment**
2. Fill in the doctor's name, reason, place, date & time, and pick the lead time:
   **at the time / 15 minutes / 1 hour / 1 day before**
3. At the lead time the app shows a **full-screen reminder** (works on the lock
   screen) and **speaks it in the chosen language**:
   *"रमेश! डॉक्टर शर्मा के साथ अपॉइंटमेंट है, 10:30 बजे।"* — with a
   **📞 Call the doctor** button on the reminder itself

Appointments live in the same SQLite DB (table `appointments`, schema v2 – the
upgrade keeps all existing medicines and history) and are re-armed after reboot,
at midnight and on every app start (`Scheduler.scheduleAppointments`).

---

## Project structure

```
MediRemind/
├─ settings.gradle.kts / build.gradle.kts / gradle.properties
├─ gradle/wrapper/…                      # Gradle 9.6 wrapper (auto-downloads)
└─ app/
   ├─ build.gradle.kts                   # AGP 9.4.1, compileSdk 37, minSdk 26
   └─ src/main/
      ├─ AndroidManifest.xml             # permissions + receivers
      ├─ java/com/example/mediremind/
      │  ├─ MedRemindApp.kt              # notification channels (doses, alerts, appointments)
      │  ├─ data/  Db.kt, Prefs.kt       # SQLite (medicines, dose log, appointments) + settings
      │  ├─ util/
      │  │  ├─ Scheduler.kt              # AlarmManager: dose + escalation + appointments + midnight
      │  │  ├─ DoseActions.kt            # taken / snooze / skip logic
      │  │  ├─ Notifs.kt                 # notifications (localised, big buttons)
      │  │  ├─ VoiceAnnouncer.kt         # spoken reminders, de-duplicated, locale-aware
      │  │  ├─ SmsHelper.kt              # guardian SMS (localised)
      │  │  ├─ TimeFmt.kt, LocaleHelper.kt, DoseMath.kt, ImageUtil.kt
      │  ├─ receivers/
      │  │  ├─ DoseAlarmReceiver.kt      # time reached → ring + speak + arm escalation
      │  │  ├─ AppointmentReceiver.kt    # appointment lead time → ring + speak
      │  │  ├─ MissedCheckReceiver.kt    # no answer → SMS to guardian
      │  │  ├─ ActionReceiver.kt         # notification buttons
      │  │  ├─ DayRolloverReceiver.kt    # midnight re-schedule
      │  │  └─ BootReceiver.kt           # reboot re-schedule
      │  └─ ui/
      │     ├─ MainActivity.kt + DoseAdapter.kt   # today's timeline, SOS, doctor, adherence
      │     ├─ ReminderActivity.kt       # full-screen, lock-screen, speaks (TTS)
      │     ├─ AppointmentReminderActivity.kt  # full-screen appointment reminder + call doctor
      │     ├─ AppointmentsActivity.kt + AddAppointmentActivity.kt  # appointment list/editor
      │     ├─ AddMedicineActivity.kt    # photo, dose times, meal offsets, stock
      │     ├─ SettingsActivity.kt       # name, guardian, doctor, language, grace, Twilio
      │     └─ ReportActivity.kt         # weekly %, missed history
      └─ res/
         ├─ values/        # English (default)
         ├─ values-hi/     # हिंदी
         ├─ values-mr/     # मराठी
         ├─ values-gu/     # ગુજરાતી
         └─ layout/        # big-font, senior-friendly layouts
```

## How the escalation works

```
Dose time ──► DoseAlarmReceiver ──► full-screen ReminderActivity + notification
                    │                     │ (speaks in native language)
                    └── arms MissedCheck at time + grace (5–60 min)
                                       │
              answered (took/snooze) ──┤── no answer
                                       ▼
                        MissedCheckReceiver:
                        • status = MISSED in SQLite
                        • red "Dose missed" notification
                        • SMS to guardian (localised template)
```

## Adding more languages

1. Create `app/src/main/res/values-XX/strings.xml` (copy `values/strings.xml`)
2. Translate the values
3. Add a radio button in `activity_settings.xml` + map it in `SettingsActivity.save()`

## Ideas to extend

- Caregiver dashboard / WhatsApp alerts (needs internet + backend)
- Voice answer ("हाँ" heard via speech recognition → mark taken)
- Recurring refill orders, weekly medicine box (7-day pill organiser) view
- Export report as PDF for the doctor
#   s m a r t - m e d i c i n e - r e m i n d e r  
 #   M e d i R e m i n d  
 #   M e d i R e m i n d  
 #   M e d i R e m i n d  
 #   M e d i R e m i n d  
 #   M e d i R e m i n d  
 #   M e d i R e m i  
 #   M e d i R e m i  
 #   M e d i R e m i  
 #   M e d i R e m i  
 