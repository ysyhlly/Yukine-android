pub(crate) const PACKETS_PER_TRANSFER: usize = 8;
pub(crate) const TRANSFER_RING_SIZE: usize = 8;
pub(crate) const MAX_PENDING_BYTES: usize = 4 * 1024 * 1024;

#[derive(Debug, Default)]
pub(crate) struct TransferLifecycle {
    closing: bool,
    async_failed: bool,
    in_flight: usize,
}

impl TransferLifecycle {
    pub(crate) fn accepts_writes(&self) -> bool {
        !self.closing && !self.async_failed
    }

    pub(crate) fn submitted(&mut self) {
        self.in_flight += 1;
    }

    pub(crate) fn submit_failed(&mut self) {
        self.in_flight = self.in_flight.saturating_sub(1);
    }

    pub(crate) fn completed(&mut self, failed: bool) {
        self.in_flight = self.in_flight.saturating_sub(1);
        self.async_failed |= failed;
    }

    pub(crate) fn cancel(&mut self) {
        self.closing = true;
    }

    pub(crate) fn is_closing(&self) -> bool {
        self.closing
    }

    pub(crate) fn is_drained(&self) -> bool {
        self.in_flight == 0
    }
}

pub(crate) fn pending_within_limit(current: usize, incoming: usize) -> bool {
    current
        .checked_add(incoming)
        .is_some_and(|total| total <= MAX_PENDING_BYTES)
}

pub(crate) fn first_free_slot(in_flight: impl IntoIterator<Item = bool>) -> Option<usize> {
    in_flight.into_iter().position(|busy| !busy)
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum FrequencyControlAccess {
    Unknown,
    Absent,
    ReadOnly,
    ReadWrite,
    Invalid,
}

pub(crate) fn decode_frequency_control_access(uac2: bool, raw_mode: i32) -> FrequencyControlAccess {
    if raw_mode < 0 {
        return FrequencyControlAccess::Unknown;
    }
    if !uac2 {
        return match raw_mode {
            0 => FrequencyControlAccess::Absent,
            3 => FrequencyControlAccess::ReadWrite,
            _ => FrequencyControlAccess::Invalid,
        };
    }
    match raw_mode {
        0 => FrequencyControlAccess::Absent,
        1 => FrequencyControlAccess::ReadOnly,
        3 => FrequencyControlAccess::ReadWrite,
        _ => FrequencyControlAccess::Invalid,
    }
}

pub(crate) fn should_allow_uac2_pcm_rate_mismatch(
    enabled: bool,
    uac2: bool,
    access: FrequencyControlAccess,
    any_set_accepted: bool,
    any_rate_read: bool,
    rate_matches: bool,
) -> bool {
    enabled
        && uac2
        && access == FrequencyControlAccess::ReadWrite
        && any_set_accepted
        && any_rate_read
        && !rate_matches
}

pub(crate) fn feedback_rate(data: &[u8]) -> f64 {
    match data.len() {
        3 => {
            let raw = u32::from(data[0]) | (u32::from(data[1]) << 8) | (u32::from(data[2]) << 16);
            f64::from(raw) * 1000.0 / 16384.0
        }
        4.. => {
            let raw = u32::from(data[0])
                | (u32::from(data[1]) << 8)
                | (u32::from(data[2]) << 16)
                | (u32::from(data[3]) << 24);
            f64::from(raw) * 8000.0 / 65536.0
        }
        _ => 0.0,
    }
}

pub(crate) fn packet_lengths(
    available_bytes: usize,
    sample_rate_hz: i32,
    service_interval_us: i32,
    bytes_per_frame: i32,
    max_packet_payload: i32,
    initial_remainder: i64,
) -> (Vec<i32>, usize, i64) {
    let mut lengths = Vec::with_capacity(PACKETS_PER_TRANSFER);
    let mut consumed = 0usize;
    let mut remainder = initial_remainder;
    while lengths.len() < PACKETS_PER_TRANSFER {
        let scaled_frames = i64::from(sample_rate_hz) * i64::from(service_interval_us) + remainder;
        let frames = scaled_frames / 1_000_000;
        let next_remainder = scaled_frames % 1_000_000;
        let bytes = frames * i64::from(bytes_per_frame);
        if bytes <= 0
            || bytes > i64::from(max_packet_payload)
            || consumed.saturating_add(bytes as usize) > available_bytes
        {
            break;
        }
        lengths.push(bytes as i32);
        consumed += bytes as usize;
        remainder = next_remainder;
    }
    (lengths, consumed, remainder)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decodes_uac_frequency_control_modes() {
        assert_eq!(
            decode_frequency_control_access(true, 3),
            FrequencyControlAccess::ReadWrite
        );
        assert_eq!(
            decode_frequency_control_access(true, 1),
            FrequencyControlAccess::ReadOnly
        );
        assert_eq!(
            decode_frequency_control_access(true, 2),
            FrequencyControlAccess::Invalid
        );
        assert_eq!(
            decode_frequency_control_access(false, 3),
            FrequencyControlAccess::ReadWrite
        );
    }

    #[test]
    fn mismatch_override_requires_accepted_writable_uac2_clock() {
        assert!(should_allow_uac2_pcm_rate_mismatch(
            true,
            true,
            FrequencyControlAccess::ReadWrite,
            true,
            true,
            false,
        ));
        assert!(!should_allow_uac2_pcm_rate_mismatch(
            true,
            true,
            FrequencyControlAccess::ReadWrite,
            false,
            true,
            false,
        ));
        assert!(!should_allow_uac2_pcm_rate_mismatch(
            true,
            true,
            FrequencyControlAccess::ReadOnly,
            true,
            true,
            false,
        ));
    }

    #[test]
    fn decodes_three_and_four_byte_feedback() {
        assert!((feedback_rate(&[0, 0, 12]) - 48_000.0).abs() < 0.01);
        assert!((feedback_rate(&[0, 0, 6, 0]) - 48_000.0).abs() < 0.01);
        assert_eq!(feedback_rate(&[1, 2]), 0.0);
    }

    #[test]
    fn packetizer_carries_fractional_frames_between_packets() {
        let (lengths, consumed, remainder) = packet_lengths(4096, 44_100, 125, 4, 1024, 0);
        assert_eq!(lengths.len(), PACKETS_PER_TRANSFER);
        assert_eq!(consumed, lengths.iter().map(|value| *value as usize).sum());
        assert!(remainder >= 0);
        assert!(lengths.iter().all(|length| *length == 20 || *length == 24));
    }

    #[test]
    fn packetizer_waits_for_a_complete_packet() {
        let (lengths, consumed, remainder) = packet_lengths(4, 48_000, 125, 4, 1024, 0);
        assert!(lengths.is_empty());
        assert_eq!(consumed, 0);
        assert_eq!(remainder, 0);
    }

    #[test]
    fn pending_limit_accepts_boundary_and_rejects_overflow() {
        assert!(pending_within_limit(MAX_PENDING_BYTES - 1, 1));
        assert!(!pending_within_limit(MAX_PENDING_BYTES, 1));
        assert!(!pending_within_limit(usize::MAX, 1));
    }

    #[test]
    fn full_ring_has_no_free_slot() {
        assert_eq!(first_free_slot([true; TRANSFER_RING_SIZE]), None);
        assert_eq!(
            first_free_slot([true, true, false, true, true, true, true, true]),
            Some(2)
        );
    }

    #[test]
    fn submit_failure_rolls_back_in_flight_count() {
        let mut lifecycle = TransferLifecycle::default();
        lifecycle.submitted();
        lifecycle.submit_failed();

        assert!(lifecycle.accepts_writes());
        assert!(lifecycle.is_drained());
    }

    #[test]
    fn async_failure_stops_writes_until_close_drains() {
        let mut lifecycle = TransferLifecycle::default();
        lifecycle.submitted();
        lifecycle.completed(true);

        assert!(!lifecycle.accepts_writes());
        assert!(lifecycle.is_drained());
    }

    #[test]
    fn cancel_blocks_new_submissions_while_callbacks_drain() {
        let mut lifecycle = TransferLifecycle::default();
        lifecycle.submitted();
        lifecycle.submitted();
        lifecycle.cancel();

        assert!(lifecycle.is_closing());
        assert!(!lifecycle.accepts_writes());
        assert!(!lifecycle.is_drained());

        lifecycle.completed(false);
        lifecycle.completed(false);
        assert!(lifecycle.is_drained());
    }
}
