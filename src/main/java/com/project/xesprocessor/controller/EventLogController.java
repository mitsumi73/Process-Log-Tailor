package com.project.xesprocessor.controller;

import com.project.xesprocessor.model.EventLog;
import com.project.xesprocessor.service.EventLogService;
import org.apache.poi.ss.usermodel.Workbook;
import org.deckfour.xes.in.XesXmlParser;
import org.deckfour.xes.model.XAttributeMap;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XLog;
import org.deckfour.xes.model.XTrace;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Controller
public class EventLogController {

    @Autowired
    private EventLogService eventLogService;

    private String uploadedFileName;

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/upload")
    public String uploadPage() {
        return "upload";
    }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file, Model model) {
        try {
            // Delete old event logs before processing the new file
            eventLogService.deleteAllEventLogs();

            uploadedFileName = file.getOriginalFilename();
            File convFile = new File(System.getProperty("java.io.tmpdir") + "/" + uploadedFileName);
            file.transferTo(convFile);

            InputStream inputStream = new FileInputStream(convFile);
            XesXmlParser parser = new XesXmlParser();
            List<XLog> logs = parser.parse(inputStream);

            ExecutorService executorService = Executors.newFixedThreadPool(10); // Adjust the number of threads as needed
            List<EventLog> eventLogsBatch = new ArrayList<>();
            int batchSize = 1000; // Adjust batch size as needed

            for (XLog log : logs) {
                for (XTrace trace : log) {
                    String traceName = trace.getAttributes().get("concept:name").toString();
                    for (XEvent event : trace) {
                        XAttributeMap attributes = event.getAttributes();
                        EventLog eventLog = new EventLog();
                        eventLog.setTrace(traceName);
                        eventLog.setEvent(attributes.get("concept:name").toString());
                        eventLog.setTimestamp(attributes.get("time:timestamp").toString());
                        eventLog.setLifecycleTransition(attributes.get("lifecycle:transition").toString());
                        eventLog.setPerformer(attributes.get("org:resource") != null ? attributes.get("org:resource").toString() : null);
                        eventLog.setNote(attributes.get("note") != null ? attributes.get("note").toString() : null);
                        eventLog.setEventId(attributes.get("eventid") != null ? attributes.get("eventid").toString() : null);
                        eventLog.setActivity(attributes.get("activity") != null ? attributes.get("activity").toString() : null);
                        eventLog.setDocId(attributes.get("docid") != null ? attributes.get("docid").toString() : null);
                        eventLog.setSubprocess(attributes.get("subprocess") != null ? attributes.get("subprocess").toString() : null);
                        eventLog.setIdentityId(attributes.get("identity:id") != null ? attributes.get("identity:id").toString() : null);
                        eventLog.setDocType(attributes.get("doctype") != null ? attributes.get("doctype").toString() : null);
                        eventLog.setDocIdUuid(attributes.get("docid_uuid") != null ? attributes.get("docid_uuid").toString() : null);
                        eventLog.setSuccess(attributes.get("success") != null ? Boolean.parseBoolean(attributes.get("success").toString()) : false);

                        eventLogsBatch.add(eventLog);

                        if (eventLogsBatch.size() >= batchSize) {
                            List<EventLog> batchToProcess = new ArrayList<>(eventLogsBatch);
                            executorService.submit(() -> eventLogService.saveEventLogs(batchToProcess));
                            eventLogsBatch.clear();
                        }
                    }
                }
            }

            // Process any remaining logs
            if (!eventLogsBatch.isEmpty()) {
                executorService.submit(() -> eventLogService.saveEventLogs(eventLogsBatch));
            }

            executorService.shutdown();
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/summary";
    }

    @GetMapping("/summary")
    public String summary(Model model) {
        Map<String, Object> summaryInfo = eventLogService.getSummaryInformation(uploadedFileName);
        model.addAttribute("summaryInfo", summaryInfo);
        return "summary";
    }

    @GetMapping("/details")
    public String getDetails(@RequestParam(value = "filter", required = false) String filter, Model model) {
        List<EventLog> logs;
        if (filter != null) {
            switch (filter) {
                case "min-date":
                    logs = eventLogService.getAllEventLogs().stream()
                            .filter(log -> log.getTimestamp() != null)
                            .collect(Collectors.toList());
                    break;
                case "max-length":
                case "min-length":
                default:
                    logs = eventLogService.getAllEventLogs();
                    break;
            }
        } else {
            logs = eventLogService.getAllEventLogs();
        }

        List<String> uniqueTraces = eventLogService.getUniqueTraces();
        List<String> uniqueEvents = logs.stream().map(EventLog::getEvent).distinct().collect(Collectors.toList());
        List<String> uniqueTransitions = eventLogService.getUniqueLifecycleTransitions();

        model.addAttribute("logs", logs);
        model.addAttribute("uniqueTraces", uniqueTraces);
        model.addAttribute("uniqueEvents", uniqueEvents);
        model.addAttribute("uniqueTransitions", uniqueTransitions);

        return "details";
    }

    @GetMapping("/details/filter")
    public String filter(@RequestParam(required = false) String trace,
                         @RequestParam(required = false) String event,
                         @RequestParam(required = false) String transition,
                         Model model) {
        List<EventLog> logs = eventLogService.filterLogs(trace, event, transition);
        model.addAttribute("logs", logs);
        model.addAttribute("trace", trace);
        model.addAttribute("event", event);
        model.addAttribute("transition", transition);
        model.addAttribute("uniqueEvents", eventLogService.getUniqueEvents());
        model.addAttribute("uniqueTransitions", eventLogService.getUniqueLifecycleTransitions());
        return "details";
    }

    @GetMapping("/details/download")
    public void downloadExcel(@RequestParam("selectedLogs") List<Long> selectedLogIds, HttpServletResponse response) throws IOException {
        List<EventLog> selectedLogs = selectedLogIds.stream()
                .map(eventLogService::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        Workbook workbook = eventLogService.generateSelectedExcelReport(selectedLogs);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=selected_event_logs.xlsx");
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    @GetMapping("/select")
    public String selectLogs(@RequestParam(value = "selectedLogs", required = false) List<Long> selectedLogs, Model model) {
        List<EventLog> selectedEventLogs = eventLogService.getAllEventLogs().stream()
                .filter(log -> selectedLogs.contains(log.getId()))
                .collect(Collectors.toList());

        model.addAttribute("selectedLogs", selectedEventLogs);
        return "select";
    }

    @GetMapping("/charts")
    public String charts(Model model) {
        return "charts";
    }

    @GetMapping("/chart-data")
    @ResponseBody
    public Map<String, Object> getChartData(@RequestParam("chart") String chartType) {
        Map<String, Object> data = new HashMap<>();
        if ("event-frequency".equals(chartType)) {
            List<EventLog> logs = eventLogService.getAllEventLogs();
            Map<String, Long> eventFrequency = logs.stream()
                    .collect(Collectors.groupingBy(EventLog::getEvent, Collectors.counting()));

            data.put("labels", eventFrequency.keySet());
            data.put("values", eventFrequency.values());
        }
        return data;
    }

    @GetMapping("/se-frequency")
    public String sequenceFrequency(Model model) {
        Map<String, Long> sequenceFrequency = eventLogService.calculateSequenceFrequency();

        Map.Entry<String, Long> mostCommonSequence = sequenceFrequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElse(null);

        model.addAttribute("sequences", sequenceFrequency);
        model.addAttribute("mostCommonSequence", mostCommonSequence);

        return "se-frequency";
    }

    @GetMapping("/activities")
    public String activities(Model model) {
        List<EventLog> logs = eventLogService.getAllEventLogs();

        Map<String, Long> eventCountMap = logs.stream()
                .collect(Collectors.groupingBy(EventLog::getEvent, Collectors.counting()));

        List<String> uniqueEvents = logs.stream()
                .map(EventLog::getEvent)
                .distinct()
                .collect(Collectors.toList());

        List<EventActivity> eventActivities = new ArrayList<>();
        for (int i = 0; i < uniqueEvents.size(); i++) {
            eventActivities.add(new EventActivity(i + 1, uniqueEvents.get(i), "Activity"));
        }

        model.addAttribute("eventActivities", eventActivities);
        return "activities";
    }

    public static class EventActivity {
        private int order;
        private String name;
        private String type;

        public EventActivity(int order, String name, String type) {
            this.order = order;
            this.name = name;
            this.type = type;
        }

        public int getOrder() {
            return order;
        }

        public String getName() {
            return name;
        }

        public String getType() {
            return type;
        }
    }

    @GetMapping("/max-SF")
    public String getMaxSequences(Model model) {
        Map<String, EventLogService.SequenceInfo> maxSequences = eventLogService.getMaxSequences();
        model.addAttribute("maxSequences", maxSequences);
        return "max-SF";
    }

    @GetMapping("/min-SF")
    public String getMinSequences(Model model) {
        Map<String, EventLogService.SequenceInfo> minSequences = eventLogService.getMinSequences();
        model.addAttribute("minSequences", minSequences);
        return "min-SF";
    }

    @GetMapping("/performance")
    public String performanceAnalysis(Model model) {
        List<EventLogService.PerformanceAnalysis> analysis = eventLogService.getPerformanceAnalysis();
        model.addAttribute("performanceAnalysis", analysis);
        return "performance";
    }

    @GetMapping("/trace")
    public String getTracePage(Model model) {
        List<EventLog> logs = eventLogService.getAllEventLogs();
        model.addAttribute("logs", logs);
        return "trace";
    }

    @GetMapping("/trace/filter")
    public String filterTrace(@RequestParam(name = "trace", required = false, defaultValue = "") String trace,
                              @RequestParam(name = "event", required = false, defaultValue = "") String event,
                              Model model) {
        List<EventLog> logs = eventLogService.getEventLogsByTraceAndEvent(trace, event);
        model.addAttribute("logs", logs);
        return "trace";
    }
}