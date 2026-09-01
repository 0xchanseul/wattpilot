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
