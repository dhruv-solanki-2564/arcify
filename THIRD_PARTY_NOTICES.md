# Third-party notices

## StevenBlack/hosts — adult-site list
- **Used for:** Arcify's optional adult-site filter (spec F06).
- **Source:** https://github.com/StevenBlack/hosts (the `alternates/porn-only` hosts file, version of 1 November 2026).
- **Bundled as:** `blocker/src/main/assets/adult_domains.txt.gz`, a list of domains only, built with `tools/build_adult_list.js`.
- **Licence:** MIT. The full licence text ships with the app at `blocker/src/main/assets/adult_domains_LICENSE.txt`.

To refresh the list, download the latest hosts file and run:

```bash
node tools/build_adult_list.js hosts blocker/src/main/assets/adult_domains.txt.gz
```
