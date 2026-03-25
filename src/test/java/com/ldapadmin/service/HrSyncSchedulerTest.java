package com.ldapadmin.service;

import com.ldapadmin.entity.DirectoryConnection;
import com.ldapadmin.entity.enums.HrProvider;
import com.ldapadmin.entity.hr.HrConnection;
import com.ldapadmin.repository.hr.HrConnectionRepository;
import com.ldapadmin.service.hr.HrSyncScheduler;
import com.ldapadmin.service.hr.HrSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class HrSyncSchedulerTest {

    @Mock private HrConnectionRepository connectionRepo;
    @Mock private HrSyncService syncService;

    private HrSyncScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new HrSyncScheduler(connectionRepo, syncService);
    }

    @Test
    void isDue_neverSynced_returnsTrue() {
        HrConnection conn = buildConnection();
        conn.setLastSyncAt(null);

        assertThat(scheduler.isDue(conn)).isTrue();
    }

    @Test
    void isDue_syncedLongAgo_returnsTrue() {
        HrConnection conn = buildConnection();
        conn.setSyncCron("0 * * * * *"); // every minute
        conn.setLastSyncAt(OffsetDateTime.now().minusHours(1));

        assertThat(scheduler.isDue(conn)).isTrue();
    }

    @Test
    void isDue_syncedJustNow_returnsFalse() {
        HrConnection conn = buildConnection();
        conn.setSyncCron("0 0 * * * *"); // every hour
        conn.setLastSyncAt(OffsetDateTime.now());

        assertThat(scheduler.isDue(conn)).isFalse();
    }

    @Test
    void isDue_invalidCron_returnsFalse() {
        HrConnection conn = buildConnection();
        conn.setSyncCron("not-a-cron");
        conn.setLastSyncAt(OffsetDateTime.now().minusDays(1));

        assertThat(scheduler.isDue(conn)).isFalse();
    }

    private HrConnection buildConnection() {
        DirectoryConnection dir = new DirectoryConnection();
        dir.setId(UUID.randomUUID());

        HrConnection conn = new HrConnection();
        conn.setId(UUID.randomUUID());
        conn.setDirectory(dir);
        conn.setProvider(HrProvider.BAMBOOHR);
        conn.setSyncCron("0 0 * * * ?");
        return conn;
    }
}
