import { CheckIcon, XIcon } from 'lucide-react'

import { cn } from '@/lib/utils'
import { formatDateTime, formatScheduleWindow } from '@/lib/format'
import { chargingFailureText } from '../failure-copy'
import type { ChargingSchedule } from '../types'

type StepState = 'done' | 'current' | 'upcoming' | 'failed' | 'skipped'

interface Step {
  label: string
  at?: string
  detail?: string
  state: StepState
}

/**
 * The lifecycle of one schedule as a vertical stepper. This is the schedule detail's operational
 * counterpart to the history receipt: it answers "where is this in its run?", not "what did it cost?".
 */
export function ScheduleTimeline({ schedule }: { schedule: ChargingSchedule }) {
  const steps = buildSteps(schedule)

  return (
    <ol className="space-y-0">
      {steps.map((step, index) => {
        const last = index === steps.length - 1
        return (
          <li key={step.label} className="flex gap-3">
            <div className="flex flex-col items-center">
              <Marker state={step.state} />
              {!last ? (
                <span
                  className={cn(
                    'w-px flex-1',
                    step.state === 'done' ? 'bg-chart-2/40' : 'bg-border',
                  )}
                />
              ) : null}
            </div>
            <div className={cn('pb-6', last && 'pb-0')}>
              <p
                className={cn(
                  'text-sm font-medium',
                  step.state === 'upcoming' && 'text-muted-foreground',
                  step.state === 'failed' && 'text-destructive',
                )}
              >
                {step.label}
              </p>
              {step.at ? (
                <p className="text-muted-foreground text-xs tabular-nums">
                  {formatDateTime(step.at)}
                </p>
              ) : null}
              {step.detail ? (
                <p className="text-muted-foreground mt-0.5 text-xs">{step.detail}</p>
              ) : null}
            </div>
          </li>
        )
      })}
    </ol>
  )
}

function Marker({ state }: { state: StepState }) {
  if (state === 'done') {
    return (
      <span className="bg-chart-2 flex size-5 items-center justify-center rounded-full text-white">
        <CheckIcon className="size-3" />
      </span>
    )
  }
  if (state === 'failed') {
    return (
      <span className="bg-destructive flex size-5 items-center justify-center rounded-full text-white">
        <XIcon className="size-3" />
      </span>
    )
  }
  if (state === 'current') {
    return (
      <span className="border-chart-2 flex size-5 items-center justify-center rounded-full border-2">
        <span className="bg-chart-2 size-2 animate-pulse rounded-full" />
      </span>
    )
  }
  if (state === 'skipped') {
    return <span className="border-border bg-muted size-5 rounded-full border" />
  }
  return <span className="border-border size-5 rounded-full border-2" />
}

function buildSteps(schedule: ChargingSchedule): Step[] {
  const { status, session } = schedule
  const chargingWindow = formatScheduleWindow(
    schedule.scheduledStartAt,
    schedule.scheduledEndAt,
  )
  const past = (states: ChargingSchedule['status'][]) => states.includes(status)

  const createdStep: Step = {
    label: 'Schedule created',
    at: schedule.createdAt,
    state: 'done',
  }

  const windowStep: Step = {
    label: 'Waiting for the cheapest window',
    detail: `${chargingWindow.date} · ${chargingWindow.timeRange}`,
    state:
      status === 'CANCELLED'
        ? 'skipped'
        : past(['IN_PROGRESS', 'COMPLETED', 'FAILED'])
          ? 'done'
          : 'current',
  }

  const chargingStep: Step = {
    label: 'Charging',
    at: session?.startedAt ?? undefined,
    state:
      status === 'IN_PROGRESS'
        ? 'current'
        : status === 'COMPLETED'
          ? 'done'
          : status === 'FAILED'
            ? 'failed'
            : status === 'CANCELLED'
              ? 'skipped'
              : 'upcoming',
  }

  let finishedStep: Step
  if (status === 'COMPLETED') {
    finishedStep = {
      label: 'Charging complete',
      at: session?.completedAt ?? undefined,
      state: 'done',
    }
  } else if (status === 'FAILED') {
    finishedStep = {
      label: 'Charging failed',
      at: session?.completedAt ?? undefined,
      detail:
        chargingFailureText(session?.failureCode ?? null, session?.failureReason ?? null) ??
        undefined,
      state: 'failed',
    }
  } else if (status === 'CANCELLED') {
    finishedStep = { label: 'Schedule cancelled', state: 'skipped' }
  } else {
    finishedStep = { label: 'Charging complete', state: 'upcoming' }
  }

  return [createdStep, windowStep, chargingStep, finishedStep]
}
