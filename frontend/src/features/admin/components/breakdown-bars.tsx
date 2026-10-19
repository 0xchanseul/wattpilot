interface BreakdownRow {
  key: string
  label: string
  visitors: number
  share: number
}

const MAX_ROWS = 5
const count = new Intl.NumberFormat('en-GB')

/** The largest rows, with the rest folded into one "Others" row so a long tail cannot crowd the chart. */
function withOthers(rows: BreakdownRow[]): BreakdownRow[] {
  if (rows.length <= MAX_ROWS) return rows
  const top = rows.slice(0, MAX_ROWS - 1)
  const rest = rows.slice(MAX_ROWS - 1)
  return [
    ...top,
    {
      key: '__others',
      label: 'Others',
      visitors: rest.reduce((sum, row) => sum + row.visitors, 0),
      share: rest.reduce((sum, row) => sum + row.share, 0),
    },
  ]
}

export function BreakdownBars({ rows, emptyText }: { rows: BreakdownRow[]; emptyText: string }) {
  if (rows.length === 0) {
    return <p className="text-muted-foreground py-10 text-center text-sm">{emptyText}</p>
  }

  return (
    <ul className="space-y-3">
      {withOthers(rows).map((row) => (
        <li key={row.key} className="space-y-1">
          <div className="flex items-baseline justify-between gap-2 text-sm">
            <span className="truncate">{row.label}</span>
            <span className="text-muted-foreground shrink-0 tabular-nums">
              {count.format(row.visitors)} · {(row.share * 100).toFixed(1)}%
            </span>
          </div>
          <div className="bg-secondary h-2 overflow-hidden rounded-full">
            <div
              className="bg-chart-1 h-full rounded-full"
              style={{ width: `${Math.max(row.share * 100, 1)}%` }}
            />
          </div>
        </li>
      ))}
    </ul>
  )
}
