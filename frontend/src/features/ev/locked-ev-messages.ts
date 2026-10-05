/**
 * Shown when a visitor tries to change a locked (fixed demo) EV. The backend rejects the same
 * actions with 403 EV_LOCKED; these messages let the UI explain it up front, without a round trip.
 */
export const LOCKED_EV_EDIT_MESSAGE = 'This demo EV cannot be edited.'
export const LOCKED_EV_DELETE_MESSAGE = 'This demo EV cannot be deleted.'
export const LOCKED_EV_DISCONNECT_MESSAGE = 'The vehicle connection of this demo EV cannot be removed.'
