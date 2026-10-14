// Widget Google Translate (solo italiano -> inglese). Caricato come file esterno e non inline,
// cosi' la CSP non richiede un secondo hash. Se il browser e' in inglese, la traduzione parte da sola
// (cookie googtrans) senza che l'utente tocchi il selettore.
(function () {
  try {
    var lang = (navigator.language || '').toLowerCase();
    if (lang.indexOf('en') === 0 && document.cookie.indexOf('googtrans=') === -1) {
      document.cookie = 'googtrans=/it/en; path=/';
    }
  } catch (e) { /* cookie bloccati: resta il selettore manuale */ }

  window.googleTranslateElementInit = function () {
    new google.translate.TranslateElement(
      { pageLanguage: 'it', includedLanguages: 'en', layout: google.translate.TranslateElement.InlineLayout.SIMPLE, autoDisplay: false },
      'google_translate_element'
    );
  };
})();
