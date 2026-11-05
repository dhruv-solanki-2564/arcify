#!/usr/bin/env node
// Builds Arcify's bundled adult-site list from a StevenBlack-format hosts file.
//
// Usage:
//   node tools/build_adult_list.js <hosts file> blocker/src/main/assets/adult_domains.txt.gz
//
// Source: https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn-only/hosts
// (MIT licence, see blocker/src/main/assets/adult_domains_LICENSE.txt and THIRD_PARTY_NOTICES.md).
// Output: gzip of sorted, unique, lower-case domains, one per line, with "www." removed.
'use strict';
const fs = require('fs');
const zlib = require('zlib');

const [input, output] = process.argv.slice(2);
if (!input || !output) {
  console.error('usage: node tools/build_adult_list.js <hosts file> <output .txt.gz>');
  process.exit(1);
}

const skip = new Set(['localhost', 'localhost.localdomain', 'local', 'broadcasthost', 'ip6-localhost', 'ip6-loopback', '0.0.0.0']);
const domains = new Set();
for (const raw of fs.readFileSync(input, 'utf8').split(/\r?\n/)) {
  const line = raw.replace(/#.*/, '').trim();
  if (!line) continue;
  const parts = line.split(/\s+/);
  if (parts.length < 2 || (parts[0] !== '0.0.0.0' && parts[0] !== '127.0.0.1')) continue;
  let host = parts[1].toLowerCase();
  if (skip.has(host) || !host.includes('.')) continue;
  if (host.startsWith('www.')) host = host.slice(4);
  if (/^[a-z0-9.-]+$/.test(host)) domains.add(host);
}

const sorted = [...domains].sort();
fs.writeFileSync(output, zlib.gzipSync(Buffer.from(sorted.join('\n') + '\n', 'utf8'), { level: 9 }));
console.log(`${sorted.length} domains -> ${output} (${fs.statSync(output).size} bytes)`);
