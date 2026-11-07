package aradexperience.enums;

/**
 * O acțiune alocată unui voluntar (T18): LOCKED până cu 30 de minute înainte de start, OPEN de atunci până la sfârșitul zilei
 * acțiunii (ora Aradului), ENDED după. Scanarea merge doar OPEN.
 */
public enum VolunteerActionState {
    LOCKED, OPEN, ENDED
}
