# Trenutno stanje projekta — Slagalica (Student 1)

> **Svrha ovog fajla:** Detaljan opis svega što je urađeno, kako je implementirano,
> i šta sledeće treba raditi. Namenjen AI asistentima (Claude, Copilot) za nastavak rada.
> **Datum:** Maj 2026.

---

## 1. Osnovne informacije

| Stavka | Vrednost |
|--------|----------|
| Projekat | Android Java, native app |
| Package name | `com.example.slagalica` (**uvek ovaj — nikad `rs.ftn.slagalica`**) |
| Jezik | **Java 17** (NE Kotlin) |
| UI | Android Views + XML layouts + Material Components 3 |
| Build | Gradle Groovy DSL (`build.gradle`, ne `build.gradle.kts`) |
| minSdk | 26 |
| targetSdk / compileSdk | 35 |
| Backend | Firebase (Auth, Firestore, Realtime Database, Messaging) |
| Firebase Storage | **NE koristi se** (zahteva plaćeni plan) |

**Lokacija projekta:** `C:\Users\damja\AndroidStudioProjects\Slagalica`

---

## 2. Gradle konfiguracija

### app/build.gradle (ključni delovi)

```groovy
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)   // NE stavljati verzioni broj ovde
}

android {
    namespace 'com.example.slagalica'
    compileSdk 35
    defaultConfig {
        applicationId "com.example.slagalica"
        minSdk 26
        targetSdk 35
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_11
        targetCompatibility JavaVersion.VERSION_11
    }
}

dependencies {
    // Firebase BoM (bez verzija za pojedinačne biblioteke)
    implementation platform('com.google.firebase:firebase-bom:33.5.1')
    implementation 'com.google.firebase:firebase-auth'
    implementation 'com.google.firebase:firebase-firestore'
    implementation 'com.google.firebase:firebase-database'
    implementation 'com.google.firebase:firebase-messaging'

    // UI
    implementation 'androidx.recyclerview:recyclerview:1.3.2'

    // Glide
    implementation 'com.github.bumptech.glide:glide:4.16.0'
    annotationProcessor 'com.github.bumptech.glide:compiler:4.16.0'

    // Navigation Component
    implementation 'androidx.navigation:navigation-fragment:2.8.4'
    implementation 'androidx.navigation:navigation-ui:2.8.4'

    // exp4j (za igru Moj broj)
    implementation 'net.objecthunter:exp4j:0.4.8'
}
```

### VAŽNO — google-services plugin pravilo
- **`settings.gradle`** — NEMA `google-services` plugina ovde (klasa-loader bug u AGP 9)
- **root `build.gradle`** — plugins blok sa `alias(libs.plugins.google.services) apply false`
- **`app/build.gradle`** — `alias(libs.plugins.google.services)` bez verzije

---

## 3. Arhitektura paketa

```
com.example.slagalica/
├── ui/                  ← Prezentacioni sloj (Activities, Adapters)
│   ├── auth/            ← LoginActivity, RegisterActivity, ForgotPasswordActivity
│   ├── games/           ← KorakPoKorakActivity, MojBrojActivity, KorakAdapter,
│   │                       GameMenuActivity, KoZnaZnaActivity, SpojniceActivity,
│   │                       SkockoActivity, AsocijacijeActivity
│   ├── main/            ← MainActivity
│   ├── profile/         ← ProfileActivity (stub)
│   ├── chat/            ← (nije implementovano)
│   ├── notifications/   ← NotificationsActivity (stub)
│   └── match/           ← (nije implementovano)
│
├── logic/               ← Poslovni sloj (stateless utility klase)
│   ├── games/           ← KorakPoKorakLogic, MojBrojLogic
│   ├── auth/            ← (nije implementovano)
│   ├── match/           ← (nije implementovano)
│   └── chat/            ← (nije implementovano)
│
├── data/                ← Sloj podataka
│   ├── model/           ← User, KorakPoKorakZadatak, Korak, KorakState,
│   │                       AppNotification, NotificationType
│   ├── repository/      ← UserRepository, GameContentRepository,
│   │                       NotificationRepository, NotificationService
│   └── remote/          ← (prazan, za Firebase helpere)
│
└── util/                ← Helpers
    ├── ShakeDetector.java
    ├── DateUtils.java
    └── NotificationChannelHelper.java
```

### Pravila slojeva (ne sme se kršiti)
1. `ui/` može importovati iz `logic/`, `data/`, `util/`
2. `logic/` može importovati samo iz `data/model/` i `util/`
3. `data/` može importovati samo iz `util/`
4. `util/` ne importuje iz drugih internih paketa

---

## 4. AndroidManifest.xml — registrovane aktivnosti

```xml
<!-- LAUNCHER -->
<activity android:name=".ui.auth.LoginActivity"   android:exported="true"  android:screenOrientation="portrait">
    <intent-filter><action .MAIN/><category .LAUNCHER/></intent-filter>
</activity>

<!-- Registrovane (portrait, exported=false osim Loginа) -->
.ui.main.MainActivity
.ui.auth.RegisterActivity
.ui.auth.ForgotPasswordActivity
.ui.games.KorakPoKorakActivity
.ui.games.MojBrojActivity
.ui.games.GameMenuActivity
.ui.profile.ProfileActivity
.ui.games.KoZnaZnaActivity
.ui.games.SpojniceActivity
.ui.games.SkockoActivity
.ui.games.AsocijacijeActivity
.ui.notifications.NotificationsActivity
```

**Permissions:** `INTERNET`, `ACCESS_NETWORK_STATE`
**Theme:** `Theme.Slagalica` (Material3.DayNight.NoActionBar)

---

## 5. Implementirane klase — detaljan opis

### 5.1 Data modeli (`data/model/`)

#### `User.java`
Firestore POJO za korisničke profile.
```
Polja: uid, email, username, region, avatarUrl (null),
       tokens (default 5), totalStars, currentLeague,
       lastTokenGrantTimestamp, createdAt, avatarFrameType
```
- Javni no-arg konstruktor (obavezan za Firestore)
- Puni konstruktor sa sva 11 polja
- Getteri/setteri za sva polja
- Nema `final` polja

#### `KorakPoKorakZadatak.java`
Firestore POJO za zadatke igre "Korak po korak".
```
Polja: id (String), resenje (String), koraci (List<String>, 7 hintova)
```

#### `Korak.java`
UI model — jedan hint u RecyclerView-u.
```
Polja: redniBroj (int), hint (String), state (KorakState)
```

#### `KorakState.java`
Enum sa 4 stanja: `ZAKLJUCAN`, `OTVOREN`, `POGODJEN`, `PROMASEN`

---

### 5.2 Repozitorijumi (`data/repository/`)

#### `UserRepository.java` ✅ POTPUNO IMPLEMENTOVAN
**Singleton** — `UserRepository.getInstance()`

**Callback interfejsi:**
- `AuthCallback` — `onSuccess(FirebaseUser)` / `onError(String)`
- `UserCallback` — `onSuccess(User)` / `onError(String)`

**Metode:**

| Metoda | Opis |
|--------|------|
| `register(email, username, region, password, cb)` | 1) Proveri jedinstvenost username-a u Firestore → 2) `createUserWithEmailAndPassword` → 3) `sendEmailVerification` → 4) Upiši `User` dokument u `users/{uid}` |
| `login(emailOrUsername, password, cb)` | Ako sadrži `@` → direktna prijava; inače → Firestore lookup po `username` → `signInWithEmailAndPassword` → provera `isEmailVerified()` |
| `changePassword(oldPassword, newPassword, cb)` | Reauthentikacija → `updatePassword` |
| `getCurrentUserData(cb)` | Učitava `User` iz Firestore-a za trenutnog korisnika |
| `logout()` | `mAuth.signOut()` |
| `getCurrentFirebaseUser()` | Vraća `FirebaseUser` ili `null` |
| `isLoggedIn()` | Returns `boolean` |
| `mapAuthError(Exception)` | Mapira Firebase izuzetke na srpske poruke |

**Firebase kolekcija:** `users` — document ID = Firebase Auth UID

#### `GameContentRepository.java` ✅ DELIMIČNO IMPLEMENTOVAN
**Singleton** — `GameContentRepository.getInstance()`

**Callback interfejsi:**
- `KorakLoadCallback` — `onSuccess(KorakPoKorakZadatak)` / `onError(String)`
- `SimpleCallback` — `onSuccess()` / `onError(String)`

**Metode:**

| Metoda | Status |
|--------|--------|
| `getRandomKorakPoKorak(cb)` | ✅ Implementovano — učita sve iz `korakPoKorak` kolekcije, bira random |
| `getRandomKoZnaZna(cb)` | ⚠️ TODO — placeholder, uvek vraća grešku |
| `getRandomMojBroj(cb)` | ⚠️ TODO — placeholder (Moj broj generiše lokalno) |

---

### 5.3 Logic sloj (`logic/games/`)

#### `KorakPoKorakLogic.java` ✅ POTPUNO IMPLEMENTOVAN
Statička final utility klasa.

| Metoda | Opis |
|--------|------|
| `bodoviZaPogodakUKoraku(int korakIndex)` | Formula: `20 - 2 * korakIndex`; indeks 0-6 → 20/18/16/14/12/10/8 bodova; izvan opsega → 0 |
| `bodoviZaPreuzimanje()` | Uvek vraća 5 |
| `tacanOdgovor(String userAnswer, String resenje)` | Case-insensitive + trim + diacritic-insensitive; đ/Đ→d/D pre NFD; NFD normalizacija; regex uklanja combining marks |

**Posebnost:** `đ` se obrađuje ručno pre NFD jer Unicode NFD ne rastavlja `đ` na `d + combining hook`.

#### `MojBrojLogic.java` ✅ POTPUNO IMPLEMENTOVAN
Statička final utility klasa.

**Inner class `IzrazRezultat`:**
```java
public boolean validan;
public double rezultat;
public String greska;
static IzrazRezultat ok(double)     // factory
static IzrazRezultat greska(String) // factory
```

| Metoda | Opis |
|--------|------|
| `evaluate(String izraz, int[] dostupniBrojevi)` | 1) `×→*`, `÷→/` 2) sintaksna validacija 3) multiset validacija 4) exp4j evaluacija |
| `izracunajBodovanje(trazeni, r1, r2, prviJeAktivan)` | Vraća `int[2]` — bodovi za oba igrača |

**Bodovanje logika:**
- Oba tačna → [10, 10]
- Samo jedan tačan → [10, 0] ili [0, 10]
- Oba validna ali netačna → bliži dobija 5; isti → aktivan igrač
- Nevalidan/prazan → 0

---

### 5.4 UI sloj — Auth (`ui/auth/`)

#### `LoginActivity.java` ✅ KT2 — REAL FIREBASE
- `onStart()` — auto-login ako `FirebaseUser != null && isEmailVerified()`
- `onLoginClicked()` → `UserRepository.login()` → Snackbar greška ili `goToMain()`
- `setLoading(boolean)` — pokazuje/skriva `ProgressBar`
- Polja: `TextInputLayout tlEmail`, `TextInputLayout tlPassword`, `ProgressBar pbLoading`

#### `RegisterActivity.java` ✅ KT2 — REAL FIREBASE
- Spinner za region (ExposedDropdownMenu sa predefinisanim regionima)
- `onRegisterClicked()` → lokalna validacija → `UserRepository.register()` → AlertDialog na uspeh
- Sve validacije putem `TextInputLayout.setError()` (ne Toast)

#### `ForgotPasswordActivity.java` ⚠️ KT1 MOCK
**Status: KT1 mock — ima 3 password polja, lokalna validacija, nema Firebase poziva.**
- Treba nadograditi za KT2 sa `UserRepository.changePassword()`

---

### 5.5 UI sloj — Main (`ui/main/`)

#### `MainActivity.java` ✅ IMPLEMENTOVANO
- Toolbar sa 3 chip-a: tokeni, stars, liga
- 8 dugmadi: Profil, Igraj, Rang lista, Prijatelji, Region, Notifikacije, Čet, Odjava
- Odjava → `UserRepository.logout()` → `LoginActivity` sa `FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_NEW_TASK`
- Čet → Toast "(dolazi uskoro)"
- Ostala dugmad → Toast ili prazni Intent-i (placeholder)

---

### 5.6 UI sloj — Games (`ui/games/`)

#### `KorakAdapter.java` ✅ IMPLEMENTOVAN
RecyclerView adapter za 7 hint kartica.
- `otvoriSledeci()` — vraća `int` (indeks otvorenog), menja state na `OTVOREN`, notifikuje adapter
- `oznaciPogodjen(int index)` — menja state na `POGODJEN`
- `oznaciPromasen()` — sve ZAKLJUCAN → PROMASEN

#### `KorakPoKorakActivity.java` ⚠️ KT1 MOCK (NE KT2!)
**VAŽNO: Ova aktivnost je u KT1 stanju — koristi fake hint-ove za "JABUKA", nema Firebase poziv, nema pravu proveru odgovora.**

Trenutno stanje:
- Statički `FAKE_HINTS[]` sa 7 hintova za "JABUKA"
- `CountDownTimer(70_000, 10_000)` — svakih 10s otvara sledeći hint
- `btnGuess` → Toast "(KT1) Provera odgovora"
- `btnGiveUp` → `finish()`
- NEMA: `GameContentRepository.getRandomKorakPoKorak()`, pravu validaciju, `setResult(RESULT_OK, ...)`

**Za KT2 treba implementovati:** Firebase učitavanje, `KorakPoKorakLogic.tacanOdgovor()`, AlertDialogs, `setResult` sa bodovima.

#### `MojBrojActivity.java` ✅ KT2 — POTPUNO IMPLEMENTOVANA
- `EXTRA_BODOVI = "moj_broj_bodovi"`
- `ShakeDetector` → poziva `onStopClicked()`
- `onResume()` → `shakeDetector.start()`, `onPause()` → `shakeDetector.stop()`
- `onDestroy()` → `otkaziTimere()`

**Faze igre:**
- Faza 0: auto-timer 5s → generiše traženi broj [100, 999]
- Faza 1: auto-timer 5s → generiše 6 dostupnih brojeva + pokreće 60s timer
- Faza 2: aktivna igra — igrač unosi izraz, `MojBrojLogic.evaluate()`, 60s countdown

**Generisanje 6 brojeva:** 4 jednocifrena (1-9) + 1 iz `{10,15,20}` + 1 iz `{25,50,75,100}`

---

### 5.7 Util (`util/`)

#### `ShakeDetector.java` ✅ IMPLEMENTOVAN
```java
public interface OnShakeListener { void onShake(); }
```
- SHAKE_THRESHOLD = 12.0f m/s²
- SHAKE_COOLDOWN = 1000ms
- `start()` → pozvati u `onResume()`
- `stop()` → pozvati u `onPause()`
- Implementira `SensorEventListener`, koristi `SensorManager.SENSOR_DELAY_UI`

---

## 6. Firebase kolekcije i putanje

### Firestore kolekcije

| Kolekcija | Document ID | Svrha |
|-----------|-------------|-------|
| `users` | Firebase Auth UID | Korisnički profili |
| `korakPoKorak` | auto-generated | Zadaci za "Korak po korak" |
| `koZnaZna` | auto-generated | Pitanja (Student 2) |

### Realtime Database putanje (planirano, nije implementovano)

| Putanja | Svrha |
|---------|-------|
| `matchmaking/{userId}` | Red čekanja za meč |
| `matches/{matchId}` | Aktivni meč, bodovi |
| `chats/{regionKey}/{messageId}` | Regionalni čet |
| `challenges/{challengeId}` | Izazovi |

---

## 7. Seed skripta za Firestore

**Fajl:** `seed_firestore.js` u root direktorijumu projekta

Puni kolekciju `korakPoKorak` sa 8 zadataka:
JABUKA, BEOGRAD, MOZART, PIRAMIDA, ROBOT, KISA, FUDBAL, OKEAN

**Pokretanje:**
```bash
node seed_firestore.js
```

**Preduslov:** `serviceAccountKey.json` u root direktorijumu (NE commitovati!)

---

## 8. Layouts (XML fajlovi)

| Layout fajl | Aktivnost |
|-------------|-----------|
| `activity_login.xml` | LoginActivity — ScrollView, logo, email/password TextInputLayout, dugmad |
| `activity_register.xml` | RegisterActivity — avatar ImageView, 4 input polja, region dropdown |
| `activity_forgot_password.xml` | ForgotPasswordActivity — 3 password polja |
| `activity_main.xml` | MainActivity — Toolbar sa chipovima, 4×2 GridLayout dugmad |
| `activity_korak_po_korak.xml` | KorakPoKorakActivity — header, ProgressBar, RecyclerView, input |
| `item_korak.xml` | RecyclerView item — MaterialCardView, broj circle, hint tekst |
| `activity_moj_broj.xml` | MojBrojActivity — traženi broj card, 6 number cards, expression input, keyboard |

---

## 9. .gitignore status

Postoji `.gitignore` koji isključuje:
- `*.iml`, `.gradle/`, `local.properties`
- `.idea/`
- `build/`, `app/build`
- `app/google-services.json`
- `serviceAccountKey.json`
- `node_modules/`, `package-lock.json`

---

## 10. Ono što JOŠ NIJE urađeno (prioriteti)

### Visok prioritet (Student 1 odgovornosti)

#### 10.1 KorakPoKorakActivity — KT2 upgrade ⚠️ HITNO
Trenutno je u KT1 mock stanju. Treba:
1. Pozvati `GameContentRepository.getRandomKorakPoKorak()` umesto fake hintova
2. Uvesti multi-round logiku (2 runde × 70s)
3. Implementirati pravu proveru odgovora sa `KorakPoKorakLogic.tacanOdgovor()`
4. AlertDialog za tačan odgovor (sa bodovima), netačan, i istek vremena
5. `setResult(RESULT_OK, intent.putExtra("korak_bodovi", bodovi))` na kraju
6. Pratiti `otvorenIndex` za bodovanje

**Referentna implementacija:** pogledaj `MojBrojActivity.java` kao obrazac.

#### 10.2 ForgotPasswordActivity — KT2 upgrade ⚠️
Zameni mock logiku sa:
```java
UserRepository.getInstance().changePassword(stara, nova, new AuthCallback() {
    void onSuccess(FirebaseUser u) { /* snackbar */ }
    void onError(String msg) { /* TextInputLayout.setError */ }
});
```

#### 10.3 MatchOrchestratorActivity — KO (final)
- Redosled igara: Ko zna zna → Spojnice → Asocijacije → Skočko → Korak po korak → Moj broj
- Čita `matchId` i `userId` iz Intent extras
- Pokreće igre sa `ActivityResultLauncher`
- Prati bodove iz svakog `RESULT_OK` rezultata
- Realtime Database listener na `matches/{matchId}`

#### 10.4 Matchmaking (KO)
- `MatchmakingActivity` — stavlja korisnika u `matchmaking/{userId}`, sluša dok drugi igrač ne uđe

#### 10.5 Regionalni čet (KO — Funkcionalnost 8)
- `ChatActivity`, `ChatAdapter`
- Realtime Database: `chats/{regionKey}/{messageId}`
- `ChatRepository.java`

#### 10.6 Izazovi/Challenges (KO — Funkcionalnost 9)
- `ChallengeActivity`
- Realtime Database: `challenges/{challengeId}`
- Solo igra (1 runda za Korak po korak i Moj broj)

### Srednji prioritet

#### 10.7 ProfileActivity
- Prikazati username, email, region, stars, tokens, liga
- Pozvati `UserRepository.getCurrentUserData()`

#### 10.8 MainActivity — popuniti navigaciju
- Dugmici osim Odjave trenutno prikazuju Toast ili su prazni
- Svezati ih sa odgovarajućim aktivnostima kada budu implementovane

#### 10.9 Validation logika (`logic/auth/`)
- `AuthValidation.java` — pravila za email, username, lozinka
- Trenutno je validacija direktno u aktivnostima

---

## 11. Konvencije za novi kod

### Imenovanje
- **Activities:** `XxxActivity.java`
- **Layouts:** `activity_snake_case.xml`, `item_snake_case.xml`
- **View IDs:** `etXxx` (EditText), `tvXxx` (TextView), `btnXxx` (Button), `ivXxx` (ImageView), `rvXxx` (RecyclerView), `pbXxx` (ProgressBar), `tlXxx` (TextInputLayout)
- **Strings:** snake_case u `strings.xml`, srpski tekst
- **Konstantne:** `UPPER_SNAKE_CASE`

### Obavezna pravila
1. **Sve user-facing string-ove** staviti u `res/values/strings.xml`, nikad hardcode u Java
2. **Form validacija** → `TextInputLayout.setError("poruka")`, nikad Toast
3. **Firebase listeneri** → obavezno `removeValue` / `removeEventListener` u `onDestroy()`
4. **CountDownTimer** → `cancel()` u `onDestroy()`
5. **ShakeDetector** → `start()` u `onResume()`, `stop()` u `onPause()`
6. **Repozitorijumi su singletoni** — `getInstance()`, nikad direktno instancirati Firebase u aktivnosti
7. **Callback pattern** — uvek interfejsi, nikad vraćati `Task<>` iz repozitorijuma
8. **Sve aktivnosti registrovati** u `AndroidManifest.xml` sa `screenOrientation="portrait"`

---

## 12. Najčešće greške koje treba izbegavati

1. **Kotlin kod** — samo Java
2. **`rs.ftn.slagalica`** package — samo `com.example.slagalica`
3. **`google-services` u `settings.gradle`** — builds puca
4. **Verzioni broj plugina u `app/build.gradle`** — verzije idu u `libs.versions.toml`
5. **View Binding / Data Binding / LiveData / ViewModel** — ne koriste se
6. **`FirebaseStorage`** — nije omogućen, ne koristiti
7. **`AsyncTask`** — zastareo, koristiti `Executors.newSingleThreadExecutor()` + `runOnUiThread()`
8. **`compileSdk` sa version blokovima** — uvek plain integer: `compileSdk 35`

---

## 13. Primer toka za sledeću sesiju

Ako nastavljaš sa KT2 upgrade za `KorakPoKorakActivity`, uradi:

```
1. Pročitaj: app/src/main/java/com/example/slagalica/ui/games/KorakPoKorakActivity.java
2. Pročitaj: app/src/main/java/com/example/slagalica/logic/games/KorakPoKorakLogic.java  
3. Pročitaj: app/src/main/java/com/example/slagalica/data/repository/GameContentRepository.java
4. Pročitaj: app/src/main/java/com/example/slagalica/data/model/KorakPoKorakZadatak.java
5. Pročitaj: app/src/main/java/com/example/slagalica/ui/games/MojBrojActivity.java (kao obrazac)
```

Zatim implementiraj novu verziju `KorakPoKorakActivity.java` sa:
- `EXTRA_BODOVI = "korak_bodovi"`
- `MAX_ROUNDS = 2`, `ROUND_DURATION_MS = 70_000`, `HINT_INTERVAL_MS = 10_000`
- `ucitajZadatak()` → `GameContentRepository.getRandomKorakPoKorak()`
- `pokreniRundu()` → resetuj adapter, otvori 1. hint odmah, pokreni CountDownTimer
- `onGuessClicked()` → `KorakPoKorakLogic.tacanOdgovor()`, AlertDialog
- `setResult(RESULT_OK, intent.putExtra(EXTRA_BODOVI, ukupnoBodova))`

---

## 14. Pokretanje aplikacije

```powershell
# Build (PowerShell)
.\gradlew assembleDebug

# Install na uređaj/emulator
.\gradlew installDebug

# Clean ako ima problema
.\gradlew clean

# Logovi
adb logcat -s "Slagalica:*"
```

**Preduslov:** `app/google-services.json` mora biti u `app/` folderu (NE commitovati).

