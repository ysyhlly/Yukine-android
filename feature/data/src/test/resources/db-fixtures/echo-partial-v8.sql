-- Representative partially upgraded legacy database: user tables exist, later playback/source tables do not.
CREATE TABLE tracks (id INTEGER PRIMARY KEY,title TEXT NOT NULL,artist TEXT NOT NULL,album TEXT NOT NULL,duration_ms INTEGER NOT NULL,content_uri TEXT NOT NULL,data_path TEXT NOT NULL,album_id INTEGER NOT NULL,album_art_uri TEXT NOT NULL,codec TEXT NOT NULL DEFAULT '',bitrate_kbps INTEGER NOT NULL DEFAULT 0,updated_at INTEGER NOT NULL DEFAULT 0);
CREATE TABLE favorites (track_id INTEGER PRIMARY KEY,created_at INTEGER NOT NULL DEFAULT 0);
CREATE TABLE play_history (track_id INTEGER PRIMARY KEY,played_at INTEGER NOT NULL DEFAULT 0,play_count INTEGER NOT NULL DEFAULT 1);
CREATE TABLE playlists (id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL UNIQUE,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL);
CREATE TABLE playlist_tracks (playlist_id INTEGER NOT NULL,track_id INTEGER NOT NULL,position INTEGER NOT NULL,added_at INTEGER NOT NULL,PRIMARY KEY (playlist_id, track_id));
CREATE TABLE settings (`key` TEXT PRIMARY KEY,value TEXT NOT NULL);
INSERT INTO tracks(id,title,artist,album,duration_ms,content_uri,data_path,album_id,album_art_uri,codec,bitrate_kbps,updated_at) VALUES(801,'First','Legacy Artist','Legacy Album',120000,'file:///first.mp3','/legacy/first.mp3',8,'','mp3',320,1700000000000);
INSERT INTO tracks(id,title,artist,album,duration_ms,content_uri,data_path,album_id,album_art_uri,codec,bitrate_kbps,updated_at) VALUES(802,'Second','Legacy Artist','Legacy Album',121000,'file:///second.mp3','/legacy/second.mp3',8,'','mp3',320,1700000000001);
INSERT INTO favorites(track_id,created_at) VALUES(801,1700000000000);
INSERT INTO play_history(track_id,played_at,play_count) VALUES(801,1700000000000,4);
INSERT INTO playlists(id,name,created_at,updated_at) VALUES(88,'Ordered Legacy',1700000000000,1700000000000);
INSERT INTO playlist_tracks(playlist_id,track_id,position,added_at) VALUES(88,802,0,1700000000000);
INSERT INTO playlist_tracks(playlist_id,track_id,position,added_at) VALUES(88,801,1,1700000000001);
INSERT INTO settings(`key`,value) VALUES('language_mode','zh-CN');
