# Tool catalog: tools to test light-web against

The Light Phone tools we know of, used as the test set for the API scanner and for checking how
close light-web is to "drop in a repo". Compiled 2026-10-10.

## How this list was built

Every Light SDK tool has a `lighttool.toml`, so a GitHub code search for that file finds public
tools:

```bash
gh search code --filename lighttool.toml --limit 100 --json repository \
  -q '.[].repository.nameWithOwner' | sort -u
gh search repos "lightos tool"   # catches a few the code search missed
```

Code search only indexes default branches and misses some repos, so we also use
[awesome-light](https://awesome-light.garado.dev) ([repo](https://github.com/garado/awesome-light)),
a community directory of Light Phone apps and tools. Each app is one
`content/apps/<owner>_<repo>/index.md` with front matter (`repo`, `category`, `light_sdk`,
`editor_pick`). On 2026-10-11 it listed 83 entries. Each repo was classified by its file tree:
36 have a `lighttool.toml` (SDK tools), 38 have an `AndroidManifest.xml` but no `lighttool.toml`
(plain Android apps), and 9 have neither. The directory's own `light_sdk` field is blank for many
entries, so we use the file tree instead. Light's official community Tool Library isn't covered:
we haven't found a public listing of it.

The "Likely API areas" column is a guess from each repo's description. Replace it with the
scanner's output once each tool has been scanned.

## Already in light-web, or local

| Tool | Where | Status |
|---|---|---|
| light-training | `~/Projects/light-training` | runs in light-web |
| light-dnd | `~/Projects/light-dnd` | runs in light-web (home screen doesn't fit, see HANDOFF) |
| light-reader | `~/Projects/light-reader` (yarosz/light-reader) | runs in light-web (see TOOL-ONBOARDING) |
| light-amp | `~/Projects/light-amp-fork` (wsturgiss fork of sublunarian/light-amp) | rejected: SDK submodule at an untagged commit, not v0.2.0 |
| light-herdr | `~/Projects/light-herdr` | skipped: not finished (Will) |
| light-agent-inbox | `~/Projects/light-agent-inbox` | not tried |
| Other local light-* repos | light-immich, light-keyboard, light-link, light-relay, light-relay-inbox, light-communication-rss, light-agent-relay | not checked whether they're SDK tools |

## SDK examples (`light-dnd/examples`, from lightphone/light-sdk)

Written against the SDK by Light, so these are the closest thing to a "typical tool".

| Example | Likely API areas |
|---|---|
| ui-demo | SDK UI only, so it should be the easiest |
| weather | network, location?, swipe gestures |
| audio-demo | audio playback |
| authenticator | crypto (HMAC), time, maybe camera for QR codes |
| tool-manager-demo | SDK tool-management APIs |

## Public tools on GitHub

| Repo | What it is | Likely API areas | Last push |
|---|---|---|---|
| lightphone/light-sdk | the official SDK and scaffold | upstream of the examples above | 2026-10-08 |
| garado/light-sdk-template | starter kit | baseline; should be trivial | 2026-09-21 |
| zacksimpson/measure-tool | measurement calculator | pure UI and maths: an easy one | 2026-09-17 |
| zacksimpson/verses-tool | daily Bible verse | dates, maybe network | 2026-08-18 |
| sjkornelsen/tasks | tasks (no description) | storage, maybe Room | 2026-07-28 |
| yarosz/light-pong | Pong on the scroll wheel | game loop, scroll-wheel input | 2026-10-11 |
| yarosz/light-chess | chess, puzzles, offline engine | heavy CPU, threads | 2026-10-09 |
| tyshi00/Passatempo | small games | game loop, input | 2026-10-07 |
| yarosz/light-doom | Doom via a Kotlin wasm interpreter | CPU, framebuffer drawing, audio; a stress test | 2026-09-29 |
| zachattack323/LightRSS | RSS reader | network, XML, storage | 2026-07-29 |
| garado/light-transit | transit times | network, location | 2026-09-21 |
| mitchellgordon95/light-books | audiobook player | audio, files, media session | 2026-10-10 |
| queueingqt/musicplus | Navidrome/Subsonic music | network, audio, downloads | 2026-10-09 |
| tthayer/music-app | music player | audio, files | 2026-07-20 |
| fenleon/chats | WhatsApp/Signal/Telegram bridge | network, websockets?, notifications | 2026-10-09 |
| ironfeet/Beeper4LightOS | Beeper client | network, notifications | 2026-07-28 |
| tthayer/yubikey-app | YubiKey OATH | NFC/USB: no browser equivalent (WebUSB at best) | 2026-07-16 |
| tthayer/sony-wf-control-app | Sony earbud control | Bluetooth serial: no browser equivalent | 2026-09-25 |
| thorntonmurals-del/onewheel-light-tool | Onewheel over BLE | BLE: Web Bluetooth at best | 2026-07-08 |

## awesome-light: SDK tools

Repos with a `lighttool.toml`, so light-web can target them. Ones already listed above are marked
"(above)". ★ = awesome-light Editor's Pick.

| Repo | Name | Category | Description |
|---|---|---|---|
| fenleon/chats | Chats | Communication | (above) |
| anweaver23/light-sdk | Chess | Entertainment | correspondence chess |
| gi-os/BrightSolitaire | LightSolitaire | Entertainment | Klondike solitaire |
| rsolter/matchday | Matchday | Entertainment | soccer scores (network) |
| tyshi00/Light-Games-Passatempo | Passatempo | Entertainment | (above, as tyshi00/Passatempo) |
| jabberbox/pokey | Pokey | Health & Wellness | GLP-1 injection and weight tracker |
| Zarrasko/reflect | Reflect | Health & Wellness | daily journaling prompts |
| tyshi00/Tracker | Tracker | Health & Wellness | water and health tracker |
| wsturgiss/light-training | Training | Health & Wellness | (above) |
| rn-os/SuperLight-Keyboard | SuperLight Keyboard | Keyboard | system keyboard (IME): likely out of scope |
| sublunarian/light-amp ★ | Amp | Music/Audio | (above) |
| fenleon/audiobooks | Audiobooks | Music/Audio | audiobook player |
| fenleon/radio-tool | Radio | Music/Audio | online radio (network, audio) |
| mrrobashcroft/radio-tool | Radio | Music/Audio | online radio via Media3 ExoPlayer |
| chairmanmortimort/Amtrak | Amtrak | Navigation | train tracker (network) |
| gi-os/BrightTransit | LightNYCSubway | Navigation | NYC subway times (network) |
| CJFData/light-transit | Pico Transit | Navigation | transit info (network) |
| barry7007/LightNorwayTransit | Sanntid | Navigation | Norway transit (network) |
| tyshi00/Aventura | Aventura | Productivity | offline quests |
| tyshi00/Ledger | Ledger | Productivity | budget tracker (storage) |
| cmg-ops/LP3-Lists | Lists | Productivity | nested lists (storage) |
| ChopinDavid/recall-lightos | Recall | Productivity | Anki-compatible review (SQLite? files) |
| fenleon/tasks | Tasks | Productivity | local-first to-do list |
| sjkornelsen/tasks | Tasks | Productivity | (above) Todoist client (network) |
| cmg-ops/LP3-Bible | Bible | Reading & Reference | offline Bible, downloadable translations |
| greghare/light-bible ★ | Bible | Reading & Reference | Bible reader |
| greghare/light-botany | Botany | Reading & Reference | plant ID (camera? ML?) |
| Zarrasko/brief | Brief | Reading & Reference | Kagi News reader (network) |
| jedbridges/cardinal-light | Cardinal Bible | Reading & Reference | offline Bible reader |
| zachattack323/LightRSS | Light RSS | Reading & Reference | (above) |
| zacksimpson/verses-tool ★ | Verses | Reading & Reference | (above) |
| tyshi00/Astraltrak | Astraltrak | Utility | celestial almanac (maths, time) |
| tyshi00/NFC-Reader | NFC Reader | Utility | NFC: no browser equivalent beyond Web NFC |
| fenleon/passes | Passes | Utility | barcode wallet (camera for scanning) |
| cmg-ops/LP3-StopWatch | StopWatch | Utility | stopwatch with saved history |
| tyshi00/World-Clocks | World Clocks | Utility | time zones |

## awesome-light: not SDK tools

**Plain Android apps (38).** These have an `AndroidManifest.xml` but no `lighttool.toml`: ordinary
Activities and services, not SDK tools. light-web's design doesn't cover them: they're the case only
a real Android runtime would handle. Worth scanning anyway, to see how far they are from the SDK
surface. Editor's Picks among them include Checklist, Metronome, Dictionary, Weather (vandamd),
Strings, Topographic, Reverb, NTS Radio, Zero, Bard, Reminders, Phono and Passes (vandamd).
Will's own light-immich is in this group.

**Neither (9):** garado/light, greghare/light-phone-manager and zacksimpson/reminders-web (desktop
companions), plus Alexis-NM/light-backlog, zacksimpson/nourish-tool, Ashley-doRego/ashdev,
zacksimpson/emoji-tool, zacksimpson/composer-tool and vandamd/passes. The last six are listed as
phone apps, so their Android project is probably on another branch or in a nested layout. Check them
by hand.

The classification script and raw data (`apps.tsv`, `kinds.tsv`) were one-off. To refresh, sparse-clone
awesome-light's `content/apps/*/index.md` and test each `repo` for `lighttool.toml` with
`gh api repos/<repo>/git/trees/HEAD?recursive=1`.

## Suggested order

1. **Baseline:** light-sdk-template, ui-demo, measure-tool. These should be nearly drop-in. If
   they aren't, the gaps are in the core.
2. **Storage and UI:** tasks (both), verses-tool, LP3-StopWatch, World-Clocks, reflect, LP3-Lists,
   BrightSolitaire, Passatempo, light-pong, light-chess.
3. **Network:** weather, LightRSS, light-transit. They share the CORS question with
   light-reader and light-amp.
4. **Audio:** audio-demo, light-books, music-app, musicplus, light-amp.
5. **Stress test:** light-doom.
6. **Probably out of scope** (hardware with no or partial browser equivalent): yubikey-app,
   sony-wf-control-app, onewheel-light-tool. Scan them anyway, as that's how we learn which APIs to
   mark as "not on web".

Clone third-party tools into a scratch folder (for example `~/Projects/light-web-corpus/`), not
into light-web, and don't open PRs or issues on them without asking Will.
