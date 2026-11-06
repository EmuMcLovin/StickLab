package com.sticklab.app

import android.content.Context

// Wechselt am Odin auf ein gespeichertes Profil, ohne Bildschirm-Anzeige (Phase 5d). Benutzt von "Use" in der App
// (ProfilesController) und von der Kachel (ProfileTileService), damit beide genau gleich setzen:
// alle 5 Werte über ProfileApplier (U1, nach einem Fehler weiter, U2), danach Profil aktiv, und – nur wenn alles
// gesetzt ist – der Lichtblitz in der Profilfarbe (L1, L3, K2). Die Kurven des Profils gelten danach als eingestellt
// (nur eigene Datei, am Odin ändert das nichts)
class ProfileSwitcher(private val context: Context, private val applier: ProfileApplier) {

    private val store = ProfileStore(context)

    // Ergebnis: Bericht je Teil (für Statuszeile bzw. Kachel) und ob "aktiv" gespeichert werden konnte
    data class Outcome(val report: ApplyReport, val stored: Boolean) {
        val allSet: Boolean get() = report.firstFailed == null
    }

    fun switchTo(slot: Int, profile: Profile): Outcome {
        val report = applier.apply(ProfileTargets.of(profile))
        val curvesStored = CurveStore(context).save(profile.curves)
        val stored = store.setActiveSlot(slot) && curvesStored
        val outcome = Outcome(report, stored)
        if (outcome.allSet) LightFlash.start(context, profile.color)
        return outcome
    }
}
