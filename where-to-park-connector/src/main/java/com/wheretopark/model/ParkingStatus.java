package com.wheretopark.model;

/**
 * Availability status of a parking, derived by the backend and never left for each frontend to recompute.
 */
public enum ParkingStatus {

    /** Open with at least one free spot. */
    OPEN,

    /** Open but full. */
    FULL,

    /** Closed to the public. */
    CLOSED,

    /** The source provides no status, or cannot distinguish "full" from "closed". */
    UNKNOWN
}
