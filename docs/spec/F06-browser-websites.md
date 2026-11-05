# F06 — Browser Study Mode & website blocker

## What the original does
- **No VPN.** The original declares no VPN service. `[manifest]` Website blocking works by reading the browser's address bar through accessibility. `[inferred]` `[static]`
- **Browser Study Mode**: during focus only allowed websites open. A browser can be kept usable for studying. `[listing]` `[static]`
- **Website blocker**: blocks adult sites and user-chosen distractions, with an optional **strict blocking** commitment. `[listing]`
- Block-list entries carry a URL, a category and an icon, and are fetched from the original's server. Search over a remote catalogue exists. `[static]`
- The number of blocked websites may be capped for free users. `[static]` Arcify has no cap.

## Arcify design `[arcify]`
- `MonitoredBrowser` list with bundled defaults for **Chrome** and **Brave** (address-bar view IDs). Editable. Others can be added: Firefox, Edge, Samsung Internet, etc.
- `WebsiteRule(pattern, kind = BLOCK|ALLOW, category?, source = USER|DEFAULT_LIST)`. Patterns match a domain plus its subdomains, or a domain+path prefix.
- **Adult filter**: an optional bundled domain list (MIT-licensed public hosts list, e.g. StevenBlack). Updatable by importing a file; entries can be disabled individually.
- **Study mode**: when active, only `ALLOW` rules pass and everything else is blocked.
- **Strict blocking**: rules marked strict can't be removed until their lock date.
- Detection reads the address-bar text on content change. URLs are normalised: lower-cased host, `m.`/`www.` stripped, scheme and query ignored. On a match, show the overlay and navigate back.
- Also feeds the F04 browser reel/shorts counters.

## Implementation (M4)
- **Rules** (pure, tested; `core/.../content/Websites.kt`):
  - `UrlNormalizer`: lower-cases the host and strips `www.`/`m.`/`mobile.`. Search terms, internal pages and dotless hosts give null.
  - `WebsitePolicy`: a pattern covers its domain and subdomains, or a path prefix. In study mode only ALLOW rules pass.
- **Engine**: `ContentGuard.inspectBrowser`. Websites are checked first, then short-form URLs.
- **Storage**: `website_rule` (pattern primary key, strict lock), `monitored_browser` (defaults Chrome and Brave, seeded once).
- **UI**: Block → Websites & browsers.
  - Study-mode switch, block/allow rules, lock-for-today on block rules.
  - Add a browser from installed apps; it assumes the Chromium address-bar ID `<package>:id/url_bar`. Reset browsers.
- **⚠️ Unverified**: the Chrome and Brave address-bar IDs are the commonly documented ones but haven't been checked on a phone.
- **Not yet**: the adult-site list (needs a bundled or imported hosts file; deferred, needs the user's OK to fetch a third-party list), and per-browser editing of address-bar IDs in the UI.
