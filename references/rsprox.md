# RSProx database (reference for server accuracy)

Source: https://rsprox.net/database, a database of OSRS sessions recorded and submitted by the community with RSProx
(the blurite/rsprox proxy). The user approved it as a read-only reference. Indexed on 2026-10-19.

## Privacy and use rule
- Allowed: GET requests to rsprox.net only. Use a generic User-Agent, no login, no personal identifiers, at most
  1 request/s. Cache every page and don't fetch it again. No other hosts. Nothing ever connects to Jagex or
  RuneScape.
- Recordings can contain other players' names and chat. Recordings, index and transcripts stay local in the
  client repo under `re/ref/rsprox/` (git-ignored by `re/`). Never commit, quote or upload them anywhere,
  including this branch, PRs and issues. When you cite one, give only the recording id and the packet facts
  (ids, values, timings).
- Licence/terms: the site states none. There is no terms page, and robots.txt returns 404. The footer says
  "© 2026 Z-Kris". The RSProx tool itself is MIT (Z-Kris).

## Index
- Local file `re/ref/rsprox/index.json`. Each entry has id, date, revision, world_activity, size, download URL,
  title/description/tags (described entries only). The crawled pages are cached in `re/ref/rsprox/cache/`.
- 4283 submissions, ids 2–4309, revisions 223–239 (most: 235 ×842, 227 ×760, 229 ×608).
- Paging: `?view=all&sort=newest&size=100&page=N`, with N starting at 0 (43 pages at size 100).
- Titles are almost absent. `?view=described` has only 2 entries: 4272 and 4288, both The Blood Moon Rises,
  revision 239. The only other topical metadata is the world-activity label (2625 have "-"; Leagues V/VI 945;
  Grid Master; Sailing; minigames).
- Content search (which recordings contain NPC/OBJ/LOC/VARP/VARBIT/INTERFACE/MESSAGE_GAME/... X) exists, only as
  `POST https://rsprox.net/api/search` (approved since 2026-10-19). The JSON body is
  `{conditions:[{type,query}], mode:"ALL"|"ANY", page, limit, sort}`, and type is the numeric index of the
  dropdown (0 NPC, 1 OBJ, 2 LOC, 3 MAPSQUARE, 4 MESSAGE_GAME, 5 VARP, 6 VARBIT, 7 INTERFACE, 8 SEQ,
  9 SPOTANIM, 10 MIDI, 11 SOUND, 12 JINGLE, 14 TEXT). Gameval types take a numeric id as a string; types 4 and 14 are
  text. Response: `{results:[{submission:{id,date,revision,worldActivity,fileSize},matches}], totalResults, ...}`.
  The varp index looks sparse: VARP 179 matched only 1718 (which changes it), VARP 67 matched nothing, so a
  varp hit seems to need a value change in the recording; search by NPC or TEXT as well. Search responses are
  cached in `re/ref/rsprox/search/`. Gameval name lookups are GET
  (`/gamevals/index`, `/gamevals/oldschool/<cacheId>/<dataset>`).

## Download
`GET https://rsprox.net/download/<id>` answers 302 to `https://d339ktxhvl6mrs.cloudfront.net/<id>.bin`
(RSProx's CDN, now approved for downloads). Files go to `re/ref/rsprox/bin/<id>.bin`; record sha256 and size.
The downloaded files are gzip-compressed (the transcriber rejects them as-is with "Invalid binary master-index
length"): `gzip -dc bin/<id>.bin > $H/.rsprox/binary/<id>.bin` before transcribing.

## Transcribe (headless, verified)
Use the existing RSProx build in the client repo: `re/rsprox` at `v1.0-286-g2eab74ff` (1.0.5, the same build
staged at C:\osrs-test\rsprox), with decoders for revisions 223–241. Use the ~/tools JDK 21. Don't use the GUI
or a server.

```
H=<scratch home>; mkdir -p $H/.rsprox/binary $H/.rsprox/settings; gzip -dc bin/<id>.bin > $H/.rsprox/binary/<id>.bin
~/tools/jdk-21.0.12.1+1/bin/java -Duser.home=$H \
  -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=9 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9 \
  -cp "re/rsprox/build/osrs-test-runtime/*" net.rsprox.proxy.cli.TranscribeCommandKt -name <id>
# -> $H/.rsprox/binary/<id>.txt ; copy to re/ref/rsprox/txt/<id>.txt
```
- The proxy properties block all network access. This was verified on one of our own OpenRune desktop
  recordings (revision 241). It produced a full transcript with varps/varbits named, game messages and
  runclientscript.
- For community recordings the proxy properties are dropped, because the transcriber must fetch the build cache
  from archive.openrs2.org (approved). Create `$H/.rsprox/settings` first, or it logs a settings write error.
  archive.rsprox.net answered 403 for clientscript names (warning only; scripts show by id). Varp/varbit names are
  not resolved in these transcripts (ids only). Verified 2026-10-19 on 1619–1622 (rev 225.1, cache 1943) and
  1718 (rev 227.1, cache 1974): 1718 (4.45 MB gz) transcribes in about a minute.
- Cache dependency: the transcriber needs the cache for each recording's exact build. It looks in
  `$H/.rsprox/caches` first and otherwise fetches from `https://archive.openrs2.org`. Clientscript type names
  come from `https://archive.rsprox.net`; without them it only logs a warning. For community recordings this is
  a third host, so it needs approval too, or a pre-seeded cache.

## Coverage
| Topic | Recording ids | Revision | Status |
|---|---|---|---|
| Fishing Trawler | 2396 (world activity "Fishing Trawler", 17.59 KB) | 229 | candidate, not downloaded; small, likely partial |
| Player trading | trade worlds: 547, 548, 1699–1701 (227); 2388, 2392, 2497, 2796–2799 (229); 2844 (230); 4270, 4271 (239) | 227–239 | candidates; a trade-world label does not prove a trade happened |
| Sheep Shearer (full quest) | 1718 (Leagues V; quest at ticks 7184–7744: shearing incl. The Thing, spinning at Lumbridge Castle, Fred start dialogue, all-at-once hand-in, varp 179 0→1→21, quest complete if 153) | 227 | transcribed; notes `re/ref/rsprox/notes/sheep-shearer.md`. Leagues caveat: league task messages/overlay interleaved |
| Sheep shearing mechanics (post-quest) | 1619, 1620, 1621, 1622 (same account, varp 179 = 21 at login): shear timing, 50-tick regrowth, sheared ids, "already been shorn", no-shears message, The Thing (1620), pen gate 12986 | 225 | transcribed; same notes file. No Fred, spinning or hand-in |
| Goblin Diplomacy, Witch's Potion | not yet searched (VARP 67 search: no hits) | — | next: search by NPC (Generals, Hetty) and TEXT/MESSAGE_GAME |
| Demon Slayer, Shield of Arrav, Black Knights' Fortress, Dragon Slayer I, Druidic Ritual, dwarf multicannon, Genie/random events, Kalphite Queen, Tutorial Island, Edgeville/Lumbridge general stores, idle logout, fullscreen world map | none found | — | no titles or labels; use the content search (POST), e.g. by NPC, LOC (cannon), INTERFACE (shop, world map, trade) |

## Approvals
Approved by the user on 2026-10-19 for this work: POST `rsprox.net/api/search`, GET `d339ktxhvl6mrs.cloudfront.net`
(downloads), GET `archive.openrs2.org` (caches) and `archive.rsprox.net` (clientscript names). Nothing else, and
nothing ever connects to Jagex/RuneScape.
