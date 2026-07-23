//go:build unix

package mpv

import "net"

func dialIPC(path string) (net.Conn, error) {
	return net.Dial("unix", path)
}

func ipcPath(name string) string {
	return tempIPCName(name)
}
