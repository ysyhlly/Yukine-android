//go:build windows

package mpv

import (
	"errors"
	"net"
)

// Windows support requires dialing mpv's named pipe
// (\\.\pipe\<name>); not implemented in v1.
func dialIPC(path string) (net.Conn, error) {
	return nil, errors.New("junto does not support Windows yet (mpv named-pipe IPC unimplemented)")
}

func ipcPath(name string) string {
	return `\\.\pipe\` + name
}
