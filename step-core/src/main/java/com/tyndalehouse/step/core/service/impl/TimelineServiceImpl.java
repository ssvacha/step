package com.tyndalehouse.step.core.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.Query;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.tyndalehouse.step.core.data.entities.HotSpot;
import com.tyndalehouse.step.core.data.entities.ScriptureReference;
import com.tyndalehouse.step.core.data.entities.TimelineEvent;
import com.tyndalehouse.step.core.data.entities.aggregations.TimelineEventsAndDate;
import com.tyndalehouse.step.core.data.entities.reference.TargetType;
import com.tyndalehouse.step.core.service.JSwordService;
import com.tyndalehouse.step.core.service.TimelineService;

/**
 * The implementation of the timeline service, based on JDBC and ORM Lite to access the database.
 * 
 * @author Chris
 */
@Singleton
public class TimelineServiceImpl implements TimelineService {
    private static final Logger LOGGER = LoggerFactory.getLogger(TimelineServiceImpl.class);

    private final EbeanServer ebean;
    private final JSwordService jsword;

    /**
     * @param ebean the ebean server with which to lookup data
     * @param jsword the jsword service
     */
    @Inject
    public TimelineServiceImpl(final EbeanServer ebean, final JSwordService jsword) {
        this.ebean = ebean;
        this.jsword = jsword;
    }

    @Override
    public List<HotSpot> getTimelineConfiguration() {
        return this.ebean.createQuery(HotSpot.class).findList();
    }

    @Override
    public TimelineEventsAndDate getEventsFromScripture(final String reference) {
        final TimelineEventsAndDate timelineEventsAndDate = new TimelineEventsAndDate();

        final List<TimelineEvent> matchingTimelineEvents = lookupEventsMatchingReference(reference);
        timelineEventsAndDate.setEvents(matchingTimelineEvents);

        timelineEventsAndDate.setDateTime(getDateForEvents(matchingTimelineEvents));

        return timelineEventsAndDate;

    }

    /**
     * Gets the date which is most appropriate for centering around these events, i.e. the median?
     * 
     * @param matchingTimelineEvents the number of events
     * @return the localDateTime of the median event, if a duration, then of the start point
     */
    private LocalDateTime getDateForEvents(final List<TimelineEvent> matchingTimelineEvents) {
        if (matchingTimelineEvents.isEmpty()) {
            return null;
        }

        // copy list to new list that can be sorted
        final List<TimelineEvent> events = new ArrayList<TimelineEvent>(matchingTimelineEvents);

        // first we order events based on the start date
        Collections.sort(events, new Comparator<TimelineEvent>() {

            @Override
            public int compare(final TimelineEvent o1, final TimelineEvent o2) {
                return o1.getFromDate().compareTo(o2.getFromDate());
            }
        });

        // now we simply return the median element
        return events.get(events.size() / 2).getFromDate();
    }

    /**
     * This method simply takes a reference, resolves it to the kjv versification, and then manages to output
     * all events that match
     * 
     * @param reference the reference we are looking for
     * @return the list of events matching the reference
     */
    private List<TimelineEvent> lookupEventsMatchingReference(final String reference) {
        // first get the kjv reference
        final List<ScriptureReference> passageReferences = this.jsword.getPassageReferences(reference);

        if (passageReferences.size() == 0) {
            return new ArrayList<TimelineEvent>();
        }

        // let's assume for now we only work on one reference
        final ScriptureReference searchingReference = passageReferences.get(0);

        LOGGER.debug("Finding events overlapping [{}] and [{}]", searchingReference.getStartVerseId(),
                searchingReference.getEndVerseId());

        // find all timeline events where at least one scripture reference maps to our 1st reference
        // overlap: if a and b are start and stop of event, then overlap formula is:
        // search_start < b & search_end > a

        final String queryText = "find timelineEvent where references.targetType = :targetType "
                + "and :searchStart <= references.endVerseId and :searchEnd >= references.startVerseId";
        // final String queryText = "find timelineEvent";

        final Query<TimelineEvent> query = this.ebean.createQuery(TimelineEvent.class, queryText).fetch(
                "references");

        query.setParameter("targetType", TargetType.TIMELINE_EVENT);
        query.setParameter("searchStart", searchingReference.getStartVerseId());
        query.setParameter("searchEnd", searchingReference.getEndVerseId());

        return query.findList();

    }

    @Override
    public List<TimelineEvent> getTimelineEvents(final LocalDateTime from, final LocalDateTime to) {
        // fromDate < to and toDate > from is the standard coverage method where we find the overlapping
        // events
        // however "toDate" can be null, therefore we need to cater for that

        // which gives us
        // fromDate < to and ((toDate != null and toDate > from) or (toDate == null and fromDate > from ))

        // in other words the event starts before the requested period ends, but finishes after the end

        final String eventsQuery = "find timelineEvent where fromDate <= :to and "
                + "((toDate is not null and toDate >= :from) or (toDate is null and fromDate >= :from))";

        final Query<TimelineEvent> query = this.ebean.createQuery(TimelineEvent.class, eventsQuery);
        query.setParameter("from", from);
        query.setParameter("to", to);
        return query.findList();
    }
}