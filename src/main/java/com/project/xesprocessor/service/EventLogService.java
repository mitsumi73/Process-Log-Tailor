package com.project.xesprocessor.service;

import com.project.xesprocessor.model.EventLog;
import com.project.xesprocessor.repository.EventLogRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.apache.poi.ss.usermodel.*;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import java.util.*;
import java.util.stream.Collectors;
import javax.xml.parsers.*;
import java.util.ArrayList;


import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;


@Service
public class EventLogService {

    @Autowired
    private EventLogRepository eventLogRepository;

    public List<EventLog> getAllEventLogs() {
        return eventLogRepository.findAll();
    }

    public EventLog saveEventLog(EventLog eventLog) {
        return eventLogRepository.save(eventLog);
    }

    public void saveEventLogs(List<EventLog> eventLogs) {
        eventLogRepository.saveAll(eventLogs);
    }

    public Optional<EventLog> findById(Long id) {
        return eventLogRepository.findById(id);
    }

    public List<String> getUniqueEvents() {
        return eventLogRepository.findAll().stream()
                .map(EventLog::getEvent)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> getUniqueTraces() {
        return eventLogRepository.findAll().stream()
                .map(EventLog::getTrace)
                .distinct()
                .collect(Collectors.toList());
    }

    public List<String> getUniquePerformers() {
        return eventLogRepository.findAll().stream()
                .map(EventLog::getPerformer)
                .filter(performer -> performer != null)
                .distinct()
                .collect(Collectors.toList());
    }

    public Map<String, Object> getSummaryInformation(String fileName) {
        List<EventLog> logs = getAllEventLogs();

        long numberOfTraces = logs.stream().map(EventLog::getTrace).distinct().count();
        long numberOfEvents = logs.size();
        long numberOfActivities = logs.stream().map(EventLog::getEvent).distinct().count();
        long numberOfTraceTypes = calculateUniqueSequences(logs);
        long numberOfPerformers = logs.stream().map(EventLog::getPerformer).filter(performer -> performer != null).distinct().count();
        long maxLength = logs.stream().map(EventLog::getTrace)
                .collect(Collectors.groupingBy(trace -> trace, Collectors.counting()))
                .values().stream().max(Long::compare).orElse(0L);
        long minLength = logs.stream().map(EventLog::getTrace)
                .collect(Collectors.groupingBy(trace -> trace, Collectors.counting()))
                .values().stream().min(Long::compare).orElse(0L);

        Map<String, Object> summaryInfo = new HashMap<>();
        summaryInfo.put("fileName", fileName);
        summaryInfo.put("numberOfTraces", numberOfTraces);
        summaryInfo.put("numberOfTraceTypes", numberOfTraceTypes);
        summaryInfo.put("numberOfEvents", numberOfEvents);
        summaryInfo.put("numberOfActivities", numberOfActivities);
        summaryInfo.put("numberOfPerformers", numberOfPerformers);
        summaryInfo.put("maxLength", maxLength);
        summaryInfo.put("minLength", minLength);

        return summaryInfo;
    }

    private long calculateUniqueSequences(List<EventLog> logs) {
        // Group events by trace
        Map<String, List<EventLog>> traceEvents = logs.stream()
                .collect(Collectors.groupingBy(EventLog::getTrace, Collectors.toList()));

        // Generate sequences and count unique sequences
        Set<String> uniqueSequences = new HashSet<>();
        for (Map.Entry<String, List<EventLog>> entry : traceEvents.entrySet()) {
            List<EventLog> events = entry.getValue();
            events.sort(Comparator.comparing(EventLog::getTimestamp));
            StringBuilder sequenceBuilder = new StringBuilder();
            for (EventLog event : events) {
                if (sequenceBuilder.length() > 0) {
                    sequenceBuilder.append(" ==> ");
                }
                sequenceBuilder.append(event.getEvent());
            }
            uniqueSequences.add(sequenceBuilder.toString());
        }

        return uniqueSequences.size();
    }

    public List<EventLog> getEventLogsByTraceAndEvent(String trace, String event) {
        return eventLogRepository.findAll().stream()
                .filter(log -> (trace.isEmpty() || log.getTrace().equals(trace)) &&
                        (event.isEmpty() || log.getEvent().equals(event)))
                .collect(Collectors.toList());
    }

    public Workbook generateSelectedExcelReport(List<EventLog> selectedLogs) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Event Logs");

        // Create the header row
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("Trace");
        header.createCell(1).setCellValue("Event");
        header.createCell(2).setCellValue("Timestamp");
        header.createCell(3).setCellValue("Lifecycle Transition");
        header.createCell(4).setCellValue("Performer");

        // Populate the data rows
        int rowNum = 1;
        for (EventLog log : selectedLogs) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(log.getTrace());
            row.createCell(1).setCellValue(log.getEvent());
            row.createCell(2).setCellValue(log.getTimestamp());
            row.createCell(3).setCellValue(log.getLifecycleTransition());
            row.createCell(4).setCellValue(log.getPerformer());
        }

        return workbook;
    }

    public Map<String, Long> calculateSequenceFrequency() {
        List<EventLog> logs = getAllEventLogs();
        Map<String, Long> sequenceFrequency = new HashMap<>();

        // Group events by trace
        Map<String, List<EventLog>> traceEvents = logs.stream()
                .collect(Collectors.groupingBy(EventLog::getTrace, Collectors.toList()));

        // Generate sequences and count their frequencies
        for (Map.Entry<String, List<EventLog>> entry : traceEvents.entrySet()) {
            List<EventLog> events = entry.getValue();
            events.sort(Comparator.comparing(EventLog::getTimestamp));
            StringBuilder sequenceBuilder = new StringBuilder();
            for (EventLog event : events) {
                if (sequenceBuilder.length() > 0) {
                    sequenceBuilder.append(" ==> ");
                }
                sequenceBuilder.append(event.getEvent());
            }
            String sequence = sequenceBuilder.toString();
            sequenceFrequency.put(sequence, sequenceFrequency.getOrDefault(sequence, 0L) + 1);
        }

        return sequenceFrequency;
    }

    public List<PerformanceAnalysis> getPerformanceAnalysis() {
        List<EventLog> logs = getAllEventLogs();

        // Group logs by trace and sort by timestamp
        Map<String, List<EventLog>> logsByTrace = logs.stream()
                .collect(Collectors.groupingBy(EventLog::getTrace));
        logsByTrace.values().forEach(traceLogs ->
                traceLogs.sort(Comparator.comparing(EventLog::getTimestamp)));

        Map<String, List<Long>> eventDurations = new HashMap<>();
        logsByTrace.forEach((trace, traceLogs) -> {
            for (int i = 0; i < traceLogs.size() - 1; i++) {
                EventLog currentEvent = traceLogs.get(i);
                EventLog nextEvent = traceLogs.get(i + 1);
                long duration = currentEvent.getDuration(nextEvent);
                eventDurations.putIfAbsent(currentEvent.getEvent(), new ArrayList<>());
                eventDurations.get(currentEvent.getEvent()).add(duration);
            }
        });

        List<PerformanceAnalysis> analysisList = new ArrayList<>();
        eventDurations.forEach((event, durations) -> {
            double average = durations.stream().mapToLong(Long::longValue).average().orElse(0);
            long goodCount = durations.stream().filter(d -> d <= average).count();
            long poorCount = durations.size() - goodCount;

            PerformanceAnalysis analysis = new PerformanceAnalysis();
            analysis.setEvent(event);
            analysis.setGood((double) goodCount / durations.size() * 100);
            analysis.setPoor((double) poorCount / durations.size() * 100);

            analysisList.add(analysis);
        });

        return analysisList;
    }

    public static class PerformanceAnalysis {
        private String event;
        private double good;
        private double poor;

        public String getEvent() {
            return event;
        }

        public void setEvent(String event) {
            this.event = event;
        }

        public double getGood() {
            return good;
        }

        public void setGood(double good) {
            this.good = good;
        }

        public double getPoor() {
            return poor;
        }

        public void setPoor(double poor) {
            this.poor = poor;
        }
    }

    public List<String> getUniqueLifecycleTransitions() {
        return eventLogRepository.findAll().stream()
                .map(EventLog::getLifecycleTransition)
                .distinct()
                .collect(Collectors.toList());
    }

    public Map<String, SequenceInfo> getMaxSequences() {
        List<EventLog> logs = getAllEventLogs();

        // Calculate sequences and their frequencies
        Map<String, SequenceInfo> sequenceMap = new HashMap<>();
        for (Map.Entry<String, List<EventLog>> entry : logs.stream().collect(Collectors.groupingBy(EventLog::getTrace)).entrySet()) {
            List<EventLog> events = entry.getValue();
            events.sort(Comparator.comparing(EventLog::getTimestamp));
            StringBuilder sequenceBuilder = new StringBuilder();
            for (EventLog event : events) {
                if (sequenceBuilder.length() > 0) {
                    sequenceBuilder.append(" ==> ");
                }
                sequenceBuilder.append(event.getEvent());
            }
            String sequence = sequenceBuilder.toString();
            sequenceMap.putIfAbsent(sequence, new SequenceInfo(0, new ArrayList<>()));
            SequenceInfo info = sequenceMap.get(sequence);
            info.frequency++;
            info.traces.add(entry.getKey());
        }

        // Sort by sequence length in descending order
        return sequenceMap.entrySet()
                .stream()
                .sorted((e1, e2) -> Integer.compare(e2.getKey().length(), e1.getKey().length()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public Map<String, SequenceInfo> getMinSequences() {
        List<EventLog> logs = getAllEventLogs();

        // Calculate sequences and their frequencies
        Map<String, SequenceInfo> sequenceMap = new HashMap<>();
        for (Map.Entry<String, List<EventLog>> entry : logs.stream().collect(Collectors.groupingBy(EventLog::getTrace)).entrySet()) {
            List<EventLog> events = entry.getValue();
            events.sort(Comparator.comparing(EventLog::getTimestamp));
            StringBuilder sequenceBuilder = new StringBuilder();
            for (EventLog event : events) {
                if (sequenceBuilder.length() > 0) {
                    sequenceBuilder.append(" ==> ");
                }
                sequenceBuilder.append(event.getEvent());
            }
            String sequence = sequenceBuilder.toString();
            sequenceMap.putIfAbsent(sequence, new SequenceInfo(0, new ArrayList<>()));
            SequenceInfo info = sequenceMap.get(sequence);
            info.frequency++;
            info.traces.add(entry.getKey());
        }

        // Sort by sequence length in ascending order
        return sequenceMap.entrySet()
                .stream()
                .sorted((e1, e2) -> Integer.compare(e1.getKey().length(), e2.getKey().length()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public static class SequenceInfo {
        public int frequency;
        public List<String> traces;

        public SequenceInfo(int frequency, List<String> traces) {
            this.frequency = frequency;
            this.traces = traces;
        }
    }

    public List<EventLog> filterLogs(String trace, String event, String transition) {
        return eventLogRepository.findAll().stream()
                .filter(log -> (trace == null || log.getTrace().equals(trace)) &&
                        (event == null || log.getEvent().equals(event)) &&
                        (transition == null || log.getLifecycleTransition().equals(transition)))
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAllEventLogs() {
        eventLogRepository.deleteAll();
    }

    public EventLog parseEvent(Element eventElement) {
        EventLog eventLog = new EventLog();

        eventLog.setNote(getStringValue(eventElement, "note"));
        eventLog.setEventId(getStringValue(eventElement, "eventid"));
        eventLog.setActivity(getStringValue(eventElement, "activity"));
        eventLog.setDocId(getStringValue(eventElement, "docid"));
        eventLog.setSubprocess(getStringValue(eventElement, "subprocess"));
        eventLog.setTimestamp(getStringValue(eventElement, "time:timestamp"));
        eventLog.setIdentityId(getStringValue(eventElement, "identity:id"));
        eventLog.setDocType(getStringValue(eventElement, "doctype"));
        eventLog.setDocIdUuid(getStringValue(eventElement, "docid_uuid"));
        eventLog.setPerformer(getStringValue(eventElement, "org:resource"));
        eventLog.setSuccess(getBooleanValue(eventElement, "success"));
        eventLog.setLifecycleTransition(getStringValue(eventElement, "lifecycle:transition"));

        return eventLog;
    }

    private String getStringValue(Element eventElement, String key) {
        NodeList nodeList = eventElement.getElementsByTagName("string");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element element = (Element) nodeList.item(i);
            if (element.getAttribute("key").equals(key)) {
                return element.getAttribute("value");
            }
        }
        nodeList = eventElement.getElementsByTagName("date");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element element = (Element) nodeList.item(i);
            if (element.getAttribute("key").equals(key)) {
                return element.getAttribute("value");
            }
        }
        nodeList = eventElement.getElementsByTagName("id");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element element = (Element) nodeList.item(i);
            if (element.getAttribute("key").equals(key)) {
                return element.getAttribute("value");
            }
        }
        return null;
    }

    private boolean getBooleanValue(Element eventElement, String key) {
        NodeList nodeList = eventElement.getElementsByTagName("boolean");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element element = (Element) nodeList.item(i);
            if (element.getAttribute("key").equals(key)) {
                return Boolean.parseBoolean(element.getAttribute("value"));
            }
        }
        return false;
    }

    public List<EventLog> parseEventsFromXml(String xml) {
        List<EventLog> eventLogs = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new ByteArrayInputStream(xml.getBytes()));

            NodeList nodeList = document.getElementsByTagName("event");
            for (int i = 0; i < nodeList.getLength(); i++) {
                Element eventElement = (Element) nodeList.item(i);
                EventLog eventLog = parseEvent(eventElement);
                eventLogs.add(eventLog);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return eventLogs;
    }
}