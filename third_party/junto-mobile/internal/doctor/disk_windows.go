//go:build windows

package doctor

import "fmt"

// DiskFree is unimplemented on Windows (junto doesn't support Windows
// yet); the disk check reports it as unknown rather than failing.
func DiskFree(dir string) (uint64, error) {
	return 0, fmt.Errorf("free-space check not supported on Windows yet")
}
