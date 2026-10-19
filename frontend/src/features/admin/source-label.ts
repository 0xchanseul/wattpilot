const SOURCE_LABELS: Record<string, string> = {
  direct: 'Direct',
  linkedin: 'LinkedIn',
  reddit: 'Reddit',
  google: 'Google',
  bing: 'Bing',
  duckduckgo: 'DuckDuckGo',
  naver: 'Naver',
  x: 'X',
  facebook: 'Facebook',
  instagram: 'Instagram',
  github: 'GitHub',
  hackernews: 'Hacker News',
  youtube: 'YouTube',
}

/** A known source gets its proper casing; anything else (a host or a campaign name) is shown as stored. */
export function sourceLabel(source: string | null): string {
  if (source === null) return 'Unknown (before tracking)'
  return SOURCE_LABELS[source] ?? source
}
