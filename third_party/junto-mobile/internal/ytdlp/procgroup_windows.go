//go:build windows

package ytdlp

import "os/exec"

// Windows support is unimplemented (see mpv/dial_windows.go); process
// groups don't exist in the POSIX sense there. setProcessGroup is a
// no-op and killGroup falls back to killing just the direct process,
// matching today's (pre-fix) behavior rather than regressing it.
func setProcessGroup(cmd *exec.Cmd) {}

func killGroup(cmd *exec.Cmd) error {
	return cmd.Process.Kill()
}
