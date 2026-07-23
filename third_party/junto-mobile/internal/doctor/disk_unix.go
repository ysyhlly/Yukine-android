//go:build unix

package doctor

import "syscall"

// DiskFree returns the bytes available to the current user on the
// filesystem containing dir. Exported so callers outside this package
// (the streaming-join disk-space preflight) can reuse the same platform
// logic as `junto doctor`'s own disk check.
func DiskFree(dir string) (uint64, error) {
	var st syscall.Statfs_t
	if err := syscall.Statfs(dir, &st); err != nil {
		return 0, err
	}
	return uint64(st.Bavail) * uint64(st.Bsize), nil
}
