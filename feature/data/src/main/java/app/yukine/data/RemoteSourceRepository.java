package app.yukine.data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import app.yukine.data.room.RemoteSourceDao;
import app.yukine.data.room.RemoteSourceEntity;
import app.yukine.data.room.YukineDatabase;
import app.yukine.model.RemoteSource;
import app.yukine.model.Track;
import app.yukine.security.SecureSecretStore;

/** WebDAV source metadata and its cached-track lifecycle. */
public final class RemoteSourceRepository {
    private final YukineDatabase database;
    private final RemoteSourceDao dao;
    private final LibraryRepository libraryRepository;

    public RemoteSourceRepository(YukineDatabase database, LibraryRepository libraryRepository) {
        this.database = database;
        dao = database.remoteSourceDao();
        this.libraryRepository = libraryRepository;
    }

    public List<RemoteSource> loadSources() {
        ArrayList<RemoteSource> sources = new ArrayList<>();
        for (RemoteSourceEntity row : dao.loadSources()) {
            sources.add(source(row));
        }
        return sources;
    }

    public RemoteSource loadSource(long sourceId) {
        RemoteSourceEntity row = dao.loadSource(sourceId);
        return row == null ? null : source(row);
    }

    public long save(RemoteSource source) {
        if (source == null) {
            return -1L;
        }
        AtomicLong savedId = new AtomicLong(-1L);
        database.runInTransaction(() -> {
            long now = System.currentTimeMillis();
            if (source.id > 0L) {
                libraryRepository.deleteTracksByDataPathPattern(pattern(source.id));
                RemoteSourceEntity updated = entity(source, source.id, now);
                if (dao.update(updated) > 0) {
                    savedId.set(source.id);
                    return;
                }
            }
            savedId.set(dao.upsert(entity(source, null, now)));
        });
        return savedId.get();
    }

    public void updateStatus(long sourceId, String status) {
        dao.updateStatus(
                sourceId,
                status == null ? "" : status,
                System.currentTimeMillis()
        );
    }

    public List<Track> loadTracks(long sourceId) {
        return libraryRepository.loadTracksByDataPathPattern(pattern(sourceId));
    }

    public int replaceTracks(long sourceId, List<Track> tracks) {
        return libraryRepository.replaceTracksByDataPathPattern(pattern(sourceId), tracks);
    }

    public void delete(long sourceId) {
        database.runInTransaction(() -> {
            libraryRepository.deleteTracksByDataPathPattern(pattern(sourceId));
            dao.delete(sourceId);
        });
    }

    private static String pattern(long sourceId) {
        return "webdav:" + sourceId + ":%";
    }

    private static RemoteSourceEntity entity(RemoteSource source, Long id, long updatedAt) {
        String encrypted = SecureSecretStore.INSTANCE.encryptOrPlain(source.password);
        return new RemoteSourceEntity(
                id,
                source.type,
                source.name,
                source.baseUrl,
                source.username,
                encrypted == null ? "" : encrypted,
                source.rootPath,
                source.lastStatus,
                updatedAt
        );
    }

    private static RemoteSource source(RemoteSourceEntity row) {
        String password = SecureSecretStore.INSTANCE.decryptOrPlain(row.getPassword());
        return new RemoteSource(
                row.getId() == null ? -1L : row.getId(),
                row.getType(),
                row.getName(),
                row.getBaseUrl(),
                row.getUsername(),
                password == null ? "" : password,
                row.getRootPath(),
                row.getLastStatus(),
                row.getUpdatedAt()
        );
    }
}
