package jasper.config;

import com.zaxxer.hikari.HikariDataSource;
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

/**
 * CRaC Resource Manager for handling database connection lifecycle during checkpoint/restore.
 * 
 * This component explicitly closes database connections before CRaC checkpoint to prevent
 * CheckpointOpenSocketException errors. Connections are automatically recreated after restore.
 */
@Component
@ConditionalOnClass(name = "org.crac.Core")
public class CracResourceManager implements Resource {

    private static final Logger logger = LoggerFactory.getLogger(CracResourceManager.class);
    
    private final DataSource dataSource;
    
    public CracResourceManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @PostConstruct
    public void init() {
        try {
            // Register this resource with CRaC global context
            Core.getGlobalContext().register(this);
            logger.info("Registered CRaC resource manager for database connections");
        } catch (Exception e) {
            logger.warn("Failed to register CRaC resource manager: {}", e.getMessage());
        }
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) throws Exception {
        logger.info("CRaC beforeCheckpoint: Closing database connections");
        
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
            
            if (!hikariDataSource.isClosed()) {
                int activeConnections = hikariDataSource.getHikariPoolMXBean().getActiveConnections();
                int idleConnections = hikariDataSource.getHikariPoolMXBean().getIdleConnections();
                
                logger.info("Closing HikariCP pool - Active: {}, Idle: {}", activeConnections, idleConnections);
                
                // Evict all connections to close sockets
                hikariDataSource.getHikariPoolMXBean().softEvictConnections();
                
                // Give connections time to close
                Thread.sleep(100);
                
                logger.info("HikariCP connections evicted for checkpoint");
            } else {
                logger.debug("HikariCP pool already closed");
            }
        } else {
            logger.warn("DataSource is not HikariDataSource (type: {}), may not close properly", 
                dataSource.getClass().getName());
        }
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) throws Exception {
        logger.info("CRaC afterRestore: Database connections will be recreated on demand");
        // HikariCP will automatically create new connections when needed
    }
}
