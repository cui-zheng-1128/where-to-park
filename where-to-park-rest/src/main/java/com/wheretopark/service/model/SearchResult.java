package com.wheretopark.service.model;

import lombok.Value;

import java.util.List;

/**
 * Outcome of a nearby search.
 */
@Value
public class SearchResult {

    List<ScoredParking> results;
    Ranking appliedRanking;
}
