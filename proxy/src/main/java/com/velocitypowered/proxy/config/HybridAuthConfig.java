package com.velocitypowered.proxy.config;

/**
 * Główna klasa przechowująca ustawienia do trybu hybrydowego:
 * - Czy weryfikacja premium jest w ogóle włączona.
 * - Czy gracze premium pomijają logowanie offline.
 * - Konfiguracja timeoutów i cache do zapytań Mojang.
 * - Ewentualne endpointy, jeśli chcesz je modyfikować (np. w przypadku
 *   alternatywnych usług).
 */
public class HybridAuthConfig {

    /**
     * Czy w ogóle używać weryfikacji premium (zapytania do Mojang).
     * Jeśli false, cały tryb hybrydowy można pominąć.
     */
    private boolean enablePremiumVerification = true;

    /**
     * Czy gracze premium mają automatycznie omijać /register i /login?
     * (O ile w ogóle używamy takiego systemu offline).
     */
    private boolean skipLoginForPremium = false;

    /**
     * Czas (w milisekundach) przechowywania wyniku weryfikacji w pamięci podręcznej.
     * Jeśli 0, to w ogóle nie cache’ujemy.
     */
    private long sessionCacheDurationMs = 300_000L; // np. 5 minut

    /**
     * URL endpointu do weryfikacji premium. Domyślnie to:
     * https://sessionserver.mojang.com/session/minecraft/hasJoined
     */
    private String sessionServerUrl = "https://sessionserver.mojang.com/session/minecraft/hasJoined";

    // --- Przykładowe gettery/settery ---

    public boolean isEnablePremiumVerification() {
        return enablePremiumVerification;
    }

    public void setEnablePremiumVerification(boolean enablePremiumVerification) {
        this.enablePremiumVerification = enablePremiumVerification;
    }

    public boolean isSkipLoginForPremium() {
        return skipLoginForPremium;
    }

    public void setSkipLoginForPremium(boolean skipLoginForPremium) {
        this.skipLoginForPremium = skipLoginForPremium;
    }

    public long getSessionCacheDurationMs() {
        return sessionCacheDurationMs;
    }

    public void setSessionCacheDurationMs(long sessionCacheDurationMs) {
        this.sessionCacheDurationMs = sessionCacheDurationMs;
    }

    public String getSessionServerUrl() {
        return sessionServerUrl;
    }

    public void setSessionServerUrl(String sessionServerUrl) {
        this.sessionServerUrl = sessionServerUrl;
    }

    /**
     * Metoda przykładowa, do wczytywania konfiguracji z pliku
     * (w tej chwili pusta, do zaimplementowania we własnym zakresie).
     */
    public void loadConfig(/* np. ścieżka do pliku */) {
        // TODO: Wczytaj parametry z pliku / velocity.toml / innego źródła.
        //   1) Otwórz plik,
        //   2) Parsuj wartości,
        //   3) Ustaw pola w tej klasie.
    }

    /**
     * Przykładowy konstruktor domyślny – ewentualnie do wczytania
     * wartości domyślnych. Można go zastąpić ładowaniem z pliku
     * w `loadConfig()`.
     */
    public HybridAuthConfig() {
        // można ustawić tu wartości domyślne
    }

    @Override
    public String toString() {
        return "HybridAuthConfig{" +
                "enablePremiumVerification=" + enablePremiumVerification +
                ", skipLoginForPremium=" + skipLoginForPremium +
                ", sessionCacheDurationMs=" + sessionCacheDurationMs +
                ", sessionServerUrl='" + sessionServerUrl + '\'' +
                '}';
    }
}
