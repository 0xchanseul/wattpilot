export type EvStatus = 'ACTIVE' | 'INACTIVE'

export interface Ev {
  id: number
  name: string
  manufacturer: string
  model: string
  batteryCapacityKwh: number
  maxAcChargingPowerKw: number
  defaultChargerPowerKw: number
  status: EvStatus
  createdAt: string
  updatedAt: string
}

export interface CreateEvInput {
  name: string
  manufacturer: string
  model: string
  batteryCapacityKwh: number
  maxAcChargingPowerKw: number
  defaultChargerPowerKw: number
}

/** PATCH /evs/{id}: any subset of the create fields, plus an optional status change. */
export type UpdateEvInput = Partial<CreateEvInput> & { status?: EvStatus }

export interface ListEvsParams {
  status?: EvStatus
  page?: number
  size?: number
}

/** A curated vehicle spec preset (V1.5 master data) used only to prefill the EV registration form. */
export interface VehicleModel {
  id: number
  manufacturer: string
  model: string
  batteryCapacityKwh: number
  maxAcChargingPowerKw: number
}

/**
 * Read-only Smartcar vehicle connection (V1.5, see docs/mvp-scope.md). Link metadata only - no live
 * data. Smartcar ids are internal and never exposed to the frontend.
 */
export interface VehicleConnection {
  evId: number
  make: string | null
  model: string | null
  year: number | null
  connectedAt: string
}

/**
 * Live read from the connected vehicle, fetched fresh on every request. Any field may be `null` if
 * that signal is unsupported by the vehicle. Deliberately unrelated to Mock Charging's own
 * simulated progress - the two may legitimately disagree.
 */
export interface VehicleTelemetry {
  stateOfChargePercent: number | null
  rangeKm: number | null
  isPluggedIn: boolean | null
  isCharging: boolean | null
  retrievedAt: string
}

export interface VehicleCandidate {
  smartcarVehicleId: string
  make: string | null
  model: string | null
  year: number | null
}

export interface VehicleConnectCandidates {
  evId: number
  vehicles: VehicleCandidate[]
}
