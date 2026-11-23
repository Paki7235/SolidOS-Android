# SolidOS per Android

SolidOS Mobile su Android aperto (AOSP, come /e/OS e GrapheneOS): la Home, i widget e le app
di SolidOS, con lo stesso stile «Vetro» e lo stesso Solid ID del PC.

- **app/** — l'app «SolidOS»: la Home (launcher), la Libreria app e gli Aggiornamenti.
- **Aggiornamenti** — ogni build su GitHub (Actions › Android) pubblica una nuova versione nelle
  Releases con `versione.json`. Sul telefono: tieni premuto lo sfondo › Aggiornamenti di SolidOS
  › «Aggiorna ora». Android installa solo versioni firmate con la chiave di SolidOS.

## Provarlo sul Mac

Con l'emulatore Android (Apple Silicon):

```sh
sh strumenti/emulatore.sh        # avvia il telefono virtuale «SolidOS»
sh strumenti/installa.sh         # costruisce e installa l'app nell'emulatore
```

Progetto open source e gratuito.
