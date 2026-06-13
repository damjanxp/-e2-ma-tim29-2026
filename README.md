# Slagalica — Mobilna aplikacija

Android kviz aplikacija po uzoru na TV emisiju Slagalica. Multiplayer 1v1 igre u realnom vremenu.

## Preduslovi

- Android Studio (Hedgehog ili noviji)
- JDK 17+
- Android uređaj ili emulator (min API 26)
- Aktivna internet konekcija (Firebase)

## Pokretanje

1. **Kloniraj repozitorijum**
   ```bash
   git clone https://github.com/damjanxp/-e2-ma-tim29-2026.git
   cd -e2-ma-tim29-2026
   ```

2. **Dodaj Firebase konfiguraciju**  
   Postavi fajl `google-services.json` u folder `app/`.  
   Fajl se nalazi na Firebase konzoli → Project settings → Your apps → Android app.

3. **Pokreni u Android Studiju**  
   Otvori folder projekta → `Run ▶` ili `Shift+F10`.

## Seed baze (opcionalno)

Za popunjavanje Firestore baze zadacima i kreiranje test naloga:

```bash
npm install firebase-admin
# Postavi serviceAccountKey.json u root folder projekta
node seed_firestore.js
```

**Test nalog:** `korisnik@gmail.com` / `Korisnik123`

## Testiranje multiplayera

Za testiranje 1v1 igara potrebna su **dva uređaja ili dva emulatora** sa različitim nalozima. Oba igrača kliknu "Igraj" i sistem ih automatski spoji.

## Arhitektura

- **Prezentacioni sloj:** Activities (ui/)
- **Poslovna logika:** Logic klase (logic/)
- **Podaci:** Repositories + Firebase (data/)
