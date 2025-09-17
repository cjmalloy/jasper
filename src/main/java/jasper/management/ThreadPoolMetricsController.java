package jasper.management;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import jasper.component.ScriptExecutorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller to expose thread pool metrics and statistics.
 * Provides detailed information about all thread pools in the application.
 */
@RestController
@RequestMapping("/management")
public class ThreadPoolMetricsController {

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private ScriptExecutorFactory scriptExecutorFactory;

    /**
     * Returns detailed metrics for all thread pools in the application.
     */
    @GetMapping("/threadpools")
    public Map<String, Object> threadPoolMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // Get metrics from Micrometer registry
        Map<String, Object> micrometerMetrics = new HashMap<>();
        
        // Collect executor metrics
        Search.in(meterRegistry)
            .name(name -> name.startsWith("executor."))
            .meters()
            .forEach(meter -> {
                String key = meter.getId().getName() + "_" + 
                    meter.getId().getTags().stream()
                        .filter(tag -> "name".equals(tag.getKey()))
                        .findFirst()
                        .map(tag -> tag.getValue())
                        .orElse("unknown");
                micrometerMetrics.put(key, meter.measure());
            });
        
        metrics.put("micrometer", micrometerMetrics);
        
        // Get dynamic script executor stats
        Map<String, ScriptExecutorFactory.ExecutorStats> scriptStats = scriptExecutorFactory.getExecutorStats();
        metrics.put("scriptExecutors", scriptStats);
        
        // Add summary statistics
        Map<String, Object> summary = new HashMap<>();
        summary.put("totalScriptExecutors", scriptStats.size());
        summary.put("totalActiveThreads", scriptStats.values().stream().mapToInt(ScriptExecutorFactory.ExecutorStats::activeCount).sum());
        summary.put("totalQueuedTasks", scriptStats.values().stream().mapToInt(ScriptExecutorFactory.ExecutorStats::queueSize).sum());
        summary.put("totalCompletedTasks", scriptStats.values().stream().mapToLong(ScriptExecutorFactory.ExecutorStats::completedTaskCount).sum());
        
        metrics.put("summary", summary);
        
        return metrics;
    }
}