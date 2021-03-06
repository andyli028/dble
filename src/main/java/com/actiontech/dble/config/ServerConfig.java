/*
* Copyright (C) 2016-2018 ActionTech.
* based on code by MyCATCopyrightHolder Copyright (c) 2013, OpenCloudDB/MyCAT.
* License: http://www.gnu.org/licenses/gpl.html GPL version 2 or higher.
*/
package com.actiontech.dble.config;

import com.actiontech.dble.DbleServer;
import com.actiontech.dble.backend.datasource.PhysicalDBNode;
import com.actiontech.dble.backend.datasource.PhysicalDBPool;
import com.actiontech.dble.backend.datasource.PhysicalDatasource;
import com.actiontech.dble.config.model.*;
import com.actiontech.dble.config.util.ConfigException;
import com.actiontech.dble.config.util.ConfigUtil;
import com.actiontech.dble.alarm.AlarmCode;
import com.actiontech.dble.alarm.Alert;
import com.actiontech.dble.alarm.AlertUtil;
import com.actiontech.dble.route.sequence.handler.DistributedSequenceHandler;
import com.actiontech.dble.route.sequence.handler.IncrSequenceMySQLHandler;
import com.actiontech.dble.route.sequence.handler.IncrSequenceTimeHandler;
import com.actiontech.dble.route.sequence.handler.IncrSequenceZKHandler;
import com.actiontech.dble.server.variables.SystemVariables;
import com.actiontech.dble.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLNonTransientException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author mycat
 */
public class ServerConfig {
    protected static final Logger LOGGER = LoggerFactory.getLogger(ServerConfig.class);
    private static final int RELOAD = 1;
    private static final int ROLLBACK = 2;
    private static final int RELOAD_ALL = 3;

    private volatile SystemConfig system;
    private volatile FirewallConfig firewall;
    private volatile FirewallConfig firewall2;
    private volatile Map<String, UserConfig> users;
    private volatile Map<String, UserConfig> users2;
    private volatile Map<String, SchemaConfig> schemas;
    private volatile Map<String, SchemaConfig> schemas2;
    private volatile Map<String, PhysicalDBNode> dataNodes;
    private volatile Map<String, PhysicalDBNode> dataNodes2;
    private volatile Map<String, PhysicalDBPool> dataHosts;
    private volatile Map<String, PhysicalDBPool> dataHosts2;
    private volatile Map<ERTable, Set<ERTable>> erRelations;
    private volatile Map<ERTable, Set<ERTable>> erRelations2;
    private volatile boolean dataHostWithoutWR;
    private volatile boolean dataHostWithoutWR2;
    private volatile long reloadTime;
    private volatile long rollbackTime;
    private volatile int status;
    private volatile boolean changing = false;
    private final ReentrantLock lock;

    public ServerConfig() {
        //read schema.xml,rule.xml and server.xml
        ConfigInitializer confInit = new ConfigInitializer(true, false);
        this.system = confInit.getSystem();
        this.users = confInit.getUsers();
        this.schemas = confInit.getSchemas();
        this.dataHosts = confInit.getDataHosts();
        this.dataNodes = confInit.getDataNodes();
        this.erRelations = confInit.getErRelations();
        this.dataHostWithoutWR = confInit.isDataHostWithoutWH();
        ConfigUtil.setSchemasForPool(dataHosts, dataNodes);

        this.firewall = confInit.getFirewall();

        this.reloadTime = TimeUtil.currentTimeMillis();
        this.rollbackTime = -1L;
        this.status = RELOAD;

        this.lock = new ReentrantLock();
        try {
            confInit.testConnection(true);
        } catch (ConfigException e) {
            LOGGER.warn("TestConnection fail", e);
            AlertUtil.alertSelf(AlarmCode.TEST_CONN_FAIL, Alert.AlertLevel.WARN, "TestConnection fail:" + e.getMessage(), null);
        }
    }


    public ServerConfig(ConfigInitializer confInit) {
        //read schema.xml,rule.xml and server.xml
        this.system = confInit.getSystem();
        this.users = confInit.getUsers();
        this.schemas = confInit.getSchemas();
        this.dataHosts = confInit.getDataHosts();
        this.dataNodes = confInit.getDataNodes();
        this.erRelations = confInit.getErRelations();
        this.dataHostWithoutWR = confInit.isDataHostWithoutWH();
        ConfigUtil.setSchemasForPool(dataHosts, dataNodes);

        this.firewall = confInit.getFirewall();

        this.reloadTime = TimeUtil.currentTimeMillis();
        this.rollbackTime = -1L;
        this.status = RELOAD;

        this.lock = new ReentrantLock();
    }

    private void waitIfChanging() {
        while (changing) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        }
    }

    public SystemConfig getSystem() {
        waitIfChanging();
        return system;
    }

    public boolean isDataHostWithoutWR() {
        waitIfChanging();
        return dataHostWithoutWR;
    }

    public Map<String, UserConfig> getUsers() {
        waitIfChanging();
        return users;
    }

    public Map<String, UserConfig> getBackupUsers() {
        waitIfChanging();
        return users2;
    }

    public Map<String, SchemaConfig> getSchemas() {
        waitIfChanging();
        return schemas;
    }

    public Map<String, SchemaConfig> getBackupSchemas() {
        waitIfChanging();
        return schemas2;
    }

    public Map<String, PhysicalDBNode> getDataNodes() {
        waitIfChanging();
        return dataNodes;
    }


    public Map<String, PhysicalDBNode> getBackupDataNodes() {
        waitIfChanging();
        return dataNodes2;
    }

    public Map<String, PhysicalDBPool> getDataHosts() {
        waitIfChanging();
        return dataHosts;
    }

    public Map<String, PhysicalDBPool> getBackupDataHosts() {
        waitIfChanging();
        return dataHosts2;
    }

    public Map<ERTable, Set<ERTable>> getErRelations() {
        waitIfChanging();
        return erRelations;
    }

    public Map<ERTable, Set<ERTable>> getBackupErRelations() {
        waitIfChanging();
        return erRelations2;
    }

    public FirewallConfig getFirewall() {
        waitIfChanging();
        return firewall;
    }

    public FirewallConfig getBackupFirewall() {
        waitIfChanging();
        return firewall2;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public long getReloadTime() {
        waitIfChanging();
        return reloadTime;
    }

    public long getRollbackTime() {
        waitIfChanging();
        return rollbackTime;
    }

    public boolean backDataHostWithoutWR() {
        waitIfChanging();
        return dataHostWithoutWR2;
    }

    public void reload(Map<String, UserConfig> newUsers, Map<String, SchemaConfig> newSchemas,
                       Map<String, PhysicalDBNode> newDataNodes, Map<String, PhysicalDBPool> newDataHosts,
                       Map<ERTable, Set<ERTable>> newErRelations, FirewallConfig newFirewall,
                       SystemVariables newSystemVariables, boolean newDataHostWithoutWR, boolean reloadAll) throws SQLNonTransientException {

        apply(newUsers, newSchemas, newDataNodes, newDataHosts, newErRelations, newFirewall,
                newSystemVariables, newDataHostWithoutWR, reloadAll);
        this.reloadTime = TimeUtil.currentTimeMillis();
        this.status = reloadAll ? RELOAD_ALL : RELOAD;
    }

    public boolean canRollbackAll() {
        return status == RELOAD_ALL && users2 != null && schemas2 != null && firewall2 != null && dataNodes2 != null && dataHosts2 != null;
    }

    public boolean canRollback() {
        return status == RELOAD && users2 != null && schemas2 != null && firewall2 != null;
    }

    public void rollback(Map<String, UserConfig> backupUsers, Map<String, SchemaConfig> backupSchemas,
                         Map<String, PhysicalDBNode> backupDataNodes, Map<String, PhysicalDBPool> backupDataHosts,
                         Map<ERTable, Set<ERTable>> backupErRelations, FirewallConfig backFirewall, boolean backDataHostWithoutWR) throws SQLNonTransientException {

        apply(backupUsers, backupSchemas, backupDataNodes, backupDataHosts, backupErRelations, backFirewall,
                DbleServer.getInstance().getSystemVariables(), backDataHostWithoutWR, status == RELOAD_ALL);
        this.rollbackTime = TimeUtil.currentTimeMillis();
        this.status = ROLLBACK;
    }

    private void apply(Map<String, UserConfig> newUsers,
                       Map<String, SchemaConfig> newSchemas,
                       Map<String, PhysicalDBNode> newDataNodes,
                       Map<String, PhysicalDBPool> newDataHosts,
                       Map<ERTable, Set<ERTable>> newErRelations,
                       FirewallConfig newFirewall, SystemVariables newSystemVariables,
                       boolean newDataHostWithoutWR, boolean isLoadAll) throws SQLNonTransientException {
        final ReentrantLock metaLock = DbleServer.getInstance().getTmManager().getMetaLock();
        metaLock.lock();
        this.changing = true;
        try {
            if (DbleServer.getInstance().getTmManager().getMetaCount() != 0) {
                String msg = "There is other session is doing DDL";
                LOGGER.warn(msg);
                throw new SQLNonTransientException(msg, "HY000", ErrorCode.ER_DOING_DDL);
            }
            // old data host
            // 1 stop heartbeat
            // 2 backup
            //--------------------------------------------
            if (isLoadAll) {
                Map<String, PhysicalDBPool> oldDataHosts = this.dataHosts;
                if (oldDataHosts != null) {
                    for (PhysicalDBPool oldDbPool : oldDataHosts.values()) {
                        if (oldDbPool != null) {
                            oldDbPool.stopHeartbeat();
                        }
                    }
                }
                this.dataNodes2 = this.dataNodes;
                this.dataHosts2 = this.dataHosts;
            }

            this.users2 = this.users;
            this.schemas2 = this.schemas;
            this.firewall2 = this.firewall;
            this.erRelations2 = this.erRelations;
            this.dataHostWithoutWR2 = this.dataHostWithoutWR;
            // TODO:comment BY huqing.yan and will reopen later
            //if (!isLoadAll) {
            //    DsDiff diff = dsdiff(newDataHosts);
            //    diff.apply();
            //}

            // new data host
            // 1 start heartbeat
            // 2 apply the configure
            //---------------------------------------------------
            if (isLoadAll) {
                if (newDataHosts != null) {
                    for (PhysicalDBPool newDbPool : newDataHosts.values()) {
                        if (newDbPool != null && !newDataHostWithoutWR) {
                            DbleServer.getInstance().saveDataHostIndex(newDbPool.getHostName(), newDbPool.getActiveIndex(),
                                    this.system.isUseZKSwitch() && DbleServer.getInstance().isUseZK());
                            newDbPool.startHeartbeat();
                        }
                    }
                }
                this.dataNodes = newDataNodes;
                this.dataHosts = newDataHosts;
                this.dataHostWithoutWR = newDataHostWithoutWR;
                DbleServer.getInstance().reloadSystemVariables(newSystemVariables);
                DbleServer.getInstance().getCacheService().reloadCache(newSystemVariables.isLowerCaseTableNames());
                DbleServer.getInstance().getRouterService().loadTableId2DataNodeCache(DbleServer.getInstance().getCacheService());
            }
            this.users = newUsers;
            this.schemas = newSchemas;
            this.firewall = newFirewall;
            this.erRelations = newErRelations;
            DbleServer.getInstance().getCacheService().clearCache();
            this.changing = false;
            if (!newDataHostWithoutWR) {
                DbleServer.getInstance().reloadMetaData(this);
            }
        } finally {
            this.changing = false;
            metaLock.unlock();
        }
    }

    public void simplyApply(Map<String, UserConfig> newUsers,
                            Map<String, SchemaConfig> newSchemas,
                            Map<String, PhysicalDBNode> newDataNodes,
                            Map<String, PhysicalDBPool> newDataHosts,
                            Map<ERTable, Set<ERTable>> newErRelations,
                            FirewallConfig newFirewall) {
        this.users = newUsers;
        this.schemas = newSchemas;
        this.dataNodes = newDataNodes;
        this.dataHosts = newDataHosts;
        this.firewall = newFirewall;
        this.erRelations = newErRelations;
    }

    private DsDiff dsdiff(Map<String, PhysicalDBPool> newDataHosts) {
        DsDiff diff = new DsDiff();
        // deleted datasource
        for (PhysicalDBPool opool : dataHosts.values()) {
            PhysicalDBPool npool = newDataHosts.get(opool.getHostName());
            if (npool == null) {
                LOGGER.info("reload -delete- failed, use old datasources ");
                return null;
            }

            Map<Integer, PhysicalDatasource[]> odss = opool.getReadSources();
            Map<Integer, PhysicalDatasource[]> ndss = npool.getReadSources();
            Map<Integer, ArrayList<PhysicalDatasource>> idel = new HashMap<>(2);
            boolean haveOne = false;
            for (Map.Entry<Integer, PhysicalDatasource[]> oentry : odss.entrySet()) {
                boolean doadd = false;
                ArrayList<PhysicalDatasource> del = new ArrayList<>();
                for (PhysicalDatasource ods : oentry.getValue()) {
                    boolean dodel = true;
                    for (Map.Entry<Integer, PhysicalDatasource[]> nentry : ndss.entrySet()) {
                        for (PhysicalDatasource nds : nentry.getValue()) {
                            if (ods.getName().equals(nds.getName())) {
                                dodel = false;
                                break;
                            }
                        }
                        if (!dodel) {
                            break;
                        }
                    }
                    if (dodel) {
                        del.add(ods);
                        doadd = true;
                    }
                }
                if (doadd) {
                    idel.put(oentry.getKey(), del);
                    haveOne = true;
                }
            }
            if (haveOne) {
                diff.deled.put(opool, idel);
            }
        }

        // added datasource
        if (addedDatasource(newDataHosts, diff)) return null;

        return diff;
    }

    private boolean addedDatasource(Map<String, PhysicalDBPool> newDataHosts, DsDiff diff) {
        for (PhysicalDBPool npool : newDataHosts.values()) {
            PhysicalDBPool opool = dataHosts.get(npool.getHostName());
            if (opool == null) {
                LOGGER.warn("reload -add- failed, use old datasources ");
                return true;
            }

            Map<Integer, PhysicalDatasource[]> ndss = npool.getReadSources();
            Map<Integer, PhysicalDatasource[]> odss = opool.getReadSources();
            Map<Integer, ArrayList<PhysicalDatasource>> iadd =
                    new HashMap<>(2);
            boolean haveOne = false;
            for (Map.Entry<Integer, PhysicalDatasource[]> nentry : ndss.entrySet()) {
                boolean doadd = false;
                ArrayList<PhysicalDatasource> add = new ArrayList<>();
                for (PhysicalDatasource nds : nentry.getValue()) {
                    boolean isExist = false;
                    for (Map.Entry<Integer, PhysicalDatasource[]> oentry : odss.entrySet()) {
                        for (PhysicalDatasource ods : oentry.getValue()) {
                            if (nds.getName().equals(ods.getName())) {
                                isExist = true;
                                break;
                            }
                        }
                        if (isExist) {
                            break;
                        }
                    }
                    if (!isExist) {
                        add.add(nds);
                        doadd = true;
                    }
                }
                if (doadd) {
                    iadd.put(nentry.getKey(), add);
                    haveOne = true;
                }
            }
            if (haveOne) {
                diff.added.put(opool, iadd);
            }
        }
        return false;
    }


    /**
     * turned all the config into lowerCase config
     */
    public void reviseLowerCase() {

        //user schema
        for (UserConfig uc : users.values()) {
            if (uc.getPrivilegesConfig() != null) {
                uc.getPrivilegesConfig().changeMapToLowerCase();
                uc.changeMapToLowerCase();
            }
        }

        //dataNode
        for (PhysicalDBNode physicalDBNode : dataNodes.values()) {
            physicalDBNode.toLowerCase();
        }

        //schemas
        Map<String, SchemaConfig> newSchemas = new HashMap<>();
        for (Map.Entry<String, SchemaConfig> entry : schemas.entrySet()) {
            SchemaConfig newSchema = new SchemaConfig(entry.getValue());
            newSchemas.put(entry.getKey().toLowerCase(), newSchema);
        }
        this.schemas = newSchemas;
        loadSequence();
        selfChecking0();

    }

    public void loadSequence() {
        //load global sequence
        if (system.getSequnceHandlerType() == SystemConfig.SEQUENCE_HANDLER_MYSQL) {
            IncrSequenceMySQLHandler.getInstance().load(DbleServer.getInstance().getSystemVariables().isLowerCaseTableNames());
        }

        if (system.getSequnceHandlerType() == SystemConfig.SEQUENCE_HANDLER_LOCAL_TIME) {
            IncrSequenceTimeHandler.getInstance().load();
        }

        if (system.getSequnceHandlerType() == SystemConfig.SEQUENCE_HANDLER_ZK_DISTRIBUTED) {
            DistributedSequenceHandler.getInstance().load();
        }

        if (system.getSequnceHandlerType() == SystemConfig.SEQUENCE_HANDLER_ZK_GLOBAL_INCREMENT) {
            IncrSequenceZKHandler.getInstance().load(DbleServer.getInstance().getSystemVariables().isLowerCaseTableNames());
        }
    }

    public void selfChecking0() throws ConfigException {
        // check 1.user's schemas are all existed in schema's conf
        // 2.schema's conf is not empty
        if (users == null || users.isEmpty()) {
            throw new ConfigException("SelfCheck### user all node is empty!");
        } else {
            for (UserConfig uc : users.values()) {
                if (uc == null) {
                    throw new ConfigException("SelfCheck### users node within the item is empty!");
                }
                if (!uc.isManager()) {
                    Set<String> authSchemas = uc.getSchemas();
                    if (authSchemas == null) {
                        throw new ConfigException("SelfCheck### user " + uc.getName() + "referred schemas is empty!");
                    }
                    for (String schema : authSchemas) {
                        if (!schemas.containsKey(schema)) {
                            String errMsg = "SelfCheck###  schema " + schema + " referred by user " + uc.getName() + " is not exist!";
                            throw new ConfigException(errMsg);
                        }
                    }
                }
            }
        }

        // check schema
        for (SchemaConfig sc : schemas.values()) {
            if (null == sc) {
                throw new ConfigException("SelfCheck### schema all node is empty!");
            } else {
                // check dataNode / dataHost
                if (this.dataNodes != null && this.dataHosts != null) {
                    Set<String> dataNodeNames = sc.getAllDataNodes();
                    for (String dataNodeName : dataNodeNames) {
                        PhysicalDBNode node = this.dataNodes.get(dataNodeName);
                        if (node == null) {
                            throw new ConfigException("SelfCheck### schema dataNode is empty!");
                        }
                    }
                }
            }
        }

    }

    private static class DsDiff {
        private Map<PhysicalDBPool, Map<Integer, ArrayList<PhysicalDatasource>>> deled;
        private Map<PhysicalDBPool, Map<Integer, ArrayList<PhysicalDatasource>>> added;

        DsDiff() {
            deled = new HashMap<>(2);
            added = new HashMap<>(2);
        }

        public void apply() {
            // delete
            for (Map.Entry<PhysicalDBPool, Map<Integer, ArrayList<PhysicalDatasource>>> lentry : deled.entrySet()) {
                for (Map.Entry<Integer, ArrayList<PhysicalDatasource>> llentry : lentry.getValue().entrySet()) {
                    for (int i = 0; i < llentry.getValue().size(); i++) {
                        // lentry.getKey().delRDs(llentry.getValue().get(i));
                        llentry.getValue().get(i).setDying();
                    }
                }
            }

            // add
            for (Map.Entry<PhysicalDBPool, Map<Integer, ArrayList<PhysicalDatasource>>> lentry : added.entrySet()) {
                for (Map.Entry<Integer, ArrayList<PhysicalDatasource>> llentry : lentry.getValue().entrySet()) {
                    for (int i = 0; i < llentry.getValue().size(); i++) {
                        lentry.getKey().addRDs(llentry.getKey(), llentry.getValue().get(i));
                    }
                }
            }

            // sleep
            ArrayList<PhysicalDatasource> killed = new ArrayList<>(2);
            for (Map.Entry<PhysicalDBPool, Map<Integer, ArrayList<PhysicalDatasource>>> lentry : deled.entrySet()) {
                for (Map.Entry<Integer, ArrayList<PhysicalDatasource>> llentry : lentry.getValue().entrySet()) {
                    for (int i = 0; i < llentry.getValue().size(); i++) {
                        if (llentry.getValue().get(i).getActiveCount() != 0) {
                            killed.add(llentry.getValue().get(i));
                        }
                    }
                }
            }
            if (!killed.isEmpty()) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {
                    //ignore error
                }

                for (PhysicalDatasource aKilled : killed) {
                    if (aKilled.getActiveCount() != 0) {
                        aKilled.clearConsByDying();
                    }
                }
            }
        }

        public Map<PhysicalDBPool, Map<Integer, ArrayList<PhysicalDatasource>>> getDeled() {
            return deled;
        }

        public void setDeled(Map<PhysicalDBPool, Map<Integer, ArrayList<PhysicalDatasource>>> deled) {
            this.deled = deled;
        }

        public Map<PhysicalDBPool, Map<Integer, ArrayList<PhysicalDatasource>>> getAdded() {
            return added;
        }

        public void setAdded(Map<PhysicalDBPool, Map<Integer, ArrayList<PhysicalDatasource>>> added) {
            this.added = added;
        }
    }
}


