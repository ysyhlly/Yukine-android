package app

import (
	"os/exec"
	"runtime"
	"strings"
)

// copyToClipboard puts text on the system clipboard using whichever
// tool the platform provides (pbcopy on macOS; wl-copy/xclip/xsel on
// Linux). Best-effort: returns false when no tool is available, e.g. a
// headless box — callers just skip the "copied" notice.
func copyToClipboard(text string) bool {
	var candidates [][]string
	if runtime.GOOS == "darwin" {
		candidates = [][]string{{"pbcopy"}}
	} else {
		candidates = [][]string{
			{"wl-copy"},
			{"xclip", "-selection", "clipboard"},
			{"xsel", "--clipboard", "--input"},
		}
	}
	for _, c := range candidates {
		path, err := exec.LookPath(c[0])
		if err != nil {
			continue
		}
		cmd := exec.Command(path, c[1:]...)
		cmd.Stdin = strings.NewReader(text)
		if cmd.Run() == nil {
			return true
		}
	}
	return false
}
