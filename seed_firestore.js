/**
 * seed_firestore.js
 * Puni Firestore kolekciju "korakPoKorak" sa zadacima za igru "Korak po korak".
 *
 * Pokretanje (iz root foldera projekta):
 *   npm install firebase-admin   ← samo prvi put
 *   node seed_firestore.js
 *
 * Preduslov: serviceAccountKey.json mora biti u istom folderu kao ova skripta.
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
// Seed funkcija
// -------------------------------------------------------------------------

async function seed() {
    console.log('Pokrenuta seed skripta za kolekciju "korakPoKorak"...\n');

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

    console.log(`\nUkupno dodatih dokumenata: ${uspesno}/${zadaci.length}`);
    console.log('Seed završen!');
    process.exit(0);
}

seed();

