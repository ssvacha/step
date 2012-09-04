package com.tyndalehouse.step.core.service.jsword;

import org.crosswire.jsword.passage.Key;

import com.tyndalehouse.step.core.models.LookupOption;
import com.tyndalehouse.step.core.models.search.SearchResult;
import com.tyndalehouse.step.core.service.impl.SearchQuery;

/**
 * Searches across jsword modules
 * 
 * @author chrisburrell
 * 
 */
public interface JSwordSearchService {

    /**
     * estimates the number of results returned
     * 
     * @param sq the search query
     * @return the number of results returned by the query
     */
    int estimateSearchResults(SearchQuery sq);

    /**
     * Returns the total number of results in the search
     * 
     * @param results the key containing all the results
     * @return the total number of results
     */
    int getTotal(Key results);

    /**
     * A simple search that runs end to end, supports mutliple versions, runs on currentSearch only
     * 
     * @param sq the search query
     * @param version the version desired
     * @param options the options to be used to retrieve the text
     * @return the results
     */
    SearchResult search(SearchQuery sq, String version, LookupOption... options);

    /**
     * Searches uniquely for the keys, in order to do the passage lookup at a later stage
     * 
     * @param sq the search query
     * @return the key to all the results
     */
    Key searchKeys(SearchQuery sq);

    /**
     * Given a key, the search results are retrieved
     * 
     * @param sq the search query
     * @param results the results
     * @param version the version that is desired
     * @param options the list of options to apply to the search results text retrieved
     * @return the results
     */
    SearchResult retrieveResultsFromKeys(SearchQuery sq, Key results, String version, LookupOption... options);
}
