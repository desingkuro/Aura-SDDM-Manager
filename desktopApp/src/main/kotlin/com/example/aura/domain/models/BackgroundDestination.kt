package com.example.aura.domain.models

/**
 * Pantallas a las que se aplica un fondo en el tema SDDM actual.
 */
enum class BackgroundDestination(val label: String, val targetDescription: String) {
    LOGIN("Login", "la pantalla de login"),
    LOCK("Bloqueo", "la pantalla de bloqueo"),
    BOTH("Ambos", "las pantallas de login y bloqueo"),
}
