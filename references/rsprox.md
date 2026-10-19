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
- Content search (which recordings contain NPC/OBJ/LOC/VARP/VARBIT/INTERFACE/MESSAGE_GAME/... X) exists, but
  only as `POST https://rsprox.net/api/search`. The JSON body is
  `{conditions:[{type,query}], mode:"ALL"|"ANY", page, limit, sort}`, and type is the numeric index of the
  dropdown (0 NPC, 1 OBJ, 2 LOC, 3 MAPSQUARE, 4 MESSAGE_GAME, 5 VARP, 6 VARBIT, 7 INTERFACE, 8 SEQ,
  9 SPOTANIM, ...). Because it is POST, it is outside the approved rule. Gameval name lookups are GET
  (`/gamevals/index`, `/gamevals/oldschool/<cacheId>/<dataset>`).

## Download
`GET https://rsprox.net/download/<id>` answers 302 to `https://d339ktxhvl6mrs.cloudfront.net/<id>.bin`
(RSProx's CDN). That is another host, so it is not followed under the current rule. Files go to
`re/ref/rsprox/bin/<id>.bin`; record sha256 and size.

## Transcribe (headless, verified)
Use the existing RSProx build in the client repo: `re/rsprox` at `v1.0-286-g2eab74ff` (1.0.5, the same build
staged at C:\osrs-test\rsprox), with decoders for revisions 223–241. Use the ~/tools JDK 21. Don't use the GUI
or a server.

```
H=<scratch home>; mkdir -p $H/.rsprox/binary; cp <id>.bin $H/.rsprox/binary/
~/tools/jdk-21.0.12.1+1/bin/java -Duser.home=$H \
  -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=9 -Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=9 \
  -cp "re/rsprox/build/osrs-test-runtime/*" net.rsprox.proxy.cli.TranscribeCommandKt -name <id>
# -> $H/.rsprox/binary/<id>.txt ; copy to re/ref/rsprox/txt/<id>.txt
```
- The proxy properties block all network access. This was verified on one of our own OpenRune desktop
  recordings (revision 241). It produced a full transcript with varps/varbits named, game messages and
  runclientscript.
- Cache dependency: the transcriber needs the cache for each recording's exact build. It looks in
  `$H/.rsprox/caches` first and otherwise fetches from `https://archive.openrs2.org`. Clientscript type names
  come from `https://archive.rsprox.net`; without them it only logs a warning. For community recordings this is
  a third host, so it needs approval too, or a pre-seeded cache.

## Coverage
| Topic | Recording ids | Revision | Status |
|---|---|---|---|
| Fishing Trawler | 2396 (world activity "Fishing Trawler", 17.59 KB) | 229 | candidate, not downloaded; small, likely partial |
| Player trading | trade worlds: 547, 548, 1699–1701 (227); 2388, 2392, 2497, 2796–2799 (229); 2844 (230); 4270, 4271 (239) | 227–239 | candidates; a trade-world label does not prove a trade happened |
| Sheep Shearer, Goblin Diplomacy, Witch's Potion, Demon Slayer, Shield of Arrav, Black Knights' Fortress, Dragon Slayer I, Druidic Ritual, dwarf multicannon, Genie/random events, Kalphite Queen, Tutorial Island, Edgeville/Lumbridge general stores, idle logout, fullscreen world map | none found | — | no titles or labels. Finding them needs the content search (POST), e.g. by quest varp, NPC (Fred, Kalphite Queen, Genie), LOC (cannon), INTERFACE (shop, world map, trade) |

## Pending approvals
To go further, the user needs to approve: (a) POST to `rsprox.net/api/search`, (b) GET from
`d339ktxhvl6mrs.cloudfront.net` (downloads), (c) GET from `archive.openrs2.org` for caches
(`archive.rsprox.net` is optional, for clientscript names).
