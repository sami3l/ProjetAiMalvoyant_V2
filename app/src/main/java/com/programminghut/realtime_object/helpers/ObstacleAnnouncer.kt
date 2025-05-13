package com.programminghut.realtime_object.helpers

import android.content.Context
import android.util.Log

class ObstacleAnnouncer(
    private val context: Context,
    private val tts: TTSHelper,
    private val lang: String
) {

    private val labelMap = mapOf(
        "person" to mapOf("fr" to "personne", "darija" to "shī wāḥed"),
        "car" to mapOf("fr" to "voiture", "darija" to "tonobil"),
        "truck" to mapOf("fr" to "camion", "darija" to "kamiyo"),
        "bicycle" to mapOf("fr" to "velo", "darija" to "bisikla"),
        "motorcycle" to mapOf("fr" to "moto", "darija" to "moto"),
        "bus" to mapOf("fr" to "bus", "darija" to "tobis"),
        "bench" to mapOf("fr" to "banc", "darija" to "korsi"),
        "traffic light" to mapOf("fr" to "feu", "darija" to "lampa dial traffic"),
        "stop sign" to mapOf("fr" to "panneau stop", "darija" to "stop"),
        "parking meter" to mapOf("fr" to "parcmetre", "darija" to "compteur parking"),
        "fire hydrant" to mapOf("fr" to "borne incendie", "darija" to "robine dial lma"),
        "train" to mapOf("fr" to "train", "darija" to "tran"),
        "boat" to mapOf("fr" to "bateau", "darija" to "barko"),
        "bird" to mapOf("fr" to "oiseau", "darija" to "tir"),
        "cat" to mapOf("fr" to "chat", "darija" to "qet"),
        "dog" to mapOf("fr" to "chien", "darija" to "kelb"),
        "backpack" to mapOf("fr" to "sac a dos", "darija" to "sac"),
        "umbrella" to mapOf("fr" to "parapluie", "darija" to "mdella"),
        "handbag" to mapOf("fr" to "sac à main", "darija" to "shenkila"),
        "tie" to mapOf("fr" to "cravate", "darija" to "kravata"),
        "suitcase" to mapOf("fr" to "valise", "darija" to "valiza"),
        "frisbee" to mapOf("fr" to "frisbee", "darija" to "frisbee"),
        "skis" to mapOf("fr" to "skis", "darija" to "ski"),
        "snowboard" to mapOf("fr" to "snowboard", "darija" to "snowboard"),
        "sports ball" to mapOf("fr" to "ballon", "darija" to "kōra"),
        "kite" to mapOf("fr" to "cerf-volant", "darija" to "ṭiara"),
        "baseball bat" to mapOf("fr" to "batte de baseball", "darija" to "maḍreba"),
        "baseball glove" to mapOf("fr" to "gant de baseball", "darija" to "gant"),
        "skateboard" to mapOf("fr" to "skateboard", "darija" to "skate"),
        "surfboard" to mapOf("fr" to "planche de surf", "darija" to "planche"),
        "tennis racket" to mapOf("fr" to "raquette de tennis", "darija" to "rākīṭ"),
        "bottle" to mapOf("fr" to "bouteille", "darija" to "qer'a"),
        "wine glass" to mapOf("fr" to "verre de vin", "darija" to "kas dial shrāb"),
        "cup" to mapOf("fr" to "tasse", "darija" to "kās"),
        "fork" to mapOf("fr" to "fourchette", "darija" to "fershīṭa"),
        "knife" to mapOf("fr" to "couteau", "darija" to "mūs"),
        "spoon" to mapOf("fr" to "cuillère", "darija" to "m'alqa"),
        "bowl" to mapOf("fr" to "bol", "darija" to "zlāfa"),
        "banana" to mapOf("fr" to "banane", "darija" to "banan"),
        "apple" to mapOf("fr" to "pomme", "darija" to "teffāḥa"),
        "sandwich" to mapOf("fr" to "sandwich", "darija" to "sandwich"),
        "orange" to mapOf("fr" to "orange", "darija" to "limouna"),
        "broccoli" to mapOf("fr" to "brocoli", "darija" to "brocoli"),
        "carrot" to mapOf("fr" to "carotte", "darija" to "khizzou"),
        "hot dog" to mapOf("fr" to "hot dog", "darija" to "hot dog"),
        "pizza" to mapOf("fr" to "pizza", "darija" to "pizza"),
        "donut" to mapOf("fr" to "donut", "darija" to "donut"),
        "cake" to mapOf("fr" to "gâteau", "darija" to "gato"),
        "chair" to mapOf("fr" to "chaise", "darija" to "kursi"),
        "couch" to mapOf("fr" to "canapé", "darija" to "sdari"),
        "potted plant" to mapOf("fr" to "plante en pot", "darija" to "nebta"),
        "bed" to mapOf("fr" to "lit", "darija" to "namosia"),
        "dining table" to mapOf("fr" to "table", "darija" to "ṭabla"),
        "toilet" to mapOf("fr" to "toilette", "darija" to "toilet"),
        "tv" to mapOf("fr" to "télévision", "darija" to "tlfaza"),
        "laptop" to mapOf("fr" to "ordinateur portable", "darija" to "pc"),
        "mouse" to mapOf("fr" to "souris", "darija" to "fār"),
        "remote" to mapOf("fr" to "télécommande", "darija" to "telecommande"),
        "keyboard" to mapOf("fr" to "clavier", "darija" to "clavier"),
        "cell phone" to mapOf("fr" to "téléphone", "darija" to "tilifoun"),
        "microwave" to mapOf("fr" to "micro-ondes", "darija" to "microonde"),
        "oven" to mapOf("fr" to "four", "darija" to "forn"),
        "toaster" to mapOf("fr" to "grille-pain", "darija" to "grille-pain"),
        "sink" to mapOf("fr" to "évier", "darija" to "pisine"),
        "refrigerator" to mapOf("fr" to "réfrigérateur", "darija" to "tllaja"),
        "book" to mapOf("fr" to "livre", "darija" to "ktāb"),
        "clock" to mapOf("fr" to "horloge", "darija" to "magana"),
        "vase" to mapOf("fr" to "vase", "darija" to "vaz"),
        "scissors" to mapOf("fr" to "ciseaux", "darija" to "mqess"),
        "teddy bear" to mapOf("fr" to "ours en peluche", "darija" to "dob"),
        "hair drier" to mapOf("fr" to "sèche-cheveux", "darija" to "sechoir"),
        "toothbrush" to mapOf("fr" to "brosse à dents", "darija" to "fershāt snān"),
        "unknown" to mapOf("fr" to "objet inconnu", "darija" to "shi haja")
    )

    private val lastSpoken = mutableMapOf<String, Long>()
    private val cooldownMs = 5000L  // Réduire à 5 secondes pour être plus réactif

    /**
     * Annonce un obstacle détecté avec sa distance
     * @param label L'étiquette de l'obstacle détecté
     * @param distance La distance estimée en mètres
     */
    fun announce(label: String, distance: Float) {
        // Ne pas annoncer si l'objet est au-delà de 10 mètres
        if (distance > 10f) return

        val now = System.currentTimeMillis()
        val lastTime = lastSpoken[label] ?: 0L

        // Vérifier si l'obstacle a déjà été annoncé récemment
        if ((now - lastTime) < cooldownMs) return

        // Formater la distance à une décimale
        val distanceFormatted = "%.1f".format(distance)

        // Obtenir la traduction de l'étiquette ou utiliser l'étiquette brute
        val translated = labelMap[label]?.get(lang) ?: label

        // Créer le message selon la langue
        val message = if (lang == "fr") {
            "$translated a $distanceFormatted metres"
        } else {
            "hder, kayn $translated f $distanceFormatted mitr"
        }

        // Annoncer le message
        tts.speak(message)

        // Enregistrer le moment de l'annonce
        lastSpoken[label] = now

        // Journaliser l'annonce
        Log.d("ANNOUNCER", "Announced: $message")
    }
}