export type SymbolPlacement = 'BEFORE' | 'AFTER';

export interface UserPreference {
    currency: string;
    groupSeparator: string;
    decimalSeparator: string;
    symbolPlacement: SymbolPlacement;
    currencyDecimals: number;
    truncateTrailingZeros: boolean;
    dateFormat: string;
}

// gli stessi default del backend (UserPreference.java). Servono perché
// la formattazione deve funzionare anche prima che la GET sia tornata
// senza al primo render le liste mostrerebbero nuermi grezzi
export const DEFAULT_PREFERENCE: UserPreference = {
    currency: 'EUR',
    groupSeparator: '.',
    decimalSeparator: ',',
    symbolPlacement: 'AFTER',
    currencyDecimals: 2,
    truncateTrailingZeros: false,
    dateFormat: 'dd/MM/yyyy',
};
