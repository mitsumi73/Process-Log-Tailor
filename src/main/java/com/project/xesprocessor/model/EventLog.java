package com.project.xesprocessor.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;


@Entity
public class EventLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String trace;
    private String event;
    private String timestamp;
    private String performer;
    private String lifecycleTransition;
    private String sequence;
    private String note;
    private String eventId;
    private String activity;
    private String docId;
    private String subprocess;
    private String identityId;
    private String docType;
    private String docIdUuid;
    private boolean success;


    // Default constructor
    public EventLog() {
    }

    // Constructor with parameters
    public EventLog(String trace, String event, String timestamp, String performer, String lifecycleTransition) {
        this.trace = trace;
        this.event = event;
        this.timestamp = timestamp;
        this.performer = performer;
        this.lifecycleTransition = lifecycleTransition;
    }


    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getActivity() {
        return activity;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getSubprocess() {
        return subprocess;
    }

    public void setSubprocess(String subprocess) {
        this.subprocess = subprocess;
    }

    public String getIdentityId() {
        return identityId;
    }

    public void setIdentityId(String identityId) {
        this.identityId = identityId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getDocIdUuid() {
        return docIdUuid;
    }

    public void setDocIdUuid(String docIdUuid) {
        this.docIdUuid = docIdUuid;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTrace() {
        return trace;
    }

    public void setTrace(String trace) {
        this.trace = trace;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getPerformer() {
        return performer;
    }

    public void setPerformer(String performer) {
        this.performer = performer;
    }

    public String getLifecycleTransition() {
        return lifecycleTransition;
    }

    public void setLifecycleTransition(String lifecycleTransition) {
        this.lifecycleTransition = lifecycleTransition;
    }

    public String getSequence() {
        return trace + " ==> " + event; // Example implementation
    }

    public void setSequence(String sequence) {
        this.sequence = sequence;
    }

    public List<String> getEvents() {
        List<String> events = new ArrayList<>();
        events.add(event);
        return events;
    }

    public long getDuration(EventLog nextEvent) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;
        LocalDateTime thisEventTime = LocalDateTime.parse(this.timestamp, formatter);
        LocalDateTime nextEventTime = LocalDateTime.parse(nextEvent.getTimestamp(), formatter);
        return Duration.between(thisEventTime, nextEventTime).toMinutes(); // Example: duration in minutes
    }
}