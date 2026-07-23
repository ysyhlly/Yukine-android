package human

import "testing"

func TestBytes(t *testing.T) {
	cases := map[int64]string{
		0:             "0 B",
		512:           "512 B",
		2 << 10:       "2.0 KiB",
		1536:          "1.5 KiB",
		5 << 20:       "5.0 MiB",
		3 << 30:       "3.0 GiB",
		1<<30 + 1<<29: "1.5 GiB",
	}
	for in, want := range cases {
		if got := Bytes(in); got != want {
			t.Errorf("Bytes(%d) = %q, want %q", in, got, want)
		}
	}
}
