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
