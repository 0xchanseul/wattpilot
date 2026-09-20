import { useId } from 'react'

/**
 * An EV side-profile rendered as a dark glassy silhouette with a glowing aurora-gradient edge —
 * matching the neon-outline car renders used as the design reference for the charging hero card.
 * `charging` drives the animated current flowing around the outline, a brighter/wider glow, and
 * pulsing rings at the charge port; the idle state keeps the same drawing but static and dimmer.
 */
export function ChargingCarIllustration({ charging }: { charging: boolean }) {
  const uid = useId()
  const gradientId = `${uid}-outline`
  const glowFilterId = `${uid}-glow`

  // Front on the left. Mostly straight panel lines (hood / windshield / roof / rear window /
  // trunk) with rounded corners only at the two bumpers and pillars — distinct flat segments read
  // as a car silhouette; an all-curve outline just blurs into a blob.
  const BODY_PATH =
    'M30 160 C21 160 16 155 19 147 L46 138 L95 130 L144 74 Q150 68 158 68 L222 68 Q230 68 236 75 L305 120 L330 145 C338 152 342 156 347 160 L30 160 Z'

  return (
    <svg
      viewBox="0 0 400 200"
      className="h-full w-full"
      role="img"
      aria-label={charging ? 'Vehicle charging' : 'Vehicle idle'}
    >
      <defs>
        <linearGradient id={gradientId} x1="20" y1="30" x2="360" y2="170" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#3FD0E0" stopOpacity={charging ? 1 : 0.55} />
          <stop offset="1" stopColor="#35E08D" stopOpacity={charging ? 1 : 0.55} />
        </linearGradient>
        <radialGradient id={`${uid}-ambient`} cx="0.5" cy="0.55" r="0.65">
          <stop offset="0" stopColor="#B6FBF0" stopOpacity={charging ? 0.55 : 0.25} />
          <stop offset="1" stopColor="#B6FBF0" stopOpacity="0" />
        </radialGradient>
        <filter id={glowFilterId} x="-60%" y="-60%" width="220%" height="220%">
          <feGaussianBlur stdDeviation={charging ? 4.5 : 3} />
        </filter>
      </defs>

      {/* ambient glow + ground shadow */}
      <ellipse cx="185" cy="160" rx="175" ry="36" fill={`url(#${uid}-ambient)`} />
      <ellipse cx="185" cy="172" rx="145" ry="8" fill="#04231f" opacity={charging ? 0.35 : 0.22} />

      {/* soft outer bloom */}
      <path
        d={BODY_PATH}
        fill="none"
        stroke={`url(#${gradientId})`}
        strokeWidth={charging ? 9 : 6}
        strokeLinejoin="round"
        opacity={charging ? 0.55 : 0.3}
        filter={`url(#${glowFilterId})`}
      />

      {/* glassy dark body fill */}
      <path d={BODY_PATH} fill="#071A22" opacity={charging ? 0.55 : 0.4} />

      {/* crisp glowing edge */}
      <path
        d={BODY_PATH}
        fill="none"
        stroke={`url(#${gradientId})`}
        strokeWidth="2.4"
        strokeLinejoin="round"
        strokeDasharray={charging ? '14 8' : undefined}
        style={charging ? { animation: 'aurora-flow 1.3s linear infinite' } : undefined}
      />

      {/* character line + door seam + mirror + headlight */}
      <path
        d="M62 128 L320 122"
        fill="none"
        stroke={`url(#${gradientId})`}
        strokeWidth="1.4"
        opacity="0.5"
      />
      <path d="M190 66 L184 160" fill="none" stroke={`url(#${gradientId})`} strokeWidth="1.2" opacity="0.35" />
      <path
        d="M138 92 Q128 88 130 97 Q137 101 143 96 Z"
        fill={`url(#${gradientId})`}
        opacity="0.8"
      />
      <ellipse cx="42" cy="143" rx="10" ry="5.5" fill="none" stroke={`url(#${gradientId})`} strokeWidth="1.6" opacity="0.85" />

      {/* wheels */}
      {[100, 288].map((cx) => (
        <g key={cx}>
          <circle cx={cx} cy="158" r="32" fill="none" stroke={`url(#${gradientId})`} strokeWidth="7" opacity={charging ? 0.5 : 0.28} filter={`url(#${glowFilterId})`} />
          <circle cx={cx} cy="158" r="32" fill="#050f14" stroke={`url(#${gradientId})`} strokeWidth="2.4" />
          <circle cx={cx} cy="158" r="12" fill="none" stroke={`url(#${gradientId})`} strokeWidth="1.4" opacity="0.75" />
          <circle cx={cx} cy="158" r="3" fill={`url(#${gradientId})`} />
        </g>
      ))}

      {/* charge port + bolt, rear quarter panel */}
      <g transform="translate(300 100)">
        {charging ? (
          <>
            {[0, 0.6].map((delay) => (
              <circle
                key={delay}
                r="10"
                fill="#35E08D"
                fillOpacity="0.35"
                stroke="#35E08D"
                strokeWidth="1.5"
                style={{
                  transformBox: 'fill-box',
                  transformOrigin: 'center',
                  animation: `aurora-pulse-ring 1.8s ease-out infinite ${delay}s`,
                }}
              />
            ))}
          </>
        ) : null}
        <circle r="8" fill="#0C2B36" stroke={`url(#${gradientId})`} strokeWidth="2" />
        <path
          d="M0.5 -3.8 L-2.9 1 L-0.2 1 L-0.7 4 L2.9 -1 L0.2 -1 Z"
          fill={charging ? '#35E08D' : `url(#${gradientId})`}
        />
      </g>
    </svg>
  )
}
