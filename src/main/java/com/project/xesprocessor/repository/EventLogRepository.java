package com.project.xesprocessor.repository;

import com.project.xesprocessor.model.EventLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {
    List<EventLog> findByTraceAndEvent(String trace, String event);
    List<EventLog> findByTrace(String trace);
    List<EventLog> findByEvent(String event);

    @Query("SELECT DISTINCT e.event FROM EventLog e")
    List<String> findDistinctEvents();

    @Query("SELECT DISTINCT e.lifecycleTransition FROM EventLog e")
    List<String> findDistinctTransitions();
}