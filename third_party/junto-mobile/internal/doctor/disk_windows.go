//go:build windows

package doctor

import (
	"fmt"
	"path/filepath"

	"golang.org/x/sys/windows"
)

// DiskFree reports the caller-available bytes on the volume containing dir.
func DiskFree(dir string) (uint64, error) {
	absolute, err := filepath.Abs(dir)
	if err != nil {
		return 0, fmt.Errorf("resolving disk path: %w", err)
	}
	path, err := windows.UTF16PtrFromString(absolute)
	if err != nil {
		return 0, fmt.Errorf("encoding disk path: %w", err)
	}
	var available uint64
	if err := windows.GetDiskFreeSpaceEx(path, &available, nil, nil); err != nil {
		return 0, err
	}
	return available, nil
}
