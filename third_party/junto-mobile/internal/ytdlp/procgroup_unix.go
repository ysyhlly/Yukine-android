//go:build unix

package ytdlp

import (
	"os/exec"
	"syscall"
)

// setProcessGroup puts cmd in its own process group so killGroup can
// terminate it and any subprocess it spawns (yt-dlp shells out to ffmpeg
// for merging/postprocessing) together. Killing just the direct child
// otherwise leaves ffmpeg running as an orphan: it keeps writing to the
// cache, and since it inherited the same stdout pipe, the read side
// never sees EOF until ffmpeg finishes on its own.
func setProcessGroup(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true}
}

// killGroup sends SIGKILL to cmd's whole process group (a negative pid
// targets the group whose id equals that pid, which Setpgid arranges to
// be cmd.Process.Pid).
func killGroup(cmd *exec.Cmd) error {
	return syscall.Kill(-cmd.Process.Pid, syscall.SIGKILL)
}
