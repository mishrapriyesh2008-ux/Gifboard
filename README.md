# GifBoard — Android Custom Keyboard with GIF/Clip Sending

A Gboard-style custom keyboard (IME) for Android with a media tab that searches
short looping clips (GIFs/MP4s, including ones **with audio**), plus Recents
and Favorites — built in Kotlin with Android Studio / Gradle.

## Why "GIF with sound" is actually an MP4/WebM

True GIF is a silent image format — there's no audio track, ever. What Gboard,
Tenor, and GIPHY call "GIFs" with sound are really short looping **MP4/WebM**
clips. This project follows that same convention: search results carry both a
silent preview and (where available) an MP4/WebM with audio, marked with a
speaker badge in the grid.

## How "sending" actually works

Android does **not** let a keyboard force-embed playable video into an
arbitrary text field. There are exactly two real delivery paths, and the app
picks automatically:

1. **`InputConnection.commitContent()`** (Android 7.1+) — if the focused app's
   text field advertises support for rich content (Messages, Gmail, and many
   chat apps do), the clip is committed directly into the conversation. This
   is the same mechanism Gboard uses for its own GIF/sticker keyboard.
2. **Share-sheet fallback** — if the field doesn't support rich content, the
   app downloads the clip and opens the standard Android share sheet so the
   user can route it manually. The user sees a small toast explaining why.

There is no other path. Any "keyboard" that claims to force video into every
app is either lying or relying on undocumented OEM behavior that breaks
across devices.

## Project structure

```
app/src/main/java/com/example/gifkeyboard/
  ime/      — InputMethodService, QWERTY builder, RecyclerView adapters
  media/    — Retrofit API client, repository, bundled sample-clip fallback
  data/     — MediaItem model, Room entities/DAO/database (recents/favorites)
  util/     — MediaSender: the commitContent → share-sheet decision tree
  ui/       — SetupActivity (the launcher screen, deep-links to system settings)
```

## Running it

1. Open the project root in Android Studio (Gradle sync will pull dependencies).
2. Run the app — it launches `SetupActivity`.
3. Tap **Enable Keyboard** → toggle GifBoard on in system settings.
4. Switch to GifBoard from any text field (long-press the globe/keyboard icon,
   or use the system input method picker).

**No API key is required to try it.** With no key configured, the media tab
serves a small set of bundled Creative-Commons sample clips so search,
categories, recents, and favorites all work end-to-end out of the box.

## Adding a real GIF provider (Tenor or GIPHY)

The network layer (`MediaProviderApi`, `MediaRepository`) is written against
Tenor's v2 response shape. To go live:

1. Get a free key from [Tenor's developer portal](https://tenor.com/gifapi/documentation).
2. In `app/build.gradle.kts`, replace the empty string:
   ```kotlin
   buildConfigField("String", "MEDIA_PROVIDER_API_KEY", "\"YOUR_KEY_HERE\"")
   ```
   (Better: read it from `local.properties` or an environment variable so it's
   never committed to source control.)
3. Rebuild. `MediaRepository` automatically switches from sample clips to live
   search/trending results — no other code changes needed.

To use GIPHY instead, only `MediaProviderApi`'s DTOs and
`MediaRepository.toMediaItem()`'s field mapping need to change; everything
above that layer (UI, IME, send pipeline) is provider-agnostic.

## What's implemented

- Full QWERTY letter layer with shift / caps-lock (single-tap shift, double-tap caps)
- Symbol layer (`?123`) — digits, punctuation, common symbols (@#$%&…)
- Extended symbol layer (`=\\<`) — maths, brackets, currencies, special chars
- ABC / ?123 / =\\< switcher keys wired up on all layers
- Long-press any vowel key (A E I O U) or N, C, S to get an accent popup
  with accented variants (à á â ã ä å æ …) — single tap to insert
- GIF/clip media tab with search, 5 category chips, Recents, Favorites
- commitContent send pipeline with share-sheet fallback
- Room-persisted recent/favorite history

## Remaining gaps (good next steps to extend)

- No swipe-typing (gesture typing) or autocorrect / word suggestions
- Launcher icons are placeholder generated circles — swap in real artwork
- `minSdk 24` means `commitContent` (needs API 25+) silently falls back to
  the share sheet on Android 7.0 devices specifically; all other API levels work
