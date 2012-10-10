package com.tyndalehouse.step.core.data.entities.impl;

import static org.apache.lucene.util.Version.LUCENE_30;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.MultiFieldQueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.queryParser.QueryParser.Operator;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopFieldDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.slf4j.Logger;

import com.tyndalehouse.step.core.data.AllResultsCollector;
import com.tyndalehouse.step.core.data.AnalyzedPrefixSearchQueryParser;
import com.tyndalehouse.step.core.data.EntityConfiguration;
import com.tyndalehouse.step.core.data.EntityDoc;
import com.tyndalehouse.step.core.exceptions.StepInternalException;
import com.tyndalehouse.step.core.utils.IOUtils;

/**
 * Reads an entity
 * 
 * @author chrisburrell
 * 
 */
public class EntityIndexReaderImpl implements com.tyndalehouse.step.core.data.EntityIndexReader {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(EntityIndexReaderImpl.class);
    private IndexSearcher searcher;
    private Directory directory;
    private final EntityConfiguration config;
    private boolean memoryMapped;

    /**
     * Entity reader
     * 
     * @param config the config about the reader
     * @param memoryMapped true to indicate index should be stored in memory
     */
    public EntityIndexReaderImpl(final EntityConfiguration config, final boolean memoryMapped) {
        this.config = config;
        this.memoryMapped = memoryMapped;
        initialise();
    }

    /**
     * Entity reader - does not initialise it entirely - careful when using this.
     * 
     * @param config the config about the reader
     */
    EntityIndexReaderImpl(final EntityConfiguration config) {
        this.config = config;
    }

    @Override
    public Analyzer getAnalyzer() {
        return this.config.getAnalyzerInstance();
    }

    /**
     * Initialises the index reader
     */
    private void initialise() {
        try {
            openDirectory(this.config, this.memoryMapped);
            if (this.directory != null) {
                this.searcher = new IndexSearcher(this.directory, true);
            }
        } catch (final IOException e) {
            LOGGER.warn("Index not readable - it may not yet have been created.");
            LOGGER.trace("Trace for exception:", e);
        }
    }

    @Override
    public void refresh() {
        close();
        initialise();
    }

    @Override
    public void close() {
        IOUtils.closeQuietly(this.searcher);
        IOUtils.closeQuietly(this.directory);
    }

    /**
     * Gets the best implementation of the directory
     * 
     * @param configuration config
     * @param memoryMapDirectory memory mapped directories
     */
    private void openDirectory(final EntityConfiguration configuration, final boolean memoryMapDirectory) {
        try {
            final URI entityIndexPath = configuration.getLocation();
            final File path = new File(entityIndexPath);
            if (!path.exists()) {
                return;
            }

            if (memoryMapDirectory) {
                this.directory = MMapDirectory.open(path);
            }

            this.directory = FSDirectory.open(path);
        } catch (final IOException e) {
            throw new StepInternalException("Unable to read directory", e);
        }
    }

    @Override
    public EntityDoc[] searchExactTermBySingleField(final String fieldName, final int max,
            final String... values) {
        final Query query = getQuery(fieldName, values);
        return search(query, max, null);
    }

    @Override
    public EntityDoc[] searchUniqueBySingleField(final String fieldName, final String... values) {
        final Query query = getQuery(fieldName, values);
        return search(query, values.length, null);
    }

    @Override
    public EntityDoc[] search(final String[] fieldNames, final String value, final Filter filter,
            final Sort sort, final boolean analyzePrefix) {
        return search(fieldNames, value, filter, sort, analyzePrefix, null);
    }

    @Override
    public EntityDoc[] search(final String[] fieldNames, final String value, final Filter filter,
            final Sort sort, final boolean analyzePrefix, final String queryRemainder) {
        final AllResultsCollector collector = new AllResultsCollector();
        Query parsed = null;
        QueryParser parser;
        if (analyzePrefix) {
            parser = new AnalyzedPrefixSearchQueryParser(LUCENE_30, fieldNames,
                    this.config.getAnalyzerInstance());
        } else {
            parser = new MultiFieldQueryParser(LUCENE_30, fieldNames, this.config.getAnalyzerInstance());
        }

        try {
            if (queryRemainder != null) {
                final StringBuilder sb = new StringBuilder(value.length() + queryRemainder.length() + 1);
                sb.append(value);
                sb.append(' ');
                sb.append(queryRemainder);
                parsed = parser.parse(sb.toString());
            } else {
                parsed = parser.parse(value);
            }

            if (sort != null) {
                final TopFieldDocs search = this.searcher.search(parsed, filter, Integer.MAX_VALUE, sort);
                return extractDocIds(search);

            } else {
                this.searcher.search(parsed, filter, collector);
                return extractDocIds(collector);
            }
        } catch (final ParseException e) {
            throw new StepInternalException("Unable to parse query", e);
        } catch (final IOException e) {
            throw new StepInternalException(
                    "Unable to search given query: " + parsed != null ? parsed.toString() : "<unknown>", e);
        }
    }

    /**
     * Extracts all the results
     * 
     * @param results the results that have been collected
     * @return the results
     */
    private EntityDoc[] extractDocIds(final TopFieldDocs results) {
        try {
            final ScoreDoc[] scoreDocs = results.scoreDocs;
            final EntityDoc[] docs = new EntityDoc[scoreDocs.length];
            for (int ii = 0; ii < scoreDocs.length; ii++) {
                docs[ii] = new EntityDoc(this.searcher.doc(scoreDocs[ii].doc));
            }
            return docs;
        } catch (final IOException e) {
            throw new StepInternalException("Unable to extract results", e);
        }

    }

    @Override
    public EntityDoc[] search(final Query query) {
        final AllResultsCollector collector = new AllResultsCollector();
        try {
            this.searcher.search(query, collector);
            return extractDocIds(collector);
        } catch (final IOException e) {
            throw new StepInternalException("Unable to search", e);
        }
    }

    /**
     * Extracts the query results into an entity doc
     * 
     * @param collector the collector with the results
     * @return all the entity documents
     */
    private EntityDoc[] extractDocIds(final AllResultsCollector collector) {
        try {
            final List<Integer> docIds = collector.getDocIds();
            final EntityDoc[] docs = new EntityDoc[docIds.size()];
            for (int ii = 0; ii < docIds.size(); ii++) {
                docs[ii] = new EntityDoc(this.searcher.doc(docIds.get(ii)));
            }
            return docs;
        } catch (final IOException e) {
            throw new StepInternalException("Unable to extract results from query", e);
        }
    }

    @Override
    public EntityDoc[] search(final Query query, final int max, final Sort sortField) {
        try {
            final TopDocs search;
            if (sortField != null) {
                search = this.searcher.search(query, null, max, sortField);
            } else {
                search = this.searcher.search(query, max);
            }

            final EntityDoc[] results = new EntityDoc[search.scoreDocs.length];
            for (int ii = 0; ii < search.scoreDocs.length; ii++) {
                results[ii] = new EntityDoc(this.searcher.doc(search.scoreDocs[ii].doc));
            }

            return results;
        } catch (final IOException e) {
            throw new StepInternalException("Failed to search", e);
        }
    }

    /**
     * Returns a query that matches the provided terms
     * 
     * @param fieldName the field name
     * @param values the values passed in
     * @return query
     */
    private Query getQuery(final String fieldName, final String... values) {
        if (values.length == 1) {
            return new TermQuery(new Term(fieldName, values[0]));
        }
        final Term t = new Term(fieldName);
        final Term[] ts = new Term[values.length];
        for (int ii = 0; ii < ts.length; ii++) {
            ts[ii] = t.createTerm(values[0]);
        }

        final BooleanQuery booleanQuery = new BooleanQuery();
        for (final Term term : ts) {
            booleanQuery.add(new TermQuery(term), Occur.SHOULD);
        }

        return booleanQuery;
    }

    @Override
    public EntityDoc[] searchSingleColumn(final String fieldName, final String querySyntax,
            final Operator op, final boolean allowLeadingWildcard) {
        final QueryParser parser = new QueryParser(LUCENE_30, fieldName, this.getAnalyzer());
        parser.setDefaultOperator(op);
        parser.setAllowLeadingWildcard(allowLeadingWildcard);

        try {
            final Query query = parser.parse(querySyntax);
            return search(query);

        } catch (final ParseException e) {
            throw new StepInternalException("Unable to parse query", e);
        }

    }

    @Override
    public EntityDoc[] searchSingleColumn(final String fieldName, final String querySyntax) {
        return searchSingleColumn(fieldName, querySyntax, Operator.OR, false);
    }

    /**
     * @param searcher the searcher to set
     */
    void setSearcher(final IndexSearcher searcher) {
        this.searcher = searcher;
    }
}