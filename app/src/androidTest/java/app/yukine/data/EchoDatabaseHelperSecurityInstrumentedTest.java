package app.yukine.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import app.yukine.model.RemoteSource;

@RunWith(AndroidJUnit4.class)
public final class EchoDatabaseHelperSecurityInstrumentedTest {
    private static final String DATABASE_NAME = "echo_db_security_instrumented_test.db";

    private Context context;
    private EchoDatabaseHelper database;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE_NAME);
        database = new EchoDatabaseHelper(context, DATABASE_NAME);
        database.getWritableDatabase();
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
        if (context != null) {
            context.deleteDatabase(DATABASE_NAME);
        }
    }

    @Test
    public void saveRemoteSourceEncryptsPasswordAtRestAndReadsPlainPassword() {
        RemoteSource source = new RemoteSource(
                0L,
                "webdav",
                "Test DAV",
                "https://dav.example.com",
                "alice",
                "dav-secret",
                "/music",
                "",
                0L
        );

        long sourceId = database.saveRemoteSource(source);
        assertTrue(sourceId > 0L);

        String rawPassword = readPasswordColumn(sourceId);
        RemoteSource loaded = database.loadRemoteSource(sourceId);

        assertNotNull(rawPassword);
        assertNotEquals("dav-secret", rawPassword);
        assertEquals("dav-secret", loaded.password);
    }

    private String readPasswordColumn(long sourceId) {
        try (Cursor cursor = database.getReadableDatabase().query(
                "remote_sources",
                new String[]{"password"},
                "id = ?",
                new String[]{String.valueOf(sourceId)},
                null,
                null,
                null
        )) {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return cursor.getString(0);
        }
    }
}
