/**
 * seed_firestore.js
 * Puni Firestore kolekciju "korakPoKorak" sa zadacima i kreira test korisnike.
 *
 * Pokretanje (iz root foldera projekta):
 *   npm install firebase-admin   ← samo prvi put
 *   node seed_firestore.js
 *
 * Preduslov: serviceAccountKey.json mora biti u istom folderu kao ova skripta.
 *
 * Kreira test nalog:
 *   Email:    korisnik@gmail.com
 *   Lozinka:  Korisnik123
 */

const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccountKey.json');

admin.initializeApp({
    credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

// -------------------------------------------------------------------------
// Podaci za seed
// -------------------------------------------------------------------------

const zadaci = [
    {
        resenje: "JABUKA",
        koraci: [
            "Isaac Newton",
            "Crvena ili zelena",
            "Raste na drvetu",
            "Voće",
            "Adam i Eva",
            "U pitama i tortama",
            "JA_UKA"
        ]
    },
    {
        resenje: "BEOGRAD",
        koraci: [
            "Tvrđava Kalemegdan",
            "Na ušću Save i Dunava",
            "Glavni grad Srbije",
            "Balkanski grad",
            "Beograđani",
            "BEO aerodrom",
            "BE_GRAD"
        ]
    },
    {
        resenje: "MOZART",
        koraci: [
            "Rođen 1756.",
            "Čudo od deteta",
            "Bečki kompozitor",
            "Klasična muzika",
            "Requiem",
            "Čarobna frula",
            "MO_ART"
        ]
    },
    {
        resenje: "PIRAMIDA",
        koraci: [
            "Giza",
            "Faraoni",
            "Pustinja",
            "Drevni Egipat",
            "Četverostrana građevina",
            "Turistička atrakcija Egipta",
            "PI_AMIDA"
        ]
    },
    {
        resenje: "ROBOT",
        koraci: [
            "Veštačka inteligencija",
            "Metal i žice",
            "Isaac Asimov",
            "Fabrika automobila",
            "R2-D2",
            "Programiran",
            "RO_OT"
        ]
    },
    {
        resenje: "KISA",
        koraci: [
            "Nimbostratus",
            "Hidrološki ciklus",
            "Mokri trotoari",
            "Oblaci i vode",
            "Duga posle nje",
            "Kišobran",
            "KI_A"
        ]
    },
    {
        resenje: "FUDBAL",
        koraci: [
            "FIFA World Cup",
            "11 igrača po timu",
            "Lionel Messi",
            "Zeleni teren",
            "Golman",
            "Crno-bela lopta",
            "FU_BAL"
        ]
    },
    {
        resenje: "OKEAN",
        koraci: [
            "Mariansko korito",
            "70% Zemlje",
            "Pacifik i Atlantik",
            "Slana voda",
            "Kitovi i ajkule",
            "Brodovi i talasi",
            "O_EAN"
        ]
    }
];

// -------------------------------------------------------------------------
// Test korisnici
// -------------------------------------------------------------------------

const testKorisnici = [
    {
        email: 'korisnik@gmail.com',
        password: 'Korisnik123',
        username: 'korisnik',
        region: 'Beograd'
    },
    {
        email: 'korisnik2@gmail.com',
        password: 'Korisnik123',
        username: 'korisnik2',
        region: 'Novi Sad'
    }
];

// -------------------------------------------------------------------------
// Seed funkcija
// -------------------------------------------------------------------------

async function kreirajKorisnika(podaci) {
    try {
        // Provjeri da li korisnik već postoji
        let userRecord;
        try {
            userRecord = await admin.auth().getUserByEmail(podaci.email);
            console.log(`  ↳ Već postoji: ${podaci.email} (uid: ${userRecord.uid})`);
        } catch (e) {
            // Ne postoji — kreiraj ga
            userRecord = await admin.auth().createUser({
                email: podaci.email,
                password: podaci.password,
                emailVerified: true   // odmah verifikovan, nema potrebe za email linkom
            });
            console.log(`  ↳ Kreiran: ${podaci.email} (uid: ${userRecord.uid})`);
        }

        // Upiši/ažuriraj Firestore dokument
        const now = Date.now();
        await db.collection('users').doc(userRecord.uid).set({
            uid: userRecord.uid,
            email: podaci.email,
            username: podaci.username,
            region: podaci.region,
            avatarUrl: null,
            tokens: 5,
            totalStars: 0,
            currentLeague: 0,
            lastTokenGrantTimestamp: now,
            createdAt: now,
            avatarFrameType: 0
        }, { merge: true });

        return true;
    } catch (error) {
        console.error(`  ✗ Greška: ${error.message}`);
        return false;
    }
}

async function seed() {
    // ── Zadaci za Korak po korak ──────────────────────────────────────────
    console.log('Dodavanje zadataka za "Korak po korak"...\n');
    let uspesno = 0;
    for (const zadatak of zadaci) {
        try {
            await db.collection('korakPoKorak').add(zadatak);
            console.log(`✓ Dodat: ${zadatak.resenje}`);
            uspesno++;
        } catch (error) {
            console.error(`✗ Greška pri dodavanju "${zadatak.resenje}":`, error.message);
        }
    }
    console.log(`\nUkupno zadataka: ${uspesno}/${zadaci.length}`);

    // ── Test korisnici ────────────────────────────────────────────────────
    console.log('\nKreiranje test korisnika...\n');
    for (const k of testKorisnici) {
        console.log(`Korisnik: ${k.email} / ${k.password}`);
        await kreirajKorisnika(k);
    }

    console.log('\nSeed završen!');
    process.exit(0);
}

seed();

