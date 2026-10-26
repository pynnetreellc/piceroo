# Piceroo

*Bounce from pic to pic.*

A game for clearing out your phone's photos, with a kangaroo mascot. Piceroo sorts your photos into decks:

- burst shots taken seconds apart
- screenshots
- chat and download images
- big videos
- one deck per day

In a day deck you swipe left to trash a photo and right to keep it. In a burst deck you tap the shot you want to keep and the rest go. Each finished deck earns points and keeps your daily streak going.

## How deleting works

- **Trash, not instant delete.** At the end of each deck, Android asks you once to move that deck's picks to the phone's trash. Trashed items can be restored for 30 days.
- **Google Photos keeps its cloud copies.** Backup only copies from the phone to the cloud. Deleting on the phone doesn't remove the backed-up copy. To delete those, use **Share cloud cleanup list** on the home screen. It lists everything you trashed, grouped by date.
- **Kept photos don't come back.** Once you finish a deck, the photos you kept won't appear in future decks.

## Building

You don't need Android Studio. Every push to `main` triggers GitHub Actions, which builds the APK and posts it as a release.

1. On your phone, open `https://github.com/<you>/piceroo/releases/latest`.
2. Tap **Piceroo.apk**.
3. Allow your browser to install apps when Android asks.

Every build is signed with the same key, `app/piceroo-signing.p12`. That's why a new build installs over the old one and your stats are kept. The key is only for installing builds yourself. Don't reuse it for a Play Store release.

Requires Android 11 or newer.

## Code map

| File | What it does |
|---|---|
| `data/MediaRepository.kt` | Reads every photo and video from Android's MediaStore |
| `data/Clusterer.kt` | Sorts the photos and videos into decks |
| `data/GameStore.kt` | Points, streaks, kept photos, and the cloud cleanup list |
| `AppViewModel.kt` | App state and which screen is showing |
| `MainActivity.kt` | Permissions and the trash confirmation |
| `ui/PlayScreen.kt` | The swipe cards, the burst picker, and the end-of-round summary |

## Credits

The kangaroo is by [Delapouite](https://delapouite.com) from [game-icons.net](https://game-icons.net/1x1/delapouite/kangaroo.html), used under [CC BY 3.0](https://creativecommons.org/licenses/by/3.0/). It's used as the app icon and the emblems inside the app.
