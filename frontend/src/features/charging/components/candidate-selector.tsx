import { cn } from '@/lib/utils'
import { Badge } from '@/components/ui/badge'
import { formatDateTime, formatNok } from '@/lib/format'
import type { ChargingCandidate } from '../types'

interface CandidateSelectorProps {
  candidates: ChargingCandidate[]
  selectedRank: number
  onSelect: (rank: number) => void
}

/** Radio-group of the ranked charging windows from a preview. Rank 1 is the cheapest. */
export function CandidateSelector({
  candidates,
  selectedRank,
  onSelect,
}: CandidateSelectorProps) {
  return (
    <fieldset className="space-y-3">
      <legend className="text-sm font-medium">
        {candidates.length === 1
          ? 'One charging window fits'
          : `${candidates.length} charging windows fit — pick one`}
      </legend>
      <div className="grid gap-3">
        {candidates.map((candidate) => {
          const selected = candidate.rank === selectedRank
          return (
            <label
              key={candidate.rank}
              className={cn(
                'flex cursor-pointer items-start gap-3 rounded-lg border p-4 transition-colors',
                selected ? 'border-ring bg-accent/40' : 'hover:border-ring/60',
              )}
            >
              <input
                type="radio"
                name="charging-candidate"
                className="mt-1"
                checked={selected}
                onChange={() => onSelect(candidate.rank)}
              />
              <div className="min-w-0 flex-1 space-y-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium">
                    {formatDateTime(candidate.recommendedStartAt)}
                  </span>
                  <span className="text-muted-foreground">→ {formatDateTime(candidate.recommendedEndAt)}</span>
                  {candidate.rank === 1 ? <Badge variant="secondary">Cheapest</Badge> : null}
                </div>
                <div className="text-muted-foreground text-sm">
                  Est. cost{' '}
                  <span className="text-foreground font-medium">
                    {formatNok(candidate.estimatedCostNok)}
                  </span>
                  {candidate.expectedSavingsNok > 0 ? (
                    <>
                      {' '}
                      · saves{' '}
                      <span className="text-foreground font-medium">
                        {formatNok(candidate.expectedSavingsNok)}
                      </span>{' '}
                      vs. charging now
                    </>
                  ) : null}
                </div>
              </div>
            </label>
          )
        })}
      </div>
    </fieldset>
  )
}
